package io.github.fishgames.athanorium.base.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import io.github.fishgames.athanorium.ModCore;
import io.github.fishgames.athanorium.base.platform.Services;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Packet base: one channel, messages of any size. A message is compressed when that pays off and split into
 * frames that stay below the 32,767 byte limit for packets from client to server in 1.20.1.
 * Frame layout: transfer id (int), frame index (int), frame count (int), flags (byte), data.
 */
public final class Net {
    public static final ResourceLocation CHANNEL = ModCore.rl("main");
    public static final int FRAME_DATA = 30_000;
    private static final int HEADER = 13;
    private static final int FLAG_COMPRESSED = 1;
    private static final AtomicInteger NEXT_TRANSFER = new AtomicInteger(1);

    // message types, client to server
    public static final int C_INTERIM = 1;
    public static final int C_SUBMIT = 2;
    public static final int C_SAVE = 3;
    public static final int C_DISCARD = 4;
    // message types, server to client
    public static final int S_SYNC = 10;
    public static final int S_OPEN = 11;
    public static final int S_STATUS = 12;

    private Net() {
    }

    public static void sendToServer(byte[] message) {
        for (byte[] frame : frames(message)) {
            Services.platform().sendToServer(frame);
        }
    }

    public static void sendToPlayer(ServerPlayer player, byte[] message) {
        for (byte[] frame : frames(message)) {
            Services.platform().sendToPlayer(player, frame);
        }
    }

    public static List<byte[]> frames(byte[] message) {
        int flags = 0;
        byte[] data = message;
        if (message.length > 512) {
            byte[] packed = deflate(message);
            if (packed.length < message.length) {
                data = packed;
                flags = FLAG_COMPRESSED;
            }
        }
        int count = Math.max(1, (data.length + FRAME_DATA - 1) / FRAME_DATA);
        int transfer = NEXT_TRANSFER.getAndIncrement();
        List<byte[]> frames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int from = i * FRAME_DATA;
            int len = Math.min(FRAME_DATA, data.length - from);
            ByteBuffer b = ByteBuffer.allocate(HEADER + len);
            b.putInt(transfer).putInt(i).putInt(count).put((byte) flags).put(data, from, len);
            frames.add(b.array());
        }
        return frames;
    }

    private static byte[] deflate(byte[] in) {
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(in);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length / 2 + 16);
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
        }
        d.end();
        return out.toByteArray();
    }

    /**
     * Puts the frames of one sender together again. Frames must arrive in order; anything unexpected drops the
     * transfer in progress. The buffer has an upper limit, for the packed and for the unpacked size.
     */
    public static final class Reassembler {
        private final int maxBytes;
        private int transfer = -1;
        private int expected;
        private int count;
        private int flags;
        private ByteArrayOutputStream buffer;

        public Reassembler(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        /** Feeds one frame. Returns the complete message or null if more frames are needed or the frame was bad. */
        public byte[] feed(byte[] frame) {
            if (frame.length < HEADER) {
                reset();
                return null;
            }
            ByteBuffer b = ByteBuffer.wrap(frame);
            int id = b.getInt();
            int index = b.getInt();
            int total = b.getInt();
            int fl = b.get();
            if (index == 0) {
                if (total < 1 || (long) total * FRAME_DATA > (long) maxBytes + FRAME_DATA) {
                    reset();
                    return null;
                }
                transfer = id;
                expected = 0;
                count = total;
                flags = fl;
                buffer = new ByteArrayOutputStream(Math.min(total * FRAME_DATA, 1 << 16));
            }
            if (buffer == null || id != transfer || index != expected || total != count) {
                reset();
                return null;
            }
            buffer.write(frame, HEADER, frame.length - HEADER);
            expected++;
            if (buffer.size() > maxBytes) {
                reset();
                return null;
            }
            if (expected < count) {
                return null;
            }
            byte[] data = buffer.toByteArray();
            int f = flags;
            reset();
            return (f & FLAG_COMPRESSED) != 0 ? inflate(data) : data;
        }

        private void reset() {
            transfer = -1;
            buffer = null;
        }

        private byte[] inflate(byte[] in) {
            Inflater inf = new Inflater();
            inf.setInput(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream(in.length * 2);
            byte[] buf = new byte[8192];
            try {
                while (!inf.finished()) {
                    int n = inf.inflate(buf);
                    if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                        return null;
                    }
                    out.write(buf, 0, n);
                    if (out.size() > maxBytes) {
                        return null;
                    }
                }
            } catch (DataFormatException e) {
                return null;
            } finally {
                inf.end();
            }
            return out.toByteArray();
        }
    }
}
