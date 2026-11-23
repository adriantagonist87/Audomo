package de.audomo;

import android.database.sqlite.SQLiteDatabase;
import android.net.Network;

import java.util.*;

final class CloudReconcile {
    private record Result(UploadQueue.Job job, String path, String issue) {}

    static String verify(SessionStore store, UploadQueue.Job job) throws Exception {
        if (!job.state().equals("Hochgeladen") || !job.knownCopy())
            throw new java.io.IOException("Keine bestätigte Cloudkopie vorhanden");
        CloudConfig target = CloudConfig.byId(store.context, job.target());
        Network net = CloudLibrary.network(store.context);
        String actual;
        try (RemoteFiles remote = RemoteFiles.open(target, CloudConfig.secret(store.context, target.id()),
                net, n -> CloudLibrary.checkNetwork(store.context, net))) {
            actual = remote.hash(job.confirmedPath());
        }
        String issue = actual == null ? "Cloudkopie fehlt" : job.verified().equals(actual) ? "" : "Cloudkopie geändert";
        UploadQueue queue = UploadQueue.get(store.context);
        synchronized (queue) {
            if (!job.equals(queue.byId(job.id()))) throw new java.io.IOException("Auftrag inzwischen geändert");
            queue.getWritableDatabase().execSQL("UPDATE jobs SET availability=?,lastChecked=? WHERE id=?",
                    new Object[]{issue, System.currentTimeMillis(), job.id()});
        }
        return issue.isEmpty() ? "Cloudkopie geprüft · SHA-256 stimmt überein" : issue;
    }

    static String run(SessionStore store, CloudConfig target) throws Exception {
        CloudLibrary library = new CloudLibrary(store);
        Map<String, SessionStore.Entry> recordings = library.known();
        List<UploadQueue.Job> jobs =
                library.queue.jobs().stream()
                        .filter(j -> target.id().equals(j.target()) && j.knownCopy())
                        .collect(java.util.stream.Collectors.toList());
        if (jobs.isEmpty()) return "Für dieses Ziel sind keine Cloudkopien gespeichert.";
        Map<String, String> claimed = new HashMap<>(), hashes = new HashMap<>();
        for (UploadQueue.Job j : jobs) claimed.put(j.confirmedPath(), j.rec());
        List<Result> results = new ArrayList<>();
        List<UploadQueue.Job> lost = new ArrayList<>();
        Network net = CloudLibrary.network(store.context);
        try (RemoteFiles remote =
                RemoteFiles.open(
                        target,
                        CloudConfig.secret(store.context, target.id()),
                        net,
                        bytes -> CloudLibrary.checkNetwork(store.context, net))) {
            for (UploadQueue.Job j : jobs) {
                if (!j.state().equals("Hochgeladen")) continue;
                String actual = remote.hash(j.path());
                hashes.put(j.path(), actual);
                if (j.verified().equals(actual)) results.add(new Result(j, j.path(), ""));
                else lost.add(j);
            }
            if (!lost.isEmpty()) {
                Map<String, Long> inventory = inventory(remote, target.base());
                Map<String, List<String>> byHash = new HashMap<>();
                Set<Long> sizes = new HashSet<>();
                for (UploadQueue.Job j : lost) sizes.add(recordings.get(j.rec()).bytes());
                for (var file : inventory.entrySet()) {
                    if (claimed.containsKey(file.getKey())) continue;
                    if (file.getValue() >= 0
                            && !sizes.contains(-1L)
                            && !sizes.contains(file.getValue())) continue;
                    String hash = remote.hash(file.getKey());
                    if (hash != null)
                        byHash.computeIfAbsent(hash, k -> new ArrayList<>()).add(file.getKey());
                }
                for (UploadQueue.Job j : lost) {
                    List<String> matches = byHash.getOrDefault(j.verified(), List.of());
                    boolean unique =
                            matches.size() == 1
                                    && lost.stream()
                                                    .filter(other ->
                                                            other.verified().equals(j.verified()))
                                                    .count()
                                            == 1;
                    String issue =
                            unique
                                    ? ""
                                    : !matches.isEmpty()
                                            ? "Zuordnung mehrdeutig"
                                            : hashes.get(j.path()) == null
                                                    ? "Cloudkopie fehlt"
                                                    : "Cloudkopie geändert";
                    results.add(new Result(j, unique ? matches.get(0) : j.path(), issue));
                }
            }
        }
        int moved = 0, open = 0, checked = 0;
        synchronized (store) {
            SQLiteDatabase db = library.queue.getWritableDatabase();
            synchronized (library.queue) {
                db.beginTransaction();
                try {
                    List<UploadQueue.Job> current = library.queue.jobs();
                    for (Result result : results) {
                        UploadQueue.Job j = result.job();
                        if (!current.contains(j)) continue;
                        db.execSQL(
                                "UPDATE jobs SET path=?,availability=?,lastChecked=? WHERE id=?",
                                new Object[] {result.path(), result.issue(), System.currentTimeMillis(), j.id()});
                        checked++;
                        if (!result.issue().isEmpty()) open++;
                        if (!result.path().equals(j.path())) {
                            moved++;
                            SessionStore.Entry e = store.byId(j.rec());
                            if (e == null || e.trashed() != 0) {
                                String relative =
                                        result.path()
                                                .substring(
                                                        target.base().equals("/")
                                                                ? 1
                                                                : target.base().length() + 1);
                                int slash = relative.lastIndexOf('/');
                                String folder = slash < 0 ? "" : relative.substring(0, slash);
                                if (LibraryFiles.validFolderPath(folder)) {
                                    new LibraryFiles(store).ensureFolder(folder);
                                    db.execSQL(
                                            "UPDATE recordings SET name=?,folder=? WHERE rec=?",
                                            new Object[] {
                                                RemoteFiles.leaf(result.path()), folder, j.rec()
                                            });
                                }
                            }
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
        }
        return checked + " geprüft · " + moved + " wiedergefunden · " + open + " ungeklärt";
    }

    private static Map<String, Long> inventory(RemoteFiles remote, String base) throws Exception {
        Map<String, Long> files = new HashMap<>();
        ArrayDeque<String> folders = new ArrayDeque<>();
        folders.add(base);
        while (!folders.isEmpty()) {
            remote.progress.check(0);
            String parent = folders.removeFirst();
            for (RemoteFiles.Child child : remote.children(parent)) {
                String name = child.name();
                if (name.isEmpty()
                        || name.equals(".")
                        || name.equals("..")
                        || name.startsWith(".audomo-")
                        || name.contains("/")
                        || name.contains("\\")
                        || name.chars().anyMatch(Character::isISOControl)) continue;
                String path = CloudConfig.path(parent + "/" + name);
                if (child.directory()) folders.add(path);
                else files.put(path, child.bytes());
            }
        }
        return files;
    }
}
