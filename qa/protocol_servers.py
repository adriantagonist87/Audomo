"""Loopback fixtures for ProtocolDeviceTest. Requires pyftpdlib, pyOpenSSL, cryptography and Paramiko.
Pass a new temporary directory. Only the public CA and synthetic login bootstrap go to the test APK.
The test client in the emulator addresses the host loopback via 10.0.2.2.
"""
import argparse
import base64
import datetime
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import secrets
import socket
import ssl
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
import paramiko
from pyftpdlib.authorizers import DummyAuthorizer
from pyftpdlib.handlers import FTPHandler, TLS_FTPHandler
from pyftpdlib.ioloop import IOLoop
from pyftpdlib.servers import FTPServer

parser = argparse.ArgumentParser()
parser.add_argument("directory", type=Path)
work = parser.parse_args().directory
work.mkdir(mode=0o700, exist_ok=False)
roots = {name: work / name for name in ("FTP", "FTPS", "SFTP", "WebDAV")}
for path in roots.values():
    path.mkdir()
password = secrets.token_urlsafe(24)
credentials = "Basic " + base64.b64encode(("qa:" + password).encode()).decode()
ports = {}
key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
ca_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
now = datetime.datetime.now(datetime.timezone.utc)
issuer = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Audomo local QA CA")])

def cert_builder(subject, pub):
    return (x509.CertificateBuilder().subject_name(subject).issuer_name(issuer).public_key(pub)
            .serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(days=1))
            .not_valid_after(now+datetime.timedelta(days=1)))

ca = cert_builder(issuer, ca_key.public_key()).add_extension(x509.BasicConstraints(ca=True,path_length=0),True).sign(ca_key,hashes.SHA256())
server = (cert_builder(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,"10.0.2.2")]),key.public_key())
          .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address("10.0.2.2")),x509.IPAddress(ipaddress.ip_address("127.0.0.1"))]),False)
          .sign(ca_key,hashes.SHA256()))
pem = serialization.Encoding.PEM
(work/"server.pem").write_bytes(server.public_bytes(pem)+ca.public_bytes(pem))
(work/"key.pem").write_bytes(key.private_bytes(pem,serialization.PrivateFormat.TraditionalOpenSSL,serialization.NoEncryption()))

def local(base, remote):
    path = (base/remote.lstrip("/")).resolve()
    path.relative_to(base)
    return path

class Dav(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self,*args):
        pass
    def handle_method(self):
        if self.headers.get("Authorization") != credentials:
            self.send_error(401)
            return
        try:
            path=local(roots["WebDAV"],urllib.parse.unquote(urllib.parse.urlsplit(self.path).path))
            code=200;body=b""
            etag=lambda:'"'+hashlib.sha256(path.read_bytes()).hexdigest()+'"'
            if self.command=="PROPFIND":
                code=207 if path.is_dir() else 404
                body=b'<d:multistatus xmlns:d="DAV:"/>'
            elif self.command=="MKCOL":
                if path.exists():code=405
                else:path.mkdir();code=201
            elif self.command=="PUT":
                if self.headers.get("If-None-Match")=="*" and path.exists():code=412
                elif self.headers.get("If-Match") and (not path.exists() or etag()!=self.headers["If-Match"]):code=412
                else:path.write_bytes(self.rfile.read(int(self.headers["Content-Length"])));code=201
            elif self.command=="GET":
                if path.is_file():body=path.read_bytes()
                else:code=404
            elif self.command=="DELETE":
                if not path.exists():code=404
                elif self.headers.get("If-Match")!=etag():code=412
                else:path.unlink();code=204
            self.send_response(code)
            if path.is_file():self.send_header("ETag",etag())
            self.send_header("Content-Length",str(len(body)));self.end_headers();self.wfile.write(body)
        except (OSError,ValueError):self.send_error(409)
    do_GET=do_PUT=do_PROPFIND=do_MKCOL=do_DELETE=handle_method

class SecureDavServer(ThreadingHTTPServer):
    def process_request_thread(self, request, client_address):
        try:
            request.settimeout(15)
            with tls.wrap_socket(request, server_side=True) as secure:
                self.finish_request(secure, client_address)
        except (ConnectionError, ssl.SSLError, TimeoutError):
            pass
        finally:
            self.shutdown_request(request)

tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
tls.load_cert_chain(work/"server.pem",work/"key.pem")
dav=SecureDavServer(("127.0.0.1",0),Dav)
ports["WebDAV"]=dav.server_port;threading.Thread(target=dav.serve_forever,daemon=True).start()

for name,handler in (("FTP",FTPHandler),("FTPS",TLS_FTPHandler)):
    auth=DummyAuthorizer();auth.add_user("qa",password,str(roots[name]),perm="elradfmwMT")
    cls=type("PrivateFixture",(handler,),{"authorizer":auth,"masquerade_address":"10.0.2.2"})
    if name=="FTPS":
        cls.certfile=str(work/"server.pem");cls.keyfile=str(work/"key.pem");cls.tls_control_required=True;cls.tls_data_required=True
    service=FTPServer(("127.0.0.1",0),cls,ioloop=IOLoop());ports[name]=service.socket.getsockname()[1]
    threading.Thread(target=service.serve_forever,kwargs={"handle_exit":False},daemon=True).start()

host_key=paramiko.RSAKey.generate(2048)
class Auth(paramiko.ServerInterface):
    def check_auth_password(self,u,p):
        return paramiko.AUTH_SUCCESSFUL if u=="qa" and secrets.compare_digest(p,password) else paramiko.AUTH_FAILED
    def get_allowed_auths(self,u):return "password"
    def check_channel_request(self,kind,chanid):return paramiko.OPEN_SUCCEEDED if kind=="session" else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED

class Files(paramiko.SFTPServerInterface):
    def loc(self,p):return local(roots["SFTP"],p)
    def stat(self,p):
        try:return paramiko.SFTPAttributes.from_stat(self.loc(p).stat())
        except OSError as e:return paramiko.SFTPServer.convert_errno(e.errno)
    lstat=stat
    def list_folder(self,p):
        try:return [paramiko.SFTPAttributes.from_stat(f.stat(),filename=f.name) for f in self.loc(p).iterdir()]
        except OSError as e:return paramiko.SFTPServer.convert_errno(e.errno)
    def open(self,p,flags,attr):
        try:
            fd=os.open(self.loc(p),flags,0o600);rw=bool(flags&(os.O_RDWR|os.O_WRONLY))
            obj=os.fdopen(fd,"r+b" if flags&os.O_RDWR else "wb" if rw else "rb")
            handle=paramiko.SFTPHandle(flags);handle.readfile=handle.writefile=obj
            return handle
        except OSError as e:return paramiko.SFTPServer.convert_errno(e.errno)
    def op(self,fn):
        try:fn();return paramiko.SFTP_OK
        except OSError as e:return paramiko.SFTPServer.convert_errno(e.errno)
    def mkdir(self,p,attr):return self.op(self.loc(p).mkdir)
    def rename(self,a,b):return self.op(lambda:self.loc(a).rename(self.loc(b)))
    posix_rename=rename
    def remove(self,p):return self.op(self.loc(p).unlink)

sock=socket.socket();sock.bind(("127.0.0.1",0));sock.listen(5);ports["SFTP"]=sock.getsockname()[1]
def serve_ssh():
    while True:
        conn,_=sock.accept()
        def run(c):
            transport=paramiko.Transport(c);transport.add_server_key(host_key)
            transport.set_subsystem_handler("sftp",paramiko.SFTPServer,Files)
            try:
                transport.start_server(server=Auth())
                while transport.is_active():time.sleep(.1)
            finally:transport.close()
        threading.Thread(target=run,args=(conn,),daemon=True).start()
threading.Thread(target=serve_ssh,daemon=True).start()
(work/"client.json").write_text(json.dumps({"password":password,"ports":ports,"ca":ca.public_bytes(pem).decode(),"hostKey":host_key.get_name()+" "+host_key.get_base64()}))
while True:time.sleep(5)
