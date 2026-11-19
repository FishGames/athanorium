package org.example.resourcemod.block;

import org.example.resourcemod.ModCore;
import org.example.resourcemod.core.slot.CalcResult;
import org.example.resourcemod.core.slot.Computation;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * All simulations on the server run on this one worker thread, never in the server tick: checking submitted
 * solutions, re-computation and checks on load. The worker computes in slices and takes a waiting submission
 * first before every slice, so a long re-computation never blocks a submission. Results are handed back to
 * the main thread, which decides whether they still apply.
 */
public final class SimWorker {
    private static final int SLICE_CYCLES = 1000;
    public static final String INTERNAL_ERROR = "slot.reject.internal_error";

    private static final class Job {
        final Supplier<Computation> factory;
        final Consumer<CalcResult> onDone;
        final boolean submission;
        Computation computation;

        Job(Supplier<Computation> factory, Consumer<CalcResult> onDone, boolean submission) {
            this.factory = factory;
            this.onDone = onDone;
            this.submission = submission;
        }
    }

    private final ConcurrentLinkedDeque<Job> submissions = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Job> recomputations = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedQueue<Runnable> finished = new ConcurrentLinkedQueue<>();
    private final Semaphore wake = new Semaphore(0);
    private volatile boolean running = true;
    private final Thread thread;

    public SimWorker() {
        thread = new Thread(this::loop, ModCore.MOD_ID + "-simulation");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * @param factory    builds the computation from an immutable snapshot; runs on the worker
     * @param onDone     receives the result on the main thread
     * @param submission true for a solution submitted by a player (takes precedence)
     */
    public void enqueue(Supplier<Computation> factory, Consumer<CalcResult> onDone, boolean submission) {
        (submission ? submissions : recomputations).addLast(new Job(factory, onDone, submission));
        wake.release();
    }

    /** Runs the callbacks of finished jobs. Called once per server tick on the main thread. */
    public void drain() {
        Runnable r;
        while ((r = finished.poll()) != null) {
            r.run();
        }
    }

    public void shutdown() {
        running = false;
        submissions.clear();
        recomputations.clear();
        thread.interrupt();
    }

    private void loop() {
        while (running) {
            Job job = submissions.pollFirst();
            if (job == null) {
                job = recomputations.pollFirst();
            }
            if (job == null) {
                try {
                    wake.acquire();
                } catch (InterruptedException e) {
                    return;
                }
                continue;
            }
            Job current = job;
            try {
                if (current.computation == null) {
                    current.computation = current.factory.get();
                }
                if (current.computation.advance(SLICE_CYCLES)) {
                    CalcResult result = current.computation.result();
                    finished.add(() -> current.onDone.accept(result));
                } else {
                    (current.submission ? submissions : recomputations).addFirst(current);
                    wake.release();
                }
            } catch (RuntimeException e) {
                ModCore.LOGGER.error("Simulation failed unexpectedly", e);
                finished.add(() -> current.onDone.accept(
                        CalcResult.failed(INTERNAL_ERROR, String.valueOf(e.getMessage()), "", "")));
            }
        }
    }
}
