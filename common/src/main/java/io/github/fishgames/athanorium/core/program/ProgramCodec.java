package io.github.fishgames.athanorium.core.program;

import io.github.fishgames.athanorium.core.hex.Hex;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.json.DataException;
import io.github.fishgames.athanorium.core.json.JsonText;
import io.github.fishgames.athanorium.core.json.StrictJson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compact binary form of a program, used in packets and in the block data. Part and instruction types are
 * string ids in a dictionary per program and are addressed by index, never by numeric registry ids: unknown
 * types survive unchanged and indices cannot drift apart between client and server.
 */
public final class ProgramCodec {
    private static final String FILE = "program data";

    private ProgramCodec() {
    }

    /** Limits for incoming data; {@link #NO_LIMITS} when reading stored data. */
    public record Limits(int maxParts, int maxInstructions) {
    }

    public static final Limits NO_LIMITS = new Limits(Integer.MAX_VALUE, Integer.MAX_VALUE);

    public static byte[] encode(Program program) {
        Map<String, Integer> dict = new LinkedHashMap<>();
        for (Part p : program.parts()) {
            dict.putIfAbsent(p.type().toString(), dict.size());
            for (Id id : p.instructions().values()) {
                dict.putIfAbsent(id.toString(), dict.size());
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writeVar(out, dict.size());
            for (String s : dict.keySet()) {
                out.writeUTF(s);
            }
            writeVar(out, program.parts().size());
            for (Part p : program.parts()) {
                writeVar(out, dict.get(p.type().toString()));
                writeZig(out, p.pos().q());
                writeZig(out, p.pos().r());
                if (p.isUnknown()) {
                    out.writeByte(1);
                    byte[] raw = JsonText.write(p.raw()).getBytes(StandardCharsets.UTF_8);
                    writeVar(out, raw.length);
                    out.write(raw);
                    continue;
                }
                out.writeByte(0);
                writeZig(out, p.rotation());
                writeZig(out, p.inputIndex());
                writeZig(out, p.length());
                writeVar(out, p.trackHexes().size());
                for (Hex h : p.trackHexes()) {
                    writeZig(out, h.q());
                    writeZig(out, h.r());
                }
                writeVar(out, p.instructions().size());
                int last = 0;
                for (Map.Entry<Integer, Id> e : p.instructions().entrySet()) {
                    writeVar(out, e.getKey() - last);
                    last = e.getKey();
                    writeVar(out, dict.get(e.getValue().toString()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** Decodes a program. Any malformed input or exceeded limit becomes a {@link DataException}. */
    @SuppressWarnings("unchecked")
    public static Program decode(byte[] data, Limits limits) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int dictSize = readCount(in, data.length);
            List<Id> dict = new ArrayList<>();
            for (int i = 0; i < dictSize; i++) {
                dict.add(Id.parse(in.readUTF()));
            }
            int partCount = readCount(in, data.length);
            if (partCount > limits.maxParts()) {
                throw new DataException(FILE, "parts", "Too many parts: " + partCount + " (limit " + limits.maxParts() + ")");
            }
            long instructions = 0;
            List<Part> parts = new ArrayList<>();
            for (int i = 0; i < partCount; i++) {
                Id type = entry(dict, readVar(in));
                Hex pos = new Hex(readZig(in), readZig(in));
                if (in.readByte() == 1) {
                    byte[] raw = new byte[readCount(in, data.length)];
                    in.readFully(raw);
                    Object parsed = StrictJson.parse(new String(raw, StandardCharsets.UTF_8), FILE);
                    if (!(parsed instanceof Map<?, ?>)) {
                        throw new DataException(FILE, "parts[" + i + "]", "Expected an object");
                    }
                    parts.add(Part.unknown(type, pos, (Map<String, Object>) parsed));
                    continue;
                }
                int rotation = readZig(in);
                int inputIndex = readZig(in);
                int length = readZig(in);
                int trackCount = readCount(in, data.length);
                List<Hex> track = new ArrayList<>();
                for (int t = 0; t < trackCount; t++) {
                    track.add(new Hex(readZig(in), readZig(in)));
                }
                int instCount = readCount(in, data.length);
                instructions += instCount;
                if (instructions > limits.maxInstructions()) {
                    throw new DataException(FILE, "parts[" + i + "]", "Too many instructions (limit "
                            + limits.maxInstructions() + ")");
                }
                Map<Integer, Id> tape = new TreeMap<>();
                long slot = 0;
                for (int t = 0; t < instCount; t++) {
                    slot += readVar(in);
                    if (slot < 0 || slot > Integer.MAX_VALUE) {
                        throw new DataException(FILE, "parts[" + i + "]", "Tape slot out of range");
                    }
                    tape.put((int) slot, entry(dict, readVar(in)));
                }
                parts.add(Part.ofFields(type, pos, rotation, inputIndex, length, track, tape));
            }
            return new Program(parts);
        } catch (IOException | IllegalArgumentException e) {
            throw new DataException(FILE, "", "Malformed program data: " + e.getMessage());
        }
    }

    private static Id entry(List<Id> dict, int index) {
        if (index < 0 || index >= dict.size()) {
            throw new DataException(FILE, "", "Dictionary index out of range: " + index);
        }
        return dict.get(index);
    }

    /** A count can never be larger than the data it is read from; this stops huge allocations on bad input. */
    private static int readCount(DataInputStream in, int dataLength) throws IOException {
        int n = readVar(in);
        if (n < 0 || n > dataLength) {
            throw new DataException(FILE, "", "Count out of range: " + n);
        }
        return n;
    }

    public static void writeVar(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    public static int readVar(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("Number too long");
    }

    private static void writeZig(DataOutputStream out, int value) throws IOException {
        writeVar(out, (value << 1) ^ (value >> 31));
    }

    private static int readZig(DataInputStream in) throws IOException {
        int v = readVar(in);
        return (v >>> 1) ^ -(v & 1);
    }
}
