package de.audomo;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.SystemClock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class OpusEncoder implements AutoCloseable {
    private final MediaCodec codec;
    private final OggOpus stream;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private final int rate;
    private long inputFrames;
    private long finalFrames = -1;
    private boolean ended;

    OpusEncoder(OggOpus stream, Profile profile) throws IOException {
        this.stream = stream;
        rate = profile.rate();
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS);
        try {
            MediaFormat format =
                    MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, rate, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, profile.kbps() * 1000);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
        } catch (RuntimeException e) {
            codec.release();
            throw e;
        }
    }

    void write(short[] pcm, int count) throws IOException {
        long deadline = SystemClock.elapsedRealtime() + 4000;
        int offset = 0;
        while (offset < count) {
            drain(0);
            int index = codec.dequeueInputBuffer(10000);
            if (index >= 0) {
                ByteBuffer buf = codec.getInputBuffer(index).order(ByteOrder.nativeOrder());
                int chunk = Math.min(count - offset, buf.remaining() / 2);
                buf.asShortBuffer().put(pcm, offset, chunk);
                codec.queueInputBuffer(index, 0, chunk * 2, inputFrames * 1000000 / rate, 0);
                inputFrames += chunk;
                offset += chunk;
            }
            if (SystemClock.elapsedRealtime() > deadline)
                throw new IOException("Opus-Encoder antwortet nicht");
        }
        drain(0);
    }

    void finish() throws IOException {
        finalFrames = inputFrames * (48000 / rate);
        // Flush algorithmic delay and an incomplete encoder frame. The EOS granule trims exactly
        // this padding.
        int padding = (stream.preSkip() + 1920) * rate / 48000;
        write(new short[padding], padding);
        long deadline = SystemClock.elapsedRealtime() + 5000;
        boolean sent = false;
        while (!ended) {
            if (!sent) {
                int index = codec.dequeueInputBuffer(10000);
                if (index >= 0) {
                    codec.queueInputBuffer(
                            index,
                            0,
                            0,
                            inputFrames * 1000000 / rate,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    sent = true;
                }
            }
            drain(10000);
            if (SystemClock.elapsedRealtime() > deadline)
                throw new IOException("Opus-Abschluss abgebrochen");
        }
        stream.finish(finalFrames);
    }

    private void drain(long timeout) throws IOException {
        int index;
        while ((index = codec.dequeueOutputBuffer(info, timeout))
                != MediaCodec.INFO_TRY_AGAIN_LATER) {
            timeout = 0;
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                ByteBuffer csd = codec.getOutputFormat().getByteBuffer("csd-0");
                if (csd != null) {
                    byte[] bytes = new byte[csd.remaining()];
                    csd.get(bytes);
                    stream.headers(bytes);
                }
            }
            if (index < 0) continue;
            try {
                if (info.size > 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(index);
                    buffer.position(info.offset).limit(info.offset + info.size);
                    byte[] packet = new byte[info.size];
                    buffer.get(packet);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0)
                        stream.headers(packet);
                    else stream.packet(packet, finalFrames);
                }
                ended = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            } finally {
                codec.releaseOutputBuffer(index, false);
            }
            if (ended) return;
        }
    }

    @Override
    public void close() {
        codec.release();
    }
}
