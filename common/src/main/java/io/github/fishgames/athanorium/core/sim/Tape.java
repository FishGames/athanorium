package io.github.fishgames.athanorium.core.sim;

import io.github.fishgames.athanorium.core.part.InstructionType;

import java.util.List;
import java.util.NavigableMap;

/**
 * Compiled instruction tape of one arm: repeat and reset are expanded to plain instructions (rules after omsim).
 * Codes: f grab, r drop, a rotate ccw, d rotate cw, q pivot ccw, e pivot cw, w extend, s retract,
 * g track forward, t track back, ' ' wait, 0 empty.
 */
public final class Tape {
    public static final Tape EMPTY = new Tape(new byte[0], 0, 0);

    private final byte[] codes;
    private final int length;
    private final int startSlot;

    private Tape(byte[] codes, int length, int startSlot) {
        this.codes = codes;
        this.length = length;
        this.startSlot = startSlot;
    }

    /** Number of tape cells counted from the first used slot. */
    public int length() {
        return length;
    }

    /** Slot of the first instruction; the tape starts there. */
    public int startSlot() {
        return startSlot;
    }

    public byte code(int index) {
        return codes[index];
    }

    /** Number of instructions after expansion, wait and empty cells excluded (rules after omsim). */
    public int instructionCount() {
        int n = 0;
        for (int i = 0; i < length; i++) {
            if (codes[i] != 0 && codes[i] != ' ') {
                n++;
            }
        }
        return n;
    }

    static byte codeOf(InstructionType.Kind kind) {
        switch (kind) {
            case GRAB: return 'f';
            case DROP: return 'r';
            case ROTATE_CCW: return 'a';
            case ROTATE_CW: return 'd';
            case PIVOT_CCW: return 'q';
            case PIVOT_CW: return 'e';
            case EXTEND: return 'w';
            case RETRACT: return 's';
            case TRACK_FORWARD: return 'g';
            case TRACK_BACK: return 't';
            case WAIT: return ' ';
            default: throw new IllegalArgumentException("No code for " + kind);
        }
    }

    /**
     * Builds the tape of an arm.
     *
     * @param instructions instruction kind per slot
     * @param armPos       base position of the arm (needed to resolve resets along tracks)
     * @param armLength    initial arm length (a piston arm returns to it on reset)
     */
    public static Tape compile(NavigableMap<Integer, InstructionType.Kind> instructions, Vec armPos, int armLength,
                               TrackTable tracks) {
        if (instructions.isEmpty()) {
            return EMPTY;
        }
        List<java.util.Map.Entry<Integer, InstructionType.Kind>> entries = List.copyOf(instructions.entrySet());
        int minSlot = entries.get(0).getKey();
        int maxSlot = entries.get(entries.size() - 1).getKey();
        int tapeLength = maxSlot - minSlot + 1;
        byte[] tape = new byte[tapeLength * 2 + 16];
        int lastEnd = 0;
        int lastRepeat = 0;
        int resetFrom = 0;
        for (int j = 0; j < entries.size(); j++) {
            InstructionType.Kind kind = entries.get(j).getValue();
            int n = entries.get(j).getKey() - minSlot;
            if (kind == InstructionType.Kind.REPEAT) {
                while (j < entries.size() && entries.get(j).getValue() == InstructionType.Kind.REPEAT) {
                    int at = entries.get(j).getKey() - minSlot;
                    if (lastEnd > at) {
                        throw new TapeException("program.error.repeat_overlaps_reset",
                                "A repeat instruction overlaps with a reset instruction");
                    }
                    if (lastEnd > lastRepeat) {
                        int count = lastEnd - lastRepeat;
                        tape = ensure(tape, at + count);
                        System.arraycopy(tape, lastRepeat, tape, at, count);
                        tapeLength = Math.max(tapeLength, at + count);
                    }
                    j++;
                }
                if (j < entries.size()) {
                    lastRepeat = entries.get(j).getKey() - minSlot;
                    resetFrom = lastRepeat;
                }
                j--;
            } else if (kind == InstructionType.Kind.RESET) {
                int rotation = 0;
                int piston = armLength;
                int grab = 0;
                int trackSteps = 0;
                int trackLoopingSteps = 0;
                Vec track = armPos;
                for (int k = resetFrom; k < n; k++) {
                    Vec motion = Vec.ZERO;
                    byte c = tape[k];
                    if (c == 'a') {
                        rotation++;
                    } else if (c == 'd') {
                        rotation--;
                    } else if (c == 'w') {
                        if (piston < 3) {
                            piston++;
                        }
                    } else if (c == 's') {
                        if (piston > 1) {
                            piston--;
                        }
                    } else if (c == 'g') {
                        Vec m = tracks.plusMotion(track);
                        if (m != null) {
                            motion = m;
                            if (!m.isZero()) {
                                trackSteps++;
                            }
                        }
                    } else if (c == 't') {
                        Vec m = tracks.minusMotion(track);
                        if (m != null) {
                            motion = m;
                            if (!m.isZero()) {
                                trackSteps--;
                            }
                        }
                    } else if (c == 'f') {
                        grab = 1;
                    } else if (c == 'r') {
                        grab = 0;
                    }
                    track = track.plus(motion);
                    if (track.equals(armPos)) {
                        trackLoopingSteps += trackSteps;
                        trackSteps = 0;
                    }
                }
                tape = ensure(tape, n + 64);
                if (grab > 0) {
                    tape = set(tape, n++, 'r');
                }
                while (piston > armLength) {
                    tape = set(tape, n++, 's');
                    piston--;
                }
                while (rotation > 3) {
                    rotation -= 6;
                }
                while (rotation < -3) {
                    rotation += 6;
                }
                while (rotation > 0) {
                    tape = set(tape, n++, 'd');
                    rotation--;
                }
                while (rotation < 0) {
                    tape = set(tape, n++, 'a');
                    rotation++;
                }
                if (trackSteps != 0) {
                    int searchDepth = trackSteps;
                    int direction = 1;
                    boolean usePlus = true;
                    if (searchDepth < 0) {
                        searchDepth = -searchDepth;
                        direction = -1;
                        usePlus = false;
                    }
                    if (trackSteps * (trackSteps + trackLoopingSteps) < 0) {
                        searchDepth++;
                    }
                    Vec p = track;
                    for (int i = 0; i < searchDepth; i++) {
                        if (p.equals(armPos)) {
                            trackSteps = -i * direction;
                            break;
                        }
                        Vec m = usePlus ? tracks.plusMotion(p) : tracks.minusMotion(p);
                        if (m == null) {
                            break;
                        }
                        p = p.plus(m);
                    }
                }
                while (trackSteps > 0) {
                    tape = set(tape, n++, 't');
                    trackSteps--;
                }
                while (trackSteps < 0) {
                    tape = set(tape, n++, 'g');
                    trackSteps++;
                }
                while (piston < armLength) {
                    tape = set(tape, n++, 'w');
                    piston++;
                }
                if (n == entries.get(j).getKey() - minSlot) {
                    tape = set(tape, n++, ' ');
                }
                resetFrom = n;
                tapeLength = Math.max(tapeLength, n);
                lastEnd = n;
            } else {
                tape = set(tape, n, codeOf(kind));
                lastEnd = n + 1;
            }
        }
        return new Tape(tape, tapeLength, minSlot);
    }

    private static byte[] ensure(byte[] tape, int size) {
        return size + 1 > tape.length ? java.util.Arrays.copyOf(tape, size + 16) : tape;
    }

    private static byte[] set(byte[] tape, int n, int code) {
        tape = ensure(tape, n + 1);
        if (tape[n] != 0) {
            throw new TapeException("program.error.instruction_conflict",
                    "Two instructions end up on the same tape slot");
        }
        tape[n] = (byte) code;
        return tape;
    }
}
