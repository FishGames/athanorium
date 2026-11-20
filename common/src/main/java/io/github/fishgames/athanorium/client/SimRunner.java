package io.github.fishgames.athanorium.client;

import io.github.fishgames.athanorium.core.hex.Hex;
import io.github.fishgames.athanorium.core.hex.HexField;
import io.github.fishgames.athanorium.core.program.Program;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.registry.Registries;
import io.github.fishgames.athanorium.core.sim.SimError;
import io.github.fishgames.athanorium.core.sim.Simulation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a program in the GUI. The core computes whole cycles; this class keeps the state before and after the
 * last cycle and the movements in between, so the screen can interpolate. The animation never changes a result.
 * The highest speed computes without animation on its own thread, never on the render thread.
 */
public final class SimRunner {
    /** Cycles per second for the speed steps; the last step ("max") has no animation. */
    public static final double[] SPEEDS = {2, 4, 8, 16};
    public static final int MAX_SPEED = SPEEDS.length;

    private final Simulation sim;
    private final int maxCycles;
    private List<Simulation.AtomView> prevAtoms;
    private List<Simulation.AtomView> atoms;
    private List<Simulation.ArmView> prevArms;
    private List<Simulation.ArmView> arms;
    private Map<Hex, Simulation.Motion> motionByAtom = new HashMap<>();
    private double progress = 1.0;
    private boolean playing;
    private SimError limitError;

    private Thread thread;
    private volatile boolean cancel;
    private volatile int threadCycle;
    private volatile boolean threadDone;

    public SimRunner(Puzzle puzzle, Program program, HexField field, Registries reg) {
        this.sim = new Simulation(puzzle, program, field, reg);
        this.maxCycles = puzzle.effectiveMaxCycles();
        this.atoms = sim.atoms();
        this.prevAtoms = atoms;
        this.arms = sim.armViews();
        this.prevArms = arms;
    }

    public Simulation sim() {
        return sim;
    }

    public boolean playing() {
        return playing;
    }

    public void setPlaying(boolean p) {
        playing = p && !finished();
    }

    /** True while the max speed thread is computing; the simulation must not be touched then. */
    public boolean inBackground() {
        return thread != null;
    }

    public int backgroundCycle() {
        return threadCycle;
    }

    public int maxCycles() {
        return maxCycles;
    }

    public boolean finished() {
        return sim.isComplete() || error() != null;
    }

    public boolean solved() {
        return sim.isComplete() && sim.error() == null;
    }

    /** Error of the run; reaching max_cycles is reported with the same key the server uses. */
    public SimError error() {
        return sim.error() != null ? sim.error() : limitError;
    }

    public int cycle() {
        return sim.cycle();
    }

    public double progress() {
        return Math.min(1.0, progress);
    }

    public List<Simulation.AtomView> prevAtoms() {
        return prevAtoms;
    }

    public List<Simulation.AtomView> atoms() {
        return atoms;
    }

    public List<Simulation.ArmView> prevArms() {
        return prevArms;
    }

    public List<Simulation.ArmView> arms() {
        return arms;
    }

    /** Movement of the atom that stood on the given hex before the last cycle, or null. */
    public Simulation.Motion motionOf(Hex start) {
        return motionByAtom.get(start);
    }

    /** Computes one cycle and starts its animation. */
    public void stepOnce() {
        if (finished() || inBackground()) {
            return;
        }
        prevAtoms = atoms;
        prevArms = arms;
        sim.step();
        afterStep();
        progress = 0.0;
    }

    private void afterStep() {
        atoms = sim.atoms();
        arms = sim.armViews();
        motionByAtom = new HashMap<>();
        for (Simulation.Motion m : sim.motions()) {
            for (Hex h : m.atoms()) {
                motionByAtom.put(h, m);
            }
        }
        if (!sim.isComplete() && sim.error() == null && sim.cycle() >= maxCycles) {
            limitError = new SimError(SimError.CYCLE_LIMIT, sim.cycle(), null);
        }
        if (finished()) {
            playing = false;
        }
    }

    /** Advances the animation by the given time. */
    public void advance(double seconds, int speed) {
        if (inBackground()) {
            if (threadDone) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                thread = null;
                afterStep();
                prevAtoms = atoms;
                prevArms = arms;
                progress = 1.0;
            }
            return;
        }
        if (speed >= MAX_SPEED) {
            if (playing && !finished()) {
                startBackground();
            }
            return;
        }
        progress += seconds * SPEEDS[speed];
        while (progress >= 1.0 && playing && !finished()) {
            double rest = progress - 1.0;
            stepOnce();
            progress = Math.min(rest, 0.999);
        }
    }

    private void startBackground() {
        cancel = false;
        threadDone = false;
        threadCycle = sim.cycle();
        thread = new Thread(() -> {
            while (!cancel && !sim.isComplete() && sim.error() == null && sim.cycle() < maxCycles) {
                sim.step();
                threadCycle = sim.cycle();
            }
            threadDone = true;
        }, "athanorium-fast-run");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops the max speed thread and waits for it. */
    public void cancelBackground() {
        if (thread != null) {
            cancel = true;
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
            afterStep();
            prevAtoms = atoms;
            prevArms = arms;
            progress = 1.0;
            playing = false;
        }
    }
}
