package org.example.resourcemod.core.sim;

import org.example.resourcemod.core.hex.Hex;
import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.molecule.Molecule;
import org.example.resourcemod.core.part.BondType;
import org.example.resourcemod.core.part.InstructionType;
import org.example.resourcemod.core.part.PartType;
import org.example.resourcemod.core.program.Part;
import org.example.resourcemod.core.program.Program;
import org.example.resourcemod.core.puzzle.Puzzle;
import org.example.resourcemod.core.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * The cycle simulation. The rules follow omsim (Opus Magnum simulator, public domain) as a reference; this is an
 * independent implementation. Pure Java, no randomness, no iteration over hash collections.
 * <p>
 * A cycle has two half-cycles. First half: grab and drop. Second half: all other instructions; movements are
 * collected first and carried out together, after a collision check at sampled intermediate steps.
 * In both halves, after the arms: area marking, input spawning, glyphs, output consumption.
 * <p>
 * Inside the core, positions use the (u, v) coordinates of the reference rules; see {@link Vec}.
 * The program must have passed the program check; the simulation does not validate it again.
 */
public final class Simulation {
    // movement kinds (low two bits of Movement.type)
    static final int SWING = 0;
    static final int PIVOT = 1;
    static final int PISTON_MOVE = 2;
    static final int TRACK = 3;
    static final int IS_PISTON = 4;

    private static final int MAX_ARM_DIRECTIONS = 6;

    /** Arm or glyph. */
    static final class Mech {
        PartType type;
        Vec pos;
        Hex worldPos;
        Vec du;
        Vec dv;
        int armRotation;
        boolean grabbing;
        int grabMask;
        boolean movedGrabbed;
        Vec movement = Vec.ZERO;
        Tape tape = Tape.EMPTY;
        int startCycle;
        int step;
        boolean piston;

        Vec rel(int a, int b, int w) {
            return new Vec(du.u() * a + dv.u() * b + pos.u() * w, du.v() * a + dv.v() * b + pos.v() * w);
        }
    }

    static final class MovingAtom {
        final SimAtom atom;
        final Vec start;
        Vec pos;

        MovingAtom(SimAtom atom, Vec pos) {
            this.atom = atom;
            this.start = pos;
            this.pos = pos;
        }
    }

    static final class Movement {
        int type;
        Vec base;
        int baseRotation;
        Vec grabberOffset;
        Vec absGrab;
        int rotation;
        Vec translation = Vec.ZERO;
        int pistonExtension;
        final List<MovingAtom> atoms = new ArrayList<>();
    }

    private static final class Cell {
        SimAtom atom;
        boolean used;
        boolean wall;
        boolean moving;
        int moveIndex;
    }

    private static final class Io {
        boolean input;
        final List<Vec> positions = new ArrayList<>();
        final List<SimAtom> atoms = new ArrayList<>();
        final Map<Long, SimAtom> byPos = new HashMap<>();
        int produced;
        boolean blocked;
    }

    // configuration
    private final Registries reg;
    private final HexField field;
    private final int target;
    private final boolean production;

    // board
    private final Map<Long, Cell> grid = new HashMap<>();
    private long area;
    final List<Mech> arms = new ArrayList<>();
    private final List<Mech> glyphs = new ArrayList<>();
    private final List<Io> ios = new ArrayList<>();
    final List<Vec> walls = new ArrayList<>();
    private final TrackTable tracks;
    private int tapePeriod;
    private final long instructionCount;

    // state
    private int cycle;
    private int half = 1;
    private SimError error;
    private boolean complete;
    final List<Movement> movements = new ArrayList<>();
    private final List<Cell> movedFrom = new ArrayList<>();
    private final List<SimAtom> recentAtoms = new ArrayList<>();

    public Simulation(Puzzle puzzle, Program program, HexField field, Registries reg) {
        this.reg = reg;
        this.field = field;
        this.target = puzzle.effectiveTestProducts();
        this.production = field.isBounded();

        if (field.isBounded()) {
            for (Hex h : field.wallHexes()) {
                Vec w = Vec.of(h);
                walls.add(w);
                Cell c = cell(w);
                c.used = true;
                c.wall = true;
            }
        }

        List<Part> trackParts = new ArrayList<>();
        for (Part p : program.parts()) {
            PartType pt = reg.parts.require(p.type());
            if (pt.kind() == PartType.Kind.TRACK) {
                trackParts.add(p);
            }
        }
        this.tracks = TrackTable.of(trackParts);

        // parts in program order: inputs/outputs first pass for area (like the reference: io, glyphs, arms, tracks)
        List<Part> inputs = new ArrayList<>();
        for (Part p : program.parts()) {
            PartType pt = reg.parts.require(p.type());
            switch (pt.kind()) {
                case INPUT -> ios.add(makeIo(p, puzzle.inputs().get(p.inputIndex()), true));
                case OUTPUT -> ios.add(makeIo(p, puzzle.product(), false));
                case GLYPH -> glyphs.add(makeMech(p, pt, 1));
                case ARM -> arms.add(makeMech(p, pt, p.length()));
                default -> {
                }
            }
        }
        for (Io io : ios) {
            for (Vec p : io.positions) {
                markUsedArea(p);
            }
        }
        for (Mech g : glyphs) {
            markUsedArea(g.pos);
            for (Hex h : g.type.footprintBesidesOrigin()) {
                markUsedArea(Vec.of(g.worldPos.plus(h.rotateCcw(g.armRotation))));
            }
        }
        for (Mech a : arms) {
            markUsedArea(a.pos);
        }
        for (Part p : trackParts) {
            for (Hex h : p.trackHexes()) {
                markUsedArea(Vec.of(p.pos().plus(h)));
            }
        }

        // tapes
        long instr = 0;
        int minStart = Integer.MAX_VALUE;
        int armIndex = 0;
        for (Part p : program.parts()) {
            PartType pt = reg.parts.require(p.type());
            if (pt.kind() != PartType.Kind.ARM) {
                continue;
            }
            Mech m = arms.get(armIndex++);
            NavigableMap<Integer, InstructionType.Kind> kinds = new TreeMap<>();
            for (Map.Entry<Integer, Id> e : p.instructions().entrySet()) {
                kinds.put(e.getKey(), reg.instructions.require(e.getValue()).kind());
            }
            m.tape = Tape.compile(kinds, m.pos, p.length(), tracks);
            m.startCycle = m.tape.length() == 0 ? 0 : m.tape.startSlot();
            instr += m.tape.instructionCount();
            if (m.tape.length() > tapePeriod) {
                tapePeriod = m.tape.length();
            }
        }
        for (Mech m : arms) {
            minStart = Math.min(minStart, m.startCycle);
        }
        for (Mech m : arms) {
            m.startCycle -= minStart;
        }
        this.instructionCount = instr;

        markArmArea();
        flagBlockedInputs();
        spawnInputs();
    }

    // setup helpers

    private Io makeIo(Part part, Molecule molecule, boolean input) {
        Io io = new Io();
        io.input = input;
        int rot = part.rotation();
        Map<Hex, Integer> index = new HashMap<>();
        for (Molecule.Atom a : molecule.atoms()) {
            Hex world = part.pos().plus(a.pos().rotateCcw(rot));
            Vec p = Vec.of(world);
            SimAtom atom = new SimAtom(a.element(), 0);
            io.positions.add(p);
            io.atoms.add(atom);
            io.byPos.put(p.key(), atom);
            index.put(a.pos(), io.atoms.size() - 1);
        }
        for (Molecule.Bond b : molecule.bonds()) {
            Hex delta = b.to().minus(b.from());
            int dir = Hex.directionOf(delta.rotateCcw(rot));
            int type = reg.bondTypes.require(b.type()).index();
            SimAtom from = io.atoms.get(index.get(b.from()));
            SimAtom to = io.atoms.get(index.get(b.to()));
            from.bonds |= SimAtom.bit(type, dir);
            to.bonds |= SimAtom.bit(type, (dir + 3) % 6);
        }
        return io;
    }

    private Mech makeMech(Part part, PartType type, int length) {
        Mech m = new Mech();
        m.type = type;
        m.pos = Vec.of(part.pos());
        m.worldPos = part.pos();
        int rot = Math.floorMod(part.rotation(), 6);
        m.du = Vec.dirU(rot).times(length);
        m.dv = Vec.dirV(rot).times(length);
        m.armRotation = rot;
        if (type.kind() == PartType.Kind.ARM) {
            m.step = type.grabberStep();
            m.piston = type.isPiston();
        }
        return m;
    }

    // board access

    private Cell cell(Vec p) {
        return grid.computeIfAbsent(p.key(), k -> new Cell());
    }

    private Cell lookup(Vec p) {
        return grid.get(p.key());
    }

    /** Marks a field as used. Returns true if it holds an atom (a collision when something moves over it). */
    boolean markUsedArea(Vec p) {
        Cell c = cell(p);
        if (!c.used) {
            c.used = true;
            area++;
        }
        return c.atom != null;
    }

    private SimAtom atomAt(Vec p) {
        Cell c = lookup(p);
        return c == null ? null : c.atom;
    }

    private void report(String key, Vec pos) {
        if (error == null) {
            error = new SimError(key, cycle, pos == null ? null : pos.toHex());
        }
    }

    private void insertAtom(Vec p, SimAtom atom) {
        Cell c = cell(p);
        if (c.atom != null) {
            report(SimError.OVERLAP, p);
            return;
        }
        if (!c.used) {
            c.used = true;
            area++;
        }
        c.atom = atom;
        c.moving = false;
    }

    private void removeAtom(Cell c) {
        c.atom = null;
        c.moving = false;
    }

    // public interface

    public int cycle() {
        return cycle;
    }

    public boolean isComplete() {
        return complete;
    }

    public SimError error() {
        return error;
    }

    public long area() {
        return area;
    }

    public long instructionCount() {
        return instructionCount;
    }

    /** Read-only view of an atom for rendering and tests. {@code bonds} uses the bit layout of the bond registry. */
    public record AtomView(Hex pos, Id element, long bonds, boolean grabbed) {
        /** True if the atom has a bond (of any type) in the given direction 0..5. */
        public boolean bondedIn(int direction) {
            return (bonds & SimAtom.dirMask(direction)) != 0;
        }
    }

    public record ArmView(Hex pos, int rotation, int length, boolean grabbing) {
    }

    /** All atoms on the board, sorted by position (q, then r). */
    public List<AtomView> atoms() {
        List<AtomView> out = new ArrayList<>();
        for (Map.Entry<Long, Cell> e : grid.entrySet()) {
            Cell c = e.getValue();
            if (c.atom == null) {
                continue;
            }
            int u = (int) (e.getKey() >> 32);
            int v = (int) (long) e.getKey();
            out.add(new AtomView(new Vec(u, v).toHex(), c.atom.element, c.atom.bonds, c.atom.grabbed > 0));
        }
        out.sort(Comparator.comparingInt((AtomView a) -> a.pos().q()).thenComparingInt(a -> a.pos().r()));
        return out;
    }

    public List<ArmView> armViews() {
        List<ArmView> out = new ArrayList<>();
        for (Mech m : arms) {
            out.add(new ArmView(m.pos.toHex(), Math.floorMod(m.armRotation, 6),
                    Math.abs(m.du.u()) | Math.abs(m.du.v()), m.grabbing));
        }
        return out;
    }

    /**
     * One rigid movement of the last cycle, for the animation only: the atoms that stood on {@code atoms} before
     * the cycle were either turned by {@code rotation} steps of 60 degrees (counter-clockwise positive) around
     * {@code center} or shifted by {@code translation}.
     */
    public record Motion(Hex center, int rotation, Hex translation, List<Hex> atoms) {
    }

    /** The movements of the cycle that was computed last. Never influences the result. */
    public List<Motion> motions() {
        List<Motion> out = new ArrayList<>();
        for (Movement m : movements) {
            List<Hex> from = new ArrayList<>();
            for (MovingAtom a : m.atoms) {
                from.add(a.start.toHex());
            }
            Vec center = (m.type & 3) == SWING ? m.base : m.absGrab;
            out.add(new Motion(center.toHex(), m.rotation, m.translation.toHex(), from));
        }
        return out;
    }

    /**
     * Absolute tape slot the arm (index among the arms, in program order) works on in the given cycle, or -1
     * if its tape has not started yet or is empty. For display only.
     */
    public int tapeSlot(int arm, int atCycle) {
        Mech m = arms.get(arm);
        if (m.tape.length() == 0 || atCycle < m.startCycle) {
            return -1;
        }
        int index = atCycle - m.startCycle;
        if (tapePeriod != 0) {
            index %= tapePeriod;
        }
        return m.tape.startSlot() + index;
    }

    /** Products made so far (smallest count over all outputs). */
    public int products() {
        int min = Integer.MAX_VALUE;
        for (Io io : ios) {
            if (!io.input) {
                min = Math.min(min, io.produced);
            }
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /**
     * Runs one full cycle. Returns false when the run is over (solved or an error occurred); check
     * {@link #isComplete()} and {@link #error()}.
     */
    public boolean step() {
        if (complete || error != null) {
            return false;
        }
        for (half = 1; half <= 2; half++) {
            performArmInstructions();
            markArmArea();
            flagBlockedInputs();
            spawnInputs();
            resetTemporaryFlags();
            applyGlyphs();
            consumeOutputs();
        }
        cycle++;
        half = 1;
        int min = Integer.MAX_VALUE;
        boolean anyOutput = false;
        for (Io io : ios) {
            if (!io.input) {
                anyOutput = true;
                min = Math.min(min, io.produced);
            }
        }
        complete = anyOutput && min >= target;
        return error == null && !complete;
    }

    /** Runs until solved, error or the cycle limit. */
    public SimResult run(int cycleLimit) {
        while (cycle < cycleLimit) {
            if (!step()) {
                break;
            }
        }
        if (error != null) {
            return SimResult.failed(error, instructionCount);
        }
        if (complete) {
            return SimResult.solved(cycle, area, instructionCount);
        }
        return SimResult.failed(new SimError(SimError.CYCLE_LIMIT, cycle, null), instructionCount);
    }

    /** Convenience: builds a simulation and runs it. */
    public static SimResult simulate(Puzzle puzzle, Program program, HexField field, Registries reg, int cycleLimit) {
        return new Simulation(puzzle, program, field, reg).run(cycleLimit);
    }

    // arms

    private int cabinetFor(Vec p) {
        Hex h = p.toHex();
        int len = h.length();
        int r = field.radius();
        if (len <= r) {
            return 1;
        }
        return len == r + 1 ? 255 : 0;
    }

    private void markArmArea() {
        for (Mech m : arms) {
            for (int dir = 0; dir < 6; dir += m.step) {
                Vec offset = Vec.dirU(dir);
                if (production && cabinetFor(m.rel(offset.u(), offset.v(), 1)) == 255) {
                    report(SimError.OUTSIDE_FIELD, m.rel(offset.u(), offset.v(), 1));
                }
                Vec du = m.du;
                Vec dv = m.dv;
                while (true) {
                    Vec p = new Vec(du.u() * offset.u() + dv.u() * offset.v() + m.pos.u(),
                            du.v() * offset.u() + dv.v() * offset.v() + m.pos.v());
                    markUsedArea(p);
                    if (du.isZero()) {
                        break;
                    }
                    du = adjustAxis(du, -1);
                    dv = adjustAxis(dv, -1);
                }
            }
        }
    }

    private static Vec adjustAxis(Vec p, int delta) {
        int u = p.u();
        int v = p.v();
        if (u > 0) {
            u += delta;
        }
        if (u < 0) {
            u -= delta;
        }
        if (v > 0) {
            v += delta;
        }
        if (v < 0) {
            v -= delta;
        }
        return new Vec(u, v);
    }

    private void recordSwingArea(Vec base, Vec position, int rotation) {
        Vec offset = position.minus(base);
        if (rotation == -1) {
            offset = new Vec(offset.u() + offset.v(), -offset.u());
        }
        int armLength = Math.abs(offset.u()) | Math.abs(offset.v());
        offset = offset.normalizeAxis();
        int ou = offset.u();
        int ov = offset.v();
        if (production) {
            int original = cabinetFor(new Vec(base.u() + armLength * ou, base.v() + armLength * ov));
            Vec next = new Vec(base.u() - armLength * ov, base.v() + armLength * (ou + ov));
            if (cabinetFor(next) != original) {
                report(SimError.OUTSIDE_FIELD, next);
                return;
            }
            if (armLength == 3) {
                Vec extra = new Vec(base.u() + 2 * ou - 2 * ov, base.v() + 2 * ou + 4 * ov);
                if (cabinetFor(extra) != original) {
                    report(SimError.OUTSIDE_FIELD, extra);
                    return;
                }
            }
        }
        if (armLength == 3) {
            markUsedArea(new Vec(base.u() + 3 * ou - ov, base.v() + ou + 4 * ov));
            markUsedArea(new Vec(base.u() + 2 * ou - 2 * ov, base.v() + 2 * ou + 4 * ov));
            markUsedArea(new Vec(base.u() + ou - 3 * ov, base.v() + 3 * ou + 4 * ov));
        }
        if (armLength >= 2) {
            markUsedArea(new Vec(base.u() + 2 * ou - ov, base.v() + ou + 3 * ov));
            markUsedArea(new Vec(base.u() + ou - ov, base.v() + ou + 2 * ov));
            markUsedArea(new Vec(base.u() + ou - 2 * ov, base.v() + 2 * ou + 3 * ov));
        }
    }

    private static boolean movementsEqual(Movement a, Movement b) {
        if (a.rotation == 0 && b.rotation == 0) {
            return a.translation.equals(b.translation);
        }
        if (a.rotation != b.rotation) {
            return false;
        }
        Vec ac = a.type == SWING ? a.base : a.absGrab;
        Vec bc = b.type == SWING ? b.base : b.absGrab;
        return ac.equals(bc);
    }

    private void moveAtoms(Cell start, Movement movement) {
        if (start.atom == null && start.moving) {
            // atom already moves: both movements must be the same
            if (!movementsEqual(movement, pendingMovements.get(start.moveIndex))) {
                report(SimError.MOVED_TWO_WAYS, movement.absGrab);
            }
            return;
        }
        int index = pendingMovements.size();
        pendingMovements.add(movement);
        pickUp(start, movement.absGrab, movement, index);
        // breadth-first search over the bonded molecule
        for (int cursor = 0; cursor < movement.atoms.size(); cursor++) {
            MovingAtom m = movement.atoms.get(cursor);
            for (int dir = 0; dir < 6; dir++) {
                if ((m.atom.bonds & SimAtom.dirMask(dir)) == 0) {
                    continue;
                }
                Vec p = m.pos.plus(Vec.dirU(dir));
                Cell c = lookup(p);
                if (c == null || c.atom == null) {
                    continue;
                }
                pickUp(c, p, movement, index);
            }
        }
    }

    private void pickUp(Cell c, Vec pos, Movement movement, int index) {
        movement.atoms.add(new MovingAtom(c.atom, pos));
        c.atom = null;
        c.moving = true;
        c.moveIndex = index;
        movedFrom.add(c);
    }

    private final List<Movement> pendingMovements = new ArrayList<>();

    private static Vec applyMovement(Vec base, Vec translation, Vec u, Vec v, Vec position) {
        int du = position.u() - base.u();
        int dv = position.v() - base.v();
        return new Vec(u.u() * du + v.u() * dv + base.u() + translation.u(),
                u.v() * du + v.v() * dv + base.v() + translation.v());
    }

    private void performArmInstructions() {
        movements.clear();
        pendingMovements.clear();
        movedFrom.clear();
        for (Mech m : arms) {
            m.movement = Vec.ZERO;
            m.movedGrabbed = false;
            if (cycle < m.startCycle) {
                continue;
            }
            int index = cycle - m.startCycle;
            if (tapePeriod != 0) {
                index %= tapePeriod;
            }
            if (index >= m.tape.length()) {
                continue;
            }
            int inst = m.tape.code(index);
            if (inst == ' ' || inst == 0) {
                continue;
            }
            boolean firstHalfInst = inst == 'f' || inst == 'r';
            if ((half == 1) != firstHalfInst) {
                continue;
            }
            Vec trackMotion = Vec.ZERO;
            int armLength = Math.abs(m.du.u()) | Math.abs(m.du.v());
            if (inst == 'f') {
                if (m.grabbing) {
                    continue;
                }
            } else if (inst == 'r') {
                if (!m.grabbing) {
                    continue;
                }
            } else if ((inst == 'w' || inst == 's') && !m.piston) {
                report(SimError.NOT_A_PISTON, m.pos);
                continue;
            } else if (inst == 'w') {
                if (armLength == 3) {
                    continue;
                }
            } else if (inst == 's') {
                if (armLength == 1) {
                    continue;
                }
            } else if (inst == 't' || inst == 'g') {
                Vec motion = inst == 't' ? tracks.minusMotion(m.pos) : tracks.plusMotion(m.pos);
                if (motion == null) {
                    report(SimError.NOT_ON_TRACK, m.pos);
                    continue;
                }
                trackMotion = motion;
                if (trackMotion.isZero()) {
                    continue;
                }
            }
            m.movedGrabbed = true;
            for (int dir = 0; dir < 6; dir += m.step) {
                Vec offset = Vec.dirU(dir);
                Vec p = m.rel(offset.u(), offset.v(), 1);
                if (inst == 'a') {
                    recordSwingArea(m.pos, p, 1);
                }
                if (inst == 'd') {
                    recordSwingArea(m.pos, p, -1);
                }
                Cell c = lookup(p);
                if (c == null || (c.atom == null && !c.moving)) {
                    continue;
                }
                boolean removed = c.atom == null;
                if (inst == 'f' && !removed) {
                    c.atom.grabbed++;
                    m.grabMask |= 1 << dir;
                    continue;
                }
                if ((m.grabMask & (1 << dir)) == 0 || (!removed && c.atom.grabbed == 0)) {
                    continue;
                }
                if (inst == 'r' && !removed && c.atom.grabbed > 0) {
                    c.atom.grabbed--;
                    continue;
                }
                Movement mv = new Movement();
                mv.type = m.piston ? IS_PISTON : 0;
                mv.base = m.pos;
                mv.baseRotation = m.armRotation;
                mv.grabberOffset = new Vec(offset.u() * armLength, offset.v() * armLength);
                mv.absGrab = p;
                switch (inst) {
                    case 'q' -> {
                        mv.type |= PIVOT;
                        mv.rotation = 1;
                        moveAtoms(c, mv);
                    }
                    case 'e' -> {
                        mv.type |= PIVOT;
                        mv.rotation = -1;
                        moveAtoms(c, mv);
                    }
                    case 'a' -> {
                        mv.type |= SWING;
                        mv.rotation = 1;
                        moveAtoms(c, mv);
                    }
                    case 'd' -> {
                        mv.type |= SWING;
                        mv.rotation = -1;
                        moveAtoms(c, mv);
                    }
                    case 'w' -> {
                        mv.type |= PISTON_MOVE;
                        mv.pistonExtension = 1;
                        mv.translation = m.du.normalizeAxis();
                        moveAtoms(c, mv);
                    }
                    case 's' -> {
                        mv.type |= PISTON_MOVE;
                        mv.pistonExtension = -1;
                        Vec t = m.du.normalizeAxis();
                        mv.translation = new Vec(-t.u(), -t.v());
                        moveAtoms(c, mv);
                    }
                    case 't', 'g' -> {
                        mv.type |= TRACK;
                        mv.translation = trackMotion;
                        moveAtoms(c, mv);
                    }
                    default -> {
                    }
                }
            }
            // transform the arm itself
            switch (inst) {
                case 'w' -> {
                    m.du = adjustAxis(m.du, 1);
                    m.dv = adjustAxis(m.dv, 1);
                }
                case 's' -> {
                    m.du = adjustAxis(m.du, -1);
                    m.dv = adjustAxis(m.dv, -1);
                }
                case 't', 'g' -> {
                    m.pos = m.pos.plus(trackMotion);
                    m.movement = trackMotion;
                }
                case 'f' -> m.grabbing = true;
                case 'r' -> {
                    m.grabMask = 0;
                    m.grabbing = false;
                }
                default -> {
                }
            }
            if (inst == 'a' || inst == 'd') {
                int r = inst == 'a' ? 1 : -1;
                Vec u = Vec.dirU(r);
                Vec v = Vec.dirV(r);
                Vec nu = new Vec(m.du.u() * u.u() + m.dv.u() * u.v(), m.du.v() * u.u() + m.dv.v() * u.v());
                Vec nv = new Vec(m.du.u() * v.u() + m.dv.u() * v.v(), m.du.v() * v.u() + m.dv.v() * v.v());
                m.du = nu;
                m.dv = nv;
                m.armRotation = (m.armRotation + r) % 6;
            }
        }
        if (half == 2) {
            carryOutMovements();
        }
        movedFrom.clear();
    }

    private void carryOutMovements() {
        // a static arm must not hold an atom that another arm moves
        for (Mech m : arms) {
            if (cycle < m.startCycle || m.movedGrabbed) {
                continue;
            }
            for (int dir = 0; dir < 6; dir += m.step) {
                if ((m.grabMask & (1 << dir)) == 0) {
                    continue;
                }
                Vec offset = Vec.dirU(dir);
                Vec pos = m.rel(offset.u(), offset.v(), 1);
                Cell c = lookup(pos);
                if (c != null && c.atom == null && c.moving) {
                    report(SimError.HELD_STATIONARY, pos);
                }
            }
        }

        movements.clear();
        movements.addAll(pendingMovements);
        movements.sort(Comparator.comparingInt(mv -> mv.atoms.size()));

        int maxRotationDistance = 1;
        for (Movement m : movements) {
            Vec base = (m.type & 3) == SWING ? m.base : m.absGrab;
            Vec u = Vec.dirU(m.rotation);
            Vec v = Vec.dirV(m.rotation);
            for (MovingAtom a : m.atoms) {
                a.pos = applyMovement(base, m.translation, u, v, a.pos);
                if (m.rotation != 0) {
                    int distance = a.pos.minus(base).hexLength();
                    if (distance > maxRotationDistance) {
                        maxRotationDistance = distance;
                    }
                    a.atom.bonds = SimAtom.rotateBonds(a.atom.bonds, m.rotation);
                    a.atom.recent = SimAtom.rotateGroup(a.atom.recent, m.rotation);
                }
            }
            if ((m.type & 3) == TRACK) {
                m.base = m.base.plus(m.translation);
            } else if ((m.type & 3) == PISTON_MOVE) {
                Vec g = m.grabberOffset.normalizeAxis();
                int nu = g.u() != 0 ? g.u() * (m.grabberOffset.u() / g.u() + m.pistonExtension) : 0;
                int nv = g.v() != 0 ? g.v() * (m.grabberOffset.v() / g.v() + m.pistonExtension) : 0;
                m.grabberOffset = new Vec(nu, nv);
            }
            m.absGrab = applyMovement(base, m.translation, u, v, m.absGrab);
            if ((m.type & 3) == SWING) {
                m.baseRotation = (m.baseRotation + m.rotation) % 6;
            }
        }

        double increment = 0.25 / StrictMath.pow(2, cRoundD(StrictMath.log(maxRotationDistance) / StrictMath.log(2)));
        if (!(increment <= 0.125)) {
            increment = 0.125;
        }
        CollisionChecker checker = new CollisionChecker(this);
        if (checker.run((float) increment)) {
            report(SimError.COLLISION, checker.location);
        }

        for (Movement m : movements) {
            for (MovingAtom a : m.atoms) {
                insertAtom(a.pos, a.atom);
            }
        }
        for (Cell c : movedFrom) {
            c.moving = false;
        }
    }

    private static double cRoundD(double d) {
        return d < 0 ? -StrictMath.floor(-d + 0.5) : StrictMath.floor(d + 0.5);
    }

    // inputs and outputs

    private void flagBlockedInputs() {
        for (Io io : ios) {
            io.blocked = false;
        }
        for (Io io : ios) {
            if (!io.input) {
                continue;
            }
            for (Vec p : io.positions) {
                if (atomAt(p) != null) {
                    io.blocked = true;
                    break;
                }
            }
        }
    }

    private void spawnInputs() {
        for (Io io : ios) {
            if (!io.input || io.blocked) {
                continue;
            }
            for (int i = 0; i < io.positions.size(); i++) {
                SimAtom t = io.atoms.get(i);
                SimAtom a = new SimAtom(t.element, t.bonds);
                insertAtom(io.positions.get(i), a);
            }
        }
    }

    private void consumeOutputs() {
        for (Io io : ios) {
            if (io.input) {
                continue;
            }
            Cell start = lookup(io.positions.get(0));
            if (start == null || start.atom == null) {
                continue;
            }
            List<Cell> molecule = new ArrayList<>();
            List<Vec> positions = new ArrayList<>();
            collectMolecule(start, io.positions.get(0), molecule, positions);
            if (molecule.size() != io.positions.size()) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < molecule.size(); i++) {
                SimAtom a = molecule.get(i).atom;
                if (a.grabbed > 0) {
                    match = false;
                    break;
                }
                SimAtom t = io.byPos.get(positions.get(i).key());
                if (t == null || !t.element.equals(a.element) || t.bonds != a.bonds) {
                    match = false;
                    break;
                }
            }
            if (!match) {
                continue;
            }
            for (Cell c : molecule) {
                removeAtom(c);
            }
            io.produced++;
        }
    }

    private void collectMolecule(Cell start, Vec startPos, List<Cell> cells, List<Vec> positions) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        cells.add(start);
        positions.add(startPos);
        seen.add(startPos.key());
        for (int cursor = 0; cursor < cells.size(); cursor++) {
            SimAtom a = cells.get(cursor).atom;
            Vec pos = positions.get(cursor);
            for (int dir = 0; dir < 6; dir++) {
                if ((a.bonds & SimAtom.dirMask(dir)) == 0) {
                    continue;
                }
                Vec p = pos.plus(Vec.dirU(dir));
                Cell c = lookup(p);
                if (c == null || c.atom == null || !seen.add(p.key())) {
                    continue;
                }
                cells.add(c);
                positions.add(p);
            }
        }
    }

    // glyphs

    private void resetTemporaryFlags() {
        for (SimAtom a : recentAtoms) {
            a.recent = 0;
        }
        recentAtoms.clear();
    }

    private int bondDirection(Mech m, int a, int b) {
        return Vec.directionOf(new Vec(m.du.u() * a + m.dv.u() * b, m.du.v() * a + m.dv.v() * b));
    }

    private void addBond(Mech m, SimAtom a, SimAtom b, int u, int v) {
        int normal = reg.bondTypes.require(BondType.NORMAL).index();
        int ab = bondDirection(m, u, v);
        int ba = bondDirection(m, -u, -v);
        if (ab < 0 || ba < 0) {
            return;
        }
        long other = SimAtom.dirMask(ab) & ~SimAtom.bit(normal, ab);
        long otherBack = SimAtom.dirMask(ba) & ~SimAtom.bit(normal, ba);
        if ((a.bonds & other) != 0 || (b.bonds & otherBack) != 0) {
            return;
        }
        a.bonds |= SimAtom.bit(normal, ab);
        b.bonds |= SimAtom.bit(normal, ba);
    }

    private void applyGlyphs() {
        for (Mech m : glyphs) {
            switch (m.type.glyphEffect()) {
                case BIND -> {
                    SimAtom a = atomAt(m.rel(0, 0, 1));
                    SimAtom b = atomAt(m.rel(1, 0, 1));
                    if (a != null && b != null) {
                        addBond(m, a, b, 1, 0);
                    }
                }
                case UNBIND -> {
                    SimAtom a = atomAt(m.rel(0, 0, 1));
                    SimAtom b = atomAt(m.rel(1, 0, 1));
                    if (a != null && b != null) {
                        int ab = bondDirection(m, 1, 0);
                        int ba = bondDirection(m, -1, 0);
                        if (ab >= 0 && ba >= 0 && (a.bonds & SimAtom.dirMask(ab)) != 0
                                && (b.bonds & SimAtom.dirMask(ba)) != 0) {
                            a.bonds &= ~SimAtom.dirMask(ab);
                            a.recent |= 1 << ab;
                            recentAtoms.add(a);
                            b.bonds &= ~SimAtom.dirMask(ba);
                            b.recent |= 1 << ba;
                            recentAtoms.add(b);
                        }
                    }
                }
                case MULTI_BIND -> {
                    SimAtom center = atomAt(m.rel(0, 0, 1));
                    SimAtom a = atomAt(m.rel(1, 0, 1));
                    SimAtom b = atomAt(m.rel(0, -1, 1));
                    SimAtom c = atomAt(m.rel(-1, 1, 1));
                    if (center != null && a != null) {
                        addBond(m, a, center, -1, 0);
                    }
                    if (center != null && b != null) {
                        addBond(m, b, center, 0, 1);
                    }
                    if (center != null && c != null) {
                        addBond(m, c, center, 1, -1);
                    }
                }
                case DISPOSER -> {
                    Cell c = lookup(m.rel(0, 0, 1));
                    if (c != null && c.atom != null && c.atom.bonds == 0 && c.atom.recent == 0
                            && c.atom.grabbed == 0) {
                        removeAtom(c);
                    }
                }
                default -> {
                }
            }
        }
    }
}
