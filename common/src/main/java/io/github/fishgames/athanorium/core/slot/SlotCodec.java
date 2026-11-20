package io.github.fishgames.athanorium.core.slot;

import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.json.DataException;
import io.github.fishgames.athanorium.core.program.ProgramCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;

/** Binary form of contents and whole slots, the same in packets and in the block data. */
public final class SlotCodec {
    private static final int VERSION = 1;

    private SlotCodec() {
    }

    public static void writeContent(DataOutputStream out, SlotContent c) throws IOException {
        out.writeUTF(c.puzzle() == null ? "" : c.puzzle().toString());
        out.writeInt(c.stage());
        byte[] program = ProgramCodec.encode(c.program());
        out.writeInt(program.length);
        out.write(program);
    }

    public static SlotContent readContent(DataInputStream in, ProgramCodec.Limits limits) throws IOException {
        String puzzle = in.readUTF();
        int stage = in.readInt();
        int length = in.readInt();
        if (length < 0 || length > in.available()) {
            throw new DataException("slot data", "program", "Program length out of range");
        }
        byte[] program = new byte[length];
        in.readFully(program);
        try {
            return new SlotContent(puzzle.isEmpty() ? null : Id.parse(puzzle), stage,
                    ProgramCodec.decode(program, limits));
        } catch (IllegalArgumentException e) {
            throw new DataException("slot data", "puzzle", e.getMessage());
        }
    }

    public static byte[] encode(ProgramSlot slot) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeInt(slot.revisionCounter());
            writeState(out, slot.saved());
            out.writeBoolean(slot.pending() != null);
            if (slot.pending() != null) {
                writeState(out, slot.pending());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static ProgramSlot decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readByte();
            if (version != VERSION) {
                throw new DataException("slot data", "", "Unknown slot data version " + version);
            }
            int counter = in.readInt();
            SlotState saved = readState(in);
            SlotState pending = in.readBoolean() ? readState(in) : null;
            return new ProgramSlot(saved, pending, counter);
        } catch (IOException e) {
            throw new DataException("slot data", "", "Malformed slot data: " + e.getMessage());
        }
    }

    private static void writeState(DataOutputStream out, SlotState s) throws IOException {
        writeContent(out, s.content());
        out.writeInt(s.revision());
        out.writeBoolean(s.confirmed());
        CalcResult r = s.result();
        out.writeBoolean(r != null);
        if (r != null) {
            out.writeBoolean(r.passed());
            out.writeUTF(r.failKey());
            out.writeUTF(r.failDetail());
            out.writeInt(r.metrics().size());
            for (Map.Entry<Id, Long> e : r.metrics().entrySet()) {
                out.writeUTF(e.getKey().toString());
                out.writeLong(e.getValue());
            }
            out.writeUTF(r.solvability());
            out.writeUTF(r.scoring());
        }
    }

    private static SlotState readState(DataInputStream in) throws IOException {
        SlotContent content = readContent(in, ProgramCodec.NO_LIMITS);
        int revision = in.readInt();
        boolean confirmed = in.readBoolean();
        CalcResult result = null;
        if (in.readBoolean()) {
            boolean passed = in.readBoolean();
            String key = in.readUTF();
            String detail = in.readUTF();
            int n = in.readInt();
            Map<Id, Long> metrics = new TreeMap<>();
            for (int i = 0; i < n && i < 10_000; i++) {
                metrics.put(Id.parse(in.readUTF()), in.readLong());
            }
            result = new CalcResult(passed, key, detail, metrics, in.readUTF(), in.readUTF());
        }
        return new SlotState(content, revision, confirmed, result);
    }
}
