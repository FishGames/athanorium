package io.github.fishgames.athanorium.core.sim;

/**
 * Float-based collision sampling after omsim (rules reference): atoms are discs of radius 29, arm bases and
 * walls discs of radius 20, hexagon size 82 x 71. Movements are sampled at fixed progress steps; atoms of the
 * same movement never collide with each other. All arithmetic is float with StrictMath for sin/cos/sqrt, so the
 * result is the same on every JVM and CPU.
 */
final class CollisionChecker {
    static final float HEX_SIZE_X = 82f;
    static final float HEX_SIZE_Y = 71f;
    static final float ATOM_RADIUS = 29f;
    static final float ARM_BASE_RADIUS = 20f;

    private float[] cx = new float[64];
    private float[] cy = new float[64];
    private float[] cr = new float[64];
    private int length;
    private int cursor;

    private final Simulation sim;
    boolean collided;
    Vec location = Vec.ZERO;

    CollisionChecker(Simulation sim) {
        this.sim = sim;
    }

    static float toX(int u, int v) {
        return HEX_SIZE_X * (u + 0.5f * v);
    }

    static float toY(int v) {
        return HEX_SIZE_Y * v;
    }

    private static float len2(float x, float y) {
        return x * x + y * y;
    }

    static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) StrictMath.sqrt((double) len2(dx, dy));
    }

    static float toRadians(int r) {
        return (float) (r * 60) * ((float) StrictMath.PI / 180f);
    }

    /** C round(): half away from zero. */
    private static int cRound(double d) {
        return (int) (d < 0 ? -StrictMath.floor(-d + 0.5) : StrictMath.floor(d + 0.5));
    }

    static Vec fromXy(float x, float y) {
        float u = x / HEX_SIZE_X - 0.5f * y / HEX_SIZE_Y;
        float v = y / HEX_SIZE_Y;
        float w = 0f - u - v;
        int ui = cRound(u);
        int vi = cRound(v);
        int wi = cRound(w);
        float uf = Math.abs(u - (float) ui);
        float vf = Math.abs(v - (float) vi);
        float wf = Math.abs(w - (float) wi);
        if (uf > vf && uf > wf) {
            ui = -vi - wi;
        } else if (vf > wf) {
            vi = -ui - wi;
        }
        return new Vec(ui, vi);
    }

    private void markAreaAndCheckBoard(float x, float y, float radius, int u, int v) {
        float d = dist(x, y, toX(u, v), toY(v));
        if (!(d < radius + ATOM_RADIUS)) {
            return;
        }
        if (sim.markUsedArea(new Vec(u, v))) {
            collided = true;
            location = new Vec(u, v);
        }
    }

    private void addCollider(float x, float y, float radius) {
        Vec p = fromXy(x, y);
        markAreaAndCheckBoard(x, y, radius, p.u(), p.v());
        markAreaAndCheckBoard(x, y, radius, p.u() + 1, p.v());
        markAreaAndCheckBoard(x, y, radius, p.u(), p.v() + 1);
        markAreaAndCheckBoard(x, y, radius, p.u() - 1, p.v() + 1);
        markAreaAndCheckBoard(x, y, radius, p.u() - 1, p.v());
        markAreaAndCheckBoard(x, y, radius, p.u(), p.v() - 1);
        markAreaAndCheckBoard(x, y, radius, p.u() + 1, p.v() - 1);
        addPlain(x, y, radius);
    }

    private void addPlain(float x, float y, float radius) {
        if (length == cx.length) {
            cx = java.util.Arrays.copyOf(cx, length * 2);
            cy = java.util.Arrays.copyOf(cy, length * 2);
            cr = java.util.Arrays.copyOf(cr, length * 2);
        }
        cx[length] = x;
        cy[length] = y;
        cr[length] = radius;
        length++;
        boolean hit = false;
        for (int i = 0; i < cursor; i++) {
            hit |= dist(cx[i], cy[i], x, y) < cr[i] + radius;
        }
        if (hit) {
            collided = true;
            location = fromXy(x, y);
        }
    }

    /**
     * Samples the motion phase. Returns true on a collision (position in {@link #location}).
     * Atoms in {@code sim.movements} are already at their final positions.
     */
    boolean run(float increment) {
        length = 0;
        cursor = 0;
        for (Simulation.Mech arm : sim.arms) {
            if (!arm.movement.isZero()) {
                continue;
            }
            addCollider(toX(arm.pos.u(), arm.pos.v()), toY(arm.pos.v()), ARM_BASE_RADIUS);
            cursor = length;
        }
        for (Vec wall : sim.walls) {
            addCollider(toX(wall.u(), wall.v()), toY(wall.v()), ARM_BASE_RADIUS);
        }
        int fixed = length;

        // offsets of the moving atoms relative to the grab position (final positions)
        float[][] offX = new float[sim.movements.size()][];
        float[][] offY = new float[sim.movements.size()][];
        for (int i = 0; i < sim.movements.size(); i++) {
            Simulation.Movement m = sim.movements.get(i);
            offX[i] = new float[m.atoms.size()];
            offY[i] = new float[m.atoms.size()];
            for (int j = 0; j < m.atoms.size(); j++) {
                Vec p = m.atoms.get(j).pos.minus(m.absGrab);
                offX[i][j] = toX(p.u(), p.v());
                offY[i][j] = toY(p.v());
            }
        }

        for (float progress = increment; progress < 1f; progress += increment) {
            length = fixed;
            cursor = fixed;
            for (Simulation.Mech arm : sim.arms) {
                if (arm.movement.isZero()) {
                    continue;
                }
                float x = toX(arm.pos.u(), arm.pos.v());
                float y = toY(arm.pos.v());
                float trx = toX(arm.movement.u(), arm.movement.v());
                float try_ = toY(arm.movement.v());
                x -= trx * (1f - progress);
                y -= try_ * (1f - progress);
                addCollider(x, y, ARM_BASE_RADIUS);
                cursor = length;
            }
            for (int i = 0; i < sim.movements.size(); i++) {
                Simulation.Movement m = sim.movements.get(i);
                float vx = toX(m.base.u(), m.base.v());
                float vy = toY(m.base.v());
                float rotation = toRadians(m.rotation);
                float armRotation = 0f;
                float r = (0f - rotation) * (1f - progress);
                int kind = m.type & 3;
                if (kind == Simulation.SWING) {
                    armRotation = rotation;
                } else if (kind == Simulation.TRACK) {
                    float trx = toX(m.translation.u(), m.translation.v());
                    float try_ = toY(m.translation.v());
                    vx -= trx * (1f - progress);
                    vy -= try_ * (1f - progress);
                }
                float baseRotation = toRadians(m.baseRotation);
                float gx = toX(m.grabberOffset.u(), m.grabberOffset.v());
                float gy = toY(m.grabberOffset.v());
                if ((m.type & Simulation.IS_PISTON) != 0) {
                    float len = (float) StrictMath.sqrt((double) len2(gx, gy));
                    float newLen = (float) StrictMath.sqrt((double) len2(gx, gy))
                            - ((float) m.pistonExtension) * HEX_SIZE_X * (1f - progress);
                    gx = newLen / len * gx;
                    gy = newLen / len * gy;
                }
                float grabberRotation = baseRotation - armRotation * (1f - progress);
                float grx = (float) StrictMath.cos((double) grabberRotation);
                float gry = (float) StrictMath.sin((double) grabberRotation);
                vx += gx * grx - gy * gry;
                vy += gx * gry + gy * grx;
                float rx = (float) StrictMath.cos((double) r);
                float ry = (float) StrictMath.sin((double) r);
                for (int j = 0; j < m.atoms.size(); j++) {
                    float x = offX[i][j];
                    float y = offY[i][j];
                    addCollider(vx + (x * rx - y * ry), vy + (x * ry + y * rx), ATOM_RADIUS);
                }
                cursor = length;
            }
        }
        return collided;
    }
}
