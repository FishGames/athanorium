package io.github.fishgames.athanorium.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import io.github.fishgames.athanorium.ModCore;

import java.util.HashMap;
import java.util.Map;

/** Small drawing helpers for the puzzle GUI. Every sprite is its own texture file under textures/gui/puzzle. */
final class Gfx {
    static final int BG = 0xFF171122, PANEL = 0xFF241B36, PANEL_LIGHT = 0xFF32264A, PANEL_DARK = 0xFF1B142B;
    static final int BORDER = 0xFF5B4A86, BORDER_BRIGHT = 0xFF8F7BD0, GOLD = 0xFFE2B04A;
    static final int TEXT = 0xFFE8E0F5, TEXT_DIM = 0xFF9C90B8, GOOD = 0xFF6FE0A0, BAD = 0xFFFF6B6B, CYAN = 0xFF5FD8E0;
    static final int ACTIVE = 0xFF4A3A78, TAPE_PLAY = 0xFF2F5F66, TAPE_ALT = 0xFF201832, ARM_NO = 0xFF1A1426;
    static final int INK = 0xFF0E0A17;
    static final int GOLD_DARK = mix(GOLD, 0xFF000000, 0.31f);
    static final int BORDER_DIM = mix(BORDER, PANEL, 0.55f);

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Map<String, Boolean> EXISTS = new HashMap<>();

    private Gfx() {
    }

    static ResourceLocation tex(String name) {
        return TEXTURES.computeIfAbsent(name, n -> ModCore.rl("textures/gui/puzzle/" + n + ".png"));
    }

    /** True if the sprite file exists; used to fall back to a placeholder for unknown types. */
    static boolean exists(String name) {
        return EXISTS.computeIfAbsent(name,
                n -> Minecraft.getInstance().getResourceManager().getResource(tex(n)).isPresent());
    }

    static String orElse(String name, String fallback) {
        return exists(name) ? name : fallback;
    }

    /** Draws a sprite centred on (cx, cy), scaled to w x h, turned by rot degrees and tinted with an ARGB colour. */
    static void sprite(GuiGraphics g, String name, double cx, double cy, double w, double h, int texW, int texH,
                       float rot, int argb) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        if (rot != 0) {
            pose.mulPose(Axis.ZP.rotationDegrees(rot));
        }
        pose.scale((float) (w / texW), (float) (h / texH), 1f);
        pose.translate(-texW / 2.0, -texH / 2.0, 0);
        g.setColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f,
                ((argb >>> 24) & 255) / 255f);
        g.blit(tex(name), 0, 0, 0, 0, texW, texH, texW, texH);
        g.setColor(1f, 1f, 1f, 1f);
        pose.popPose();
    }

    /** Draws a sprite stretched from (x0, y0) to (x1, y1) with the given thickness. */
    static void segment(GuiGraphics g, String name, double x0, double y0, double x1, double y1, double thickness,
                        int texW, int texH, int argb) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        if (len < 0.01) {
            return;
        }
        float angle = (float) Math.toDegrees(Math.atan2(y1 - y0, x1 - x0));
        sprite(g, name, (x0 + x1) / 2, (y0 + y1) / 2, len, thickness, texW, texH, angle, argb);
    }

    static void line(GuiGraphics g, double x0, double y0, double x1, double y1, double thickness, int argb) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        if (len < 0.01) {
            return;
        }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(Math.atan2(y1 - y0, x1 - x0))));
        pose.scale((float) len, (float) thickness, 1f);
        pose.translate(0, -0.5, 0);
        g.fill(0, 0, 1, 1, argb);
        pose.popPose();
    }

    /** 16x16 icon with its top left corner at (x, y). */
    static void icon(GuiGraphics g, String name, int x, int y) {
        g.blit(tex(name), x, y, 0, 0, 16, 16, 16, 16);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

    /** A small gem: a diamond of radius r with a one pixel core. */
    static void diamond(GuiGraphics g, int cx, int cy, int r, int argb, int core) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, argb);
        }
        g.fill(cx, cy, cx + 1, cy + 1, core);
    }

    /** An ornate panel: double frame, gold corner brackets with a gem, gems in the middle of long edges. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        frame(g, x, y, w, h, INK);
        frame(g, x + 1, y + 1, w - 2, h - 2, BORDER);
        if (w < 24 || h < 24) {
            g.fill(x + 1, y + 1, x + 2, y + 2, GOLD);
            g.fill(x + w - 2, y + 1, x + w - 1, y + 2, GOLD);
            g.fill(x + 1, y + h - 2, x + 2, y + h - 1, GOLD);
            g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, GOLD);
            return;
        }
        frame(g, x + 2, y + 2, w - 4, h - 4, PANEL_DARK);
        frame(g, x + 3, y + 3, w - 6, h - 6, BORDER_DIM);
        for (int c = 0; c < 4; c++) {
            int sx = (c & 1) == 0 ? 1 : -1, sy = c < 2 ? 1 : -1;
            int cx = sx > 0 ? x : x + w - 1, cy = sy > 0 ? y : y + h - 1;
            for (int i = 0; i < 8; i++) {
                px(g, cx + sx * i, cy + sy, GOLD);
                px(g, cx + sx, cy + sy * i, GOLD);
            }
            for (int i = 2; i < 6; i++) {
                px(g, cx + sx * i, cy + sy * 3, GOLD_DARK);
                px(g, cx + sx * 3, cy + sy * i, GOLD_DARK);
            }
            diamond(g, cx + sx * 2, cy + sy * 2, 2, GOLD, CYAN);
            px(g, cx + sx * 8, cy + sy, BORDER_BRIGHT);
            px(g, cx + sx, cy + sy * 8, BORDER_BRIGHT);
            px(g, cx + sx * 5, cy + sy * 5, GOLD_DARK);
        }
        if (w >= 90) {
            for (int yy : new int[]{y + 1, y + h - 2}) {
                diamond(g, x + w / 2, yy, 2, GOLD, CYAN);
                for (int i = 4; i < 10; i += 2) {
                    px(g, x + w / 2 - i, yy, GOLD_DARK);
                    px(g, x + w / 2 + i, yy, GOLD_DARK);
                }
            }
        }
        if (h >= 90) {
            for (int xx : new int[]{x + 1, x + w - 2}) {
                diamond(g, xx, y + h / 2, 2, GOLD, CYAN);
                for (int i = 4; i < 10; i += 2) {
                    px(g, xx, y + h / 2 - i, GOLD_DARK);
                    px(g, xx, y + h / 2 + i, GOLD_DARK);
                }
            }
        }
    }

    private static void px(GuiGraphics g, int x, int y, int argb) {
        g.fill(x, y, x + 1, y + 1, argb);
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }
}
