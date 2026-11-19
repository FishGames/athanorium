package org.example.resourcemod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.example.resourcemod.ModCore;
import org.example.resourcemod.base.network.Net;
import org.example.resourcemod.block.DeveloperMenu;
import org.example.resourcemod.core.edit.Edit;
import org.example.resourcemod.core.edit.EditHistory;
import org.example.resourcemod.core.hex.Hex;
import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.molecule.Molecule;
import org.example.resourcemod.core.part.InstructionType;
import org.example.resourcemod.core.part.PartType;
import org.example.resourcemod.core.program.Part;
import org.example.resourcemod.core.program.Program;
import org.example.resourcemod.core.program.ProgramError;
import org.example.resourcemod.core.program.ProgramValidator;
import org.example.resourcemod.core.puzzle.Puzzle;
import org.example.resourcemod.core.score.PartValue;
import org.example.resourcemod.core.score.ScoreMetric;
import org.example.resourcemod.core.score.Scoring;
import org.example.resourcemod.core.score.YieldFormula;
import org.example.resourcemod.core.sim.SimError;
import org.example.resourcemod.core.sim.Simulation;
import org.example.resourcemod.core.slot.Library;
import org.example.resourcemod.core.slot.ProgramSlot;
import org.example.resourcemod.core.slot.SlotChecker;
import org.example.resourcemod.core.slot.SlotContent;
import org.example.resourcemod.core.slot.Validity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The puzzle GUI: full screen, with the hex field in the middle, the parts on the left and the controls,
 * the program tapes and the instructions at the bottom.
 * <p>
 * The client only shows and animates; it sends program data, the server decides. Every change to the program
 * is one named editing step ({@link Edit}), which gives undo and redo. All texts come from the language file.
 */
public class PuzzleScreen extends AbstractContainerScreen<DeveloperMenu> {
    private static final double SQRT3 = Math.sqrt(3.0);
    private static final int[] ZOOMS = {6, 8, 10, 12, 16, 20, 24, 32};
    private static final String[] SPEED_LABELS = {"1x", "2x", "4x", "8x"};
    private static final long INTERIM_DELAY_MS = 3000;

    /** Keyboard rows; the instruction block is laid out like the keys that trigger the instructions. */
    private static final String[] KEY_ROWS = {"QWERT", "ASDFG", "ZXCVB"};

    /** Slot (row * 5 + column) of an instruction on the keyboard-shaped block, or -1 when it has no key. */
    private static int keySlot(Id id) {
        Character key = id.isDefaultNamespace() ? HOTKEYS.get(id.path()) : null;
        if (key == null) {
            return -1;
        }
        for (int r = 0; r < KEY_ROWS.length; r++) {
            int c = KEY_ROWS[r].indexOf(key);
            if (c >= 0) {
                return r * 5 + c;
            }
        }
        return -1;
    }

    /** Instruction hotkeys, shown on the instruction buttons. */
    private static final Map<String, Character> HOTKEYS = new LinkedHashMap<>();

    static {
        HOTKEYS.put("grab", 'R');
        HOTKEYS.put("drop", 'F');
        HOTKEYS.put("rotate_ccw", 'A');
        HOTKEYS.put("rotate_cw", 'D');
        HOTKEYS.put("pivot_ccw", 'Q');
        HOTKEYS.put("pivot_cw", 'E');
        HOTKEYS.put("extend", 'W');
        HOTKEYS.put("retract", 'S');
        HOTKEYS.put("track_forward", 'T');
        HOTKEYS.put("track_back", 'G');
        HOTKEYS.put("wait", 'Z');
        HOTKEYS.put("repeat", 'C');
        HOTKEYS.put("reset", 'X');
    }

    private enum Focus { FIELD, TAPE }

    /** A clickable area of the current frame. */
    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record DialogButton(String label, Runnable action) {
    }

    private record Dialog(String title, List<String> lines, List<DialogButton> buttons) {
    }

    // layout (set in init)
    private int W, H;
    private int pixelScale = 1;
    private double mouseFactor = 1.0;
    private int topH = 0;
    private int leftW = 124;
    private int tapeLeft = 132;
    private int ctlH = 26;
    private int tapeHeadH = 9;
    private int cell = 16;
    private int instrCols = 5;
    private int hintH = 12;
    private int tapeRows = 3;
    private int bottomH;
    private int fx0, fy0, fx1, fy1;
    private int tapeY0;
    private static final int TAPE_LABEL_W = 40;

    // data
    private boolean loaded;
    private SlotContent content = SlotContent.EMPTY;
    private SlotContent savedContent = SlotContent.EMPTY;
    private SlotContent lastSent = SlotContent.EMPTY;
    private SlotContent acked = SlotContent.EMPTY;
    private int seq;
    private int lastSentSeq;
    private long lastEditTime;
    private SlotStatus status;
    private final EditHistory history = new EditHistory();
    private List<ProgramError> errors = List.of();
    private long previewInstructions = -1;
    private Map<Id, Long> solvedMetrics;
    private int libraryGeneration = -1;

    // editing state
    private Focus focus = Focus.FIELD;
    private int selectedPart = -1;
    private Part held;
    private int dragPart = -1;
    private Hex dragOffset = Hex.ZERO;
    private boolean dragMoved;
    private List<Hex> trackDraft;
    private int trackExtendPart = -1;
    private boolean trackExtendFront;
    private boolean panning;
    private int tapeSlot;
    private int tapeScroll;
    private int tapeRowScroll;
    private int paletteScroll;
    private int instrDragSlot = -1;
    private int instrDragPart = -1;
    private Hex hoverHex;

    // view
    private double panX, panY;
    private int zoom = 4;
    private boolean fitted;

    // run
    private SimRunner run;
    private boolean runReported;
    private int speed;
    private long lastFrame;

    // messages and overlays
    private String message = "";
    private int messageKind;
    private Hex errorPos;
    private Dialog dialog;
    private boolean puzzleList;
    private String search = "";
    private String namespaceFilter;
    private Id metricFilter;
    private int listScroll;
    private final List<Hit> hits = new ArrayList<>();
    private String tooltip;
    private String tooltipShown;
    private long tooltipSince;
    private static final long TOOLTIP_DELAY_MS = 450;

    public PuzzleScreen(DeveloperMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    // messages from the server

    /** Full data of the slot: sent when the GUI opens and after a discard. */
    public void onOpenData(ProgramSlot slot, SlotStatus newStatus) {
        boolean first = !loaded;
        stopRun();
        content = slot.current().content();
        savedContent = slot.saved().content();
        lastSent = content;
        acked = content;
        status = newStatus;
        history.clear();
        selectedPart = -1;
        held = null;
        loaded = true;
        fitted = false;
        onContentChanged(false);
        if (first && slot.pending() != null) {
            // an unsaved change was left behind, for example because the server closed the menu itself
            dialog = new Dialog(tr("gui.resourcemod.pending.title"), List.of(tr("gui.resourcemod.pending.text")),
                    List.of(new DialogButton(tr("gui.resourcemod.pending.continue"), () -> dialog = null),
                            new DialogButton(tr("gui.resourcemod.save"), () -> {
                                dialog = null;
                                requestSave();
                            }),
                            new DialogButton(tr("gui.resourcemod.discard"), () -> {
                                dialog = null;
                                doDiscard();
                            })));
        }
    }

    public void onStatus(SlotStatus newStatus) {
        status = newStatus;
        if (newStatus.seq() != 0 && newStatus.seq() == lastSentSeq) {
            acked = lastSent;
        }
        if (newStatus.messageKind() != 0) {
            setMessage(newStatus.messageKind(), coreText(newStatus.messageKey(), newStatus.messageDetail()), null);
        }
    }

    /** Datapacks were reloaded: the program stays, it is checked against the new puzzle like while building. */
    public void onLibraryChanged() {
        if (loaded) {
            stopRun();
            onContentChanged(false);
            setMessage(1, tr("gui.resourcemod.reloaded"), null);
        }
    }

    // helpers: data

    private Library library() {
        return ClientData.library();
    }

    private Puzzle puzzle() {
        return library().puzzle(content.puzzle());
    }

    private HexField field() {
        return SlotChecker.field(content, ClientData.stages());
    }

    private boolean canEdit() {
        return loaded && run == null && dialog == null && !puzzleList && puzzle() != null;
    }

    private boolean dirty() {
        return !content.equals(lastSent);
    }

    /** True if the server has confirmed exactly the content shown. */
    private boolean currentConfirmed() {
        return status != null && content.equals(acked) && status.current().confirmed();
    }

    /** True if the saved program is confirmed and the content shown differs from it: an unsaved change exists. */
    private boolean pendingExists() {
        return status != null && status.saved().confirmed() && !content.equals(savedContent);
    }

    private void applyEdit(Edit edit) {
        content = history.apply(content, edit);
        onContentChanged(true);
    }

    private void onContentChanged(boolean edited) {
        if (edited) {
            lastEditTime = Util.getMillis();
        }
        solvedMetrics = null;
        errorPos = null;
        Puzzle p = puzzle();
        libraryGeneration = library().generation();
        previewInstructions = -1;
        if (p == null) {
            errors = List.of();
        } else {
            errors = ProgramValidator.validate(content.program(), p, field(), ModCore.REG, library().values());
            if (errors.isEmpty()) {
                try {
                    previewInstructions = new Simulation(p, content.program(), field(), ModCore.REG).instructionCount();
                } catch (RuntimeException e) {
                    previewInstructions = -1;
                }
            }
        }
        int n = content.program().parts().size();
        if (selectedPart >= n) {
            selectedPart = -1;
        }
        if (edited) {
            message = "";
            messageKind = 0;
        }
    }

    private void setMessage(int kind, String text, Hex position) {
        messageKind = kind;
        message = text;
        errorPos = position;
    }

    private List<Integer> armParts() {
        List<Integer> out = new ArrayList<>();
        List<Part> parts = content.program().parts();
        for (int i = 0; i < parts.size(); i++) {
            PartType pt = ModCore.REG.parts.get(parts.get(i).type());
            if (pt != null && pt.kind() == PartType.Kind.ARM) {
                out.add(i);
            }
        }
        return out;
    }

    private Part part(int index) {
        return index >= 0 && index < content.program().parts().size() ? content.program().parts().get(index) : null;
    }

    private PartType typeOf(Part p) {
        return p == null ? null : ModCore.REG.parts.get(p.type());
    }

    private int partAt(Hex h) {
        List<Part> parts = content.program().parts();
        Puzzle p = puzzle();
        int found = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (PartShapes.hexes(parts.get(i), p, ModCore.REG).contains(h)) {
                PartType pt = typeOf(parts.get(i));
                if (pt != null && pt.kind() == PartType.Kind.ARM) {
                    return i; // an arm standing on a track is picked first
                }
                found = i;
            }
        }
        return found;
    }

    // helpers: texts

    private static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    /** Text for a message key of the core ("sim.error.collision", "program.error.overlap", ...). */
    private static String coreText(String key, String detail) {
        String full = ModCore.MOD_ID + "." + key;
        if (I18n.exists(full)) {
            return I18n.get(full, detail == null ? "" : detail).trim();
        }
        return detail == null || detail.isEmpty() ? key : key + ": " + detail;
    }

    /** Display name of a registered thing, e.g. kind "part", id resourcemod:arm gives key part.resourcemod.arm. */
    private static String nameOf(String kind, Id id) {
        String key = kind + "." + id.namespace() + "." + id.path().replace('/', '.');
        return I18n.exists(key) ? I18n.get(key) : id.toFileString();
    }

    private static String puzzleName(Id id, Puzzle p) {
        String key = p != null && p.name() != null ? p.name()
                : "puzzle." + id.namespace() + "." + id.path().replace('/', '.');
        if (I18n.exists(key)) {
            return I18n.get(key);
        }
        return p != null && p.nameFallback() != null ? p.nameFallback() : id.toFileString();
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(".."))) + "..";
    }

    // layout

    @Override
    protected void init() {
        imageWidth = width;
        imageHeight = height;
        super.init();
        // Own scale, independent of Minecraft's GUI scale: the largest whole pixel scale that still leaves at
        // least 520 x 300 units, so the GUI fills the screen and stays readable on every resolution.
        var window = minecraft.getWindow();
        pixelScale = 1;
        while ((pixelScale + 1) * 520 <= window.getWidth() && (pixelScale + 1) * 300 <= window.getHeight()) {
            pixelScale++;
        }
        W = window.getWidth() / pixelScale;
        H = window.getHeight() / pixelScale;
        mouseFactor = window.getGuiScale() / pixelScale;
        tapeRows = Math.max(5, Math.min(9, 5 + (H - 330) / (2 * cell)));
        bottomH = ctlH + tapeHeadH + tapeRows * cell + hintH + 8;
        fy1 = H - bottomH;
        // instruction block left of the tapes: as many columns as needed to show every registered instruction
        int instrRows = Math.max(1, (tapeHeadH + tapeRows * cell - 13) / 25);
        int extras = 0;
        for (InstructionType it : ModCore.REG.instructions.values()) {
            if (keySlot(it.id()) < 0) {
                extras++;
            }
        }
        // three keyboard rows (QWERT / ASDFG / ZXCVB) and below them, in as many columns as needed, instructions without a key
        instrCols = Math.max(5, instrRows > 3 ? (extras + instrRows - 4) / (instrRows - 3) : 5);
        tapeLeft = instrCols * 24 + 12;
        fx0 = leftW;
        fx1 = W;
        fy0 = topH;
        tapeY0 = fy1 + ctlH + tapeHeadH + 2;
        fitted = false;
    }

    private void fitView() {
        fitted = true;
        panX = 0;
        panY = 0;
        HexField f = field();
        if (!f.isBounded()) {
            zoom = 3;
            return;
        }
        int rings = f.radius() + 1; // including the wall
        zoom = 0;
        for (int i = ZOOMS.length - 1; i >= 0; i--) {
            double s = ZOOMS[i];
            double w = SQRT3 * s * (2 * rings + 1) + 8;
            double h = s * (3 * rings + 2) + 8;
            if (w <= fx1 - fx0 && h <= fy1 - fy0) {
                zoom = i;
                break;
            }
        }
    }

    private double size() {
        return ZOOMS[zoom];
    }

    private double px(double q, double r) {
        return (fx0 + fx1) / 2.0 + panX + size() * SQRT3 * (q + r / 2.0);
    }

    private double py(double r) {
        return (fy0 + fy1) / 2.0 + panY + size() * 1.5 * r;
    }

    private double px(Hex h) {
        return px(h.q(), h.r());
    }

    private double py(Hex h) {
        return py(h.r());
    }

    private Hex pick(double mx, double my) {
        double s = size();
        double r = (my - (fy0 + fy1) / 2.0 - panY) / (1.5 * s);
        double q = (mx - (fx0 + fx1) / 2.0 - panX) / (SQRT3 * s) - r / 2.0;
        double x = q, z = r, y = -x - z;
        long rx = Math.round(x), ry = Math.round(y), rz = Math.round(z);
        double dx = Math.abs(rx - x), dy = Math.abs(ry - y), dz = Math.abs(rz - z);
        if (dx > dy && dx > dz) {
            rx = -ry - rz;
        } else if (dy <= dz) {
            rz = -rx - ry;
        }
        return new Hex((int) rx, (int) rz);
    }

    private boolean inField(double mx, double my) {
        return mx >= fx0 && mx < fx1 && my >= fy0 && my < fy1;
    }

    private boolean inTape(double mx, double my) {
        return my >= tapeY0 && my < tapeY0 + tapeRows * cell && mx >= tapeLeft + 4 + TAPE_LABEL_W && mx < W - 4;
    }

    // rendering

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // everything is drawn in render
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // no inventory labels
    }

    @Override
    public void render(GuiGraphics g, int realMouseX, int realMouseY, float partialTick) {
        RenderSystem.enableBlend();
        int mouseX = (int) (realMouseX * mouseFactor), mouseY = (int) (realMouseY * mouseFactor);
        g.pose().pushPose();
        g.pose().scale((float) (1.0 / mouseFactor), (float) (1.0 / mouseFactor), 1f);
        long now = Util.getMillis();
        double dt = lastFrame == 0 ? 0 : Math.min(0.25, (now - lastFrame) / 1000.0);
        lastFrame = now;
        if (loaded && !fitted) {
            fitView();
        }
        if (loaded && libraryGeneration != library().generation()) {
            onContentChanged(false);
        }
        if (run != null) {
            run.advance(dt, speed);
            checkRunFinished();
        }
        hits.clear();
        tooltip = null;
        hoverHex = inField(mouseX, mouseY) && dialog == null && !puzzleList ? pick(mouseX, mouseY) : null;

        g.fill(0, 0, W, H, Gfx.BG);
        drawField(g, mouseX, mouseY);
        drawTitle(g, mouseX, mouseY);
        drawParts(g, mouseX, mouseY);
        drawBottom(g, mouseX, mouseY);
        drawSelectionBar(g, mouseX, mouseY);
        if (!loaded) {
            centeredBox(g, tr("gui.resourcemod.loading"));
        }
        if (run != null && run.inBackground()) {
            hits.clear();
            int w = 220, h = 54, x = (W - w) / 2, y = (H - h) / 2;
            Gfx.panel(g, x, y, w, h);
            g.drawString(font, tr("gui.resourcemod.fast.title"), x + 8, y + 7, Gfx.GOLD, false);
            int cycle = run.backgroundCycle();
            g.drawString(font, tr("gui.resourcemod.fast.progress", cycle, run.maxCycles()), x + 8, y + 19, Gfx.TEXT, false);
            g.fill(x + 8, y + 31, x + w - 8, y + 35, Gfx.PANEL_DARK);
            g.fill(x + 8, y + 31, x + 8 + (int) ((w - 16) * Math.min(1.0, cycle / (double) run.maxCycles())), y + 35, Gfx.CYAN);
            textButton(g, x + w - 68, y + 38, 60, 13, tr("gui.resourcemod.cancel"), true, false, null, mouseX, mouseY,
                    () -> run.cancelBackground());
        }
        if (puzzleList) {
            drawPuzzleList(g, mouseX, mouseY);
        }
        if (dialog != null) {
            drawDialog(g, mouseX, mouseY);
        }
        // explanations only appear after the pointer rested on an element for a moment
        long tipNow = System.currentTimeMillis();
        if (tooltip == null || !tooltip.equals(tooltipShown)) {
            tooltipShown = tooltip;
            tooltipSince = tipNow;
        }
        if (tooltip != null && tipNow - tooltipSince >= TOOLTIP_DELAY_MS) {
            int tw = Math.min(font.width(tooltip), 220);
            List<FormattedCharSequence> lines = font.split(Component.literal(tooltip), tw);
            int th = lines.size() * 9 + 5;
            int tx = Math.max(2, Math.min(mouseX + 10, W - tw - 10)), ty = Math.max(2, Math.min(mouseY - th - 4, H - th - 2));
            g.pose().translate(0, 0, 300);
            g.fill(tx, ty, tx + tw + 8, ty + th, 0xF0100A1C);
            Gfx.frame(g, tx, ty, tw + 8, th, Gfx.GOLD);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(font, lines.get(i), tx + 4, ty + 3 + i * 9, Gfx.TEXT, false);
            }
        }
        g.pose().popPose();
    }

    /** Clips drawing to a rectangle given in the GUI's own units (the pose scale does not affect scissors). */
    private void clip(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.flush();
        int fbH = minecraft.getWindow().getHeight();
        RenderSystem.enableScissor(x0 * pixelScale, fbH - y1 * pixelScale, Math.max(0, (x1 - x0) * pixelScale),
                Math.max(0, (y1 - y0) * pixelScale));
    }

    private void unclip(GuiGraphics g) {
        g.flush();
        RenderSystem.disableScissor();
    }

    private void centeredBox(GuiGraphics g, String text) {
        int w = font.width(text) + 20;
        Gfx.panel(g, (W - w) / 2, H / 2 - 12, w, 24);
        g.drawString(font, text, (W - w) / 2 + 10, H / 2 - 4, Gfx.TEXT, false);
    }

    // buttons

    private boolean hover(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void buttonBase(GuiGraphics g, int x, int y, int w, int h, boolean enabled, boolean active, boolean hov) {
        g.fill(x, y, x + w, y + h, active ? Gfx.ACTIVE : hov && enabled ? Gfx.PANEL_LIGHT : Gfx.PANEL_DARK);
        Gfx.frame(g, x, y, w, h, active ? Gfx.GOLD : hov && enabled ? Gfx.BORDER_BRIGHT : Gfx.BORDER);
    }

    private void iconButton(GuiGraphics g, int x, int y, String icon, boolean enabled, boolean active, String tip,
                            int mx, int my, Runnable action) {
        boolean hov = hover(x, y, 18, 18, mx, my);
        buttonBase(g, x, y, 18, 18, enabled, active, hov);
        if (!enabled) {
            g.setColor(1f, 1f, 1f, 0.3f);
        }
        Gfx.icon(g, icon, x + 1, y + 1);
        g.setColor(1f, 1f, 1f, 1f);
        if (hov && tip != null) {
            tooltip = tip;
        }
        if (enabled) {
            hits.add(new Hit(x, y, 18, 18, action));
        }
    }

    private void textButton(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled, boolean active,
                            String tip, int mx, int my, Runnable action) {
        boolean hov = hover(x, y, w, h, mx, my);
        buttonBase(g, x, y, w, h, enabled, active, hov);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, enabled ? Gfx.TEXT : Gfx.TEXT_DIM, false);
        if (hov && tip != null) {
            tooltip = tip;
        }
        if (enabled) {
            hits.add(new Hit(x, y, w, h, action));
        }
    }

    // top bar

    /** The puzzle name in an ornate cartouche hanging from the upper field edge: list button, name, stage and status. */
    private void drawTitle(GuiGraphics g, int mx, int my) {
        Puzzle p = puzzle();
        String name = content.puzzle() == null ? tr("gui.resourcemod.no_puzzle")
                : p == null ? tr("gui.resourcemod.puzzle_missing", content.puzzle().toFileString())
                : puzzleName(content.puzzle(), p);
        boolean showStage = p != null;
        String stage = "";
        if (showStage) {
            HexField f = field();
            stage = tr("gui.resourcemod.stage", content.stage(),
                    f.isBounded() ? tr("gui.resourcemod.radius", f.radius()) : tr("gui.resourcemod.unbounded"));
        }
        String badge = statusText();
        int stageW = showStage ? 13 + font.width(stage) + 8 + 13 : 0;
        int badgeW = font.width(badge) + 10;
        int fixed = 30 + (showStage ? stageW + 8 : 0) + 8 + badgeW + 6;
        int maxW = fx1 - fx0 - 20;
        int nameW = Math.max(40, Math.min(font.width(name), maxW - fixed));
        int w = Math.min(maxW, fixed + nameW);
        int x = (fx0 + fx1 - w) / 2, y = fy0;
        g.fill(x, y, x + w, y + 24, Gfx.INK);
        g.fill(x + 1, y, x + w - 1, y + 23, Gfx.PANEL_DARK);
        Gfx.frame(g, x + 1, y + 1, w - 2, 22, Gfx.GOLD);
        Gfx.frame(g, x + 3, y + 3, w - 6, 18, Gfx.GOLD_DARK);
        // small pendant below the middle
        for (int i = 0; i < 4; i++) {
            g.fill(x + w / 2 - 4 + i, y + 24 + i, x + w / 2 + 4 - i, y + 25 + i, i == 3 ? Gfx.GOLD : Gfx.PANEL_DARK);
            g.fill(x + w / 2 - 4 + i, y + 24 + i, x + w / 2 - 3 + i, y + 25 + i, Gfx.GOLD);
            g.fill(x + w / 2 + 3 - i, y + 24 + i, x + w / 2 + 4 - i, y + 25 + i, Gfx.GOLD);
        }
        iconButton(g, x + 5, y + 3, "ctl_puzzles", loaded && run == null, false, tr("gui.resourcemod.puzzles.tip"), mx, my,
                () -> {
                    puzzleList = true;
                    listScroll = 0;
                });
        int cx = x + 27;
        String shown = fit(name, nameW);
        g.drawString(font, shown, cx, y + 8, p == null && content.puzzle() != null ? Gfx.BAD : Gfx.GOLD, false);
        cx += nameW + 4;
        if (showStage) {
            Gfx.diamond(g, cx + 1, y + 12, 1, Gfx.GOLD, Gfx.GOLD);
            cx += 6;
            boolean edit = canEdit();
            textButton(g, cx, y + 5, 13, 14, "<", edit && content.stage() > 1, false, tr("gui.resourcemod.stage.down"), mx, my,
                    () -> changeStage(-1));
            g.drawString(font, stage, cx + 17, y + 8, Gfx.TEXT, false);
            cx += 13 + font.width(stage) + 8;
            textButton(g, cx, y + 5, 13, 14, ">", edit && content.stage() < ClientData.stages(), false,
                    tr("gui.resourcemod.stage.up"), mx, my, () -> changeStage(1));
            cx += 17;
        }
        Gfx.diamond(g, cx + 1, y + 12, 1, Gfx.GOLD, Gfx.GOLD);
        cx += 6;
        g.fill(cx, y + 5, cx + badgeW, y + 19, Gfx.PANEL);
        Gfx.frame(g, cx, y + 5, badgeW, 14, statusColor());
        g.drawString(font, badge, cx + 5, y + 8, statusColor(), false);
        if (hover(cx, y + 5, badgeW, 14, mx, my)) {
            tooltip = statusTip();
        }
    }

    /** A small header plate: dark body, gold frame, a gem at each end and the centred title. */
    private void plate(GuiGraphics g, int x, int y, int w, String title) {
        g.fill(x, y, x + w, y + 11, Gfx.PANEL_DARK);
        Gfx.frame(g, x, y, w, 11, Gfx.GOLD_DARK);
        g.fill(x, y, x + 1, y + 1, Gfx.PANEL);
        g.fill(x + w - 1, y, x + w, y + 1, Gfx.PANEL);
        g.fill(x, y + 10, x + 1, y + 11, Gfx.PANEL);
        g.fill(x + w - 1, y + 10, x + w, y + 11, Gfx.PANEL);
        Gfx.diamond(g, x + 5, y + 5, 2, Gfx.GOLD, Gfx.CYAN);
        Gfx.diamond(g, x + w - 6, y + 5, 2, Gfx.GOLD, Gfx.CYAN);
        String t = fit(title, w - 20);
        g.drawString(font, t, x + (w - font.width(t)) / 2, y + 2, Gfx.GOLD, false);
    }

    private String statusText() {
        if (!loaded || status == null) {
            return "...";
        }
        if (content.puzzle() == null) {
            return tr("gui.resourcemod.status.no_puzzle");
        }
        String s;
        if (!content.equals(acked) || !status.current().confirmed()) {
            s = tr("gui.resourcemod.status.untested");
        } else {
            s = switch (status.current().validity()) {
                case VALID -> tr("gui.resourcemod.status.confirmed");
                case CHECKING -> tr("gui.resourcemod.status.checking");
                case INVALID -> tr("gui.resourcemod.status.invalid");
                default -> tr("gui.resourcemod.status.untested");
            };
        }
        return pendingExists() ? s + " *" : s;
    }

    private int statusColor() {
        if (status == null || content.puzzle() == null || !content.equals(acked) || !status.current().confirmed()) {
            return Gfx.TEXT_DIM;
        }
        return switch (status.current().validity()) {
            case VALID -> Gfx.GOOD;
            case INVALID -> Gfx.BAD;
            default -> Gfx.GOLD;
        };
    }

    private String statusTip() {
        if (status == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(tr("gui.resourcemod.status.tip"));
        if (pendingExists()) {
            sb.append(' ').append(tr("gui.resourcemod.status.tip.pending"));
        }
        if (currentConfirmed() && status.current().validity() == Validity.INVALID) {
            sb.append(' ').append(coreText(status.current().reasonKey(), status.current().reasonDetail()));
        }
        return sb.toString();
    }

    // left: parts

    private record PaletteEntry(String icon, String label, String value, Part part, int group, Molecule molecule, boolean placed) {
    }

    private List<PaletteEntry> paletteEntries() {
        List<PaletteEntry> out = new ArrayList<>();
        Puzzle p = puzzle();
        if (p == null) {
            return out;
        }
        boolean[] used = new boolean[p.inputs().size()];
        boolean outputUsed = false;
        for (Part part : content.program().parts()) {
            PartType pt = typeOf(part);
            if (pt == null) {
                continue;
            }
            if (pt.kind() == PartType.Kind.INPUT && part.inputIndex() >= 0 && part.inputIndex() < used.length) {
                used[part.inputIndex()] = true;
            }
            if (pt.kind() == PartType.Kind.OUTPUT) {
                outputUsed = true;
            }
        }
        for (int i = 0; i < used.length; i++) {
            Molecule m = p.inputs().get(i);
            String what = m.atoms().size() == 1 ? nameOf("element", m.atoms().get(0).element())
                    : tr("gui.resourcemod.reagent", m.atoms().size());
            out.add(new PaletteEntry("part_input", tr("gui.resourcemod.input", i + 1, what), "",
                    Part.input(PartType.INPUT, Hex.ZERO, 0, i), 0, m, used[i]));
        }
        out.add(new PaletteEntry("part_output", tr("gui.resourcemod.output"), "", Part.output(PartType.OUTPUT, Hex.ZERO, 0), 0,
                p.product(), outputUsed));
        for (PartType pt : ModCore.REG.parts.values()) {
            if (!pt.purchasable() || p.forbiddenPartsOrEmpty().contains(pt.id())) {
                continue;
            }
            PartValue v = library().values().get(pt.id());
            if (v == null) {
                continue; // no part value file: not available
            }
            Part part = switch (pt.kind()) {
                case ARM -> Part.arm(pt.id(), Hex.ZERO, 0, 1, Map.of());
                case TRACK -> Part.track(pt.id(), Hex.ZERO, List.of(Hex.ZERO));
                default -> Part.glyph(pt.id(), Hex.ZERO, 0);
            };
            String icon = Gfx.orElse("part_" + pt.id().path(), "instr_unknown");
            out.add(new PaletteEntry(icon, nameOf("part", pt.id()), v.value() + (v.perHex() ? "/" : ""), part,
                    pt.kind() == PartType.Kind.ARM || pt.kind() == PartType.Kind.TRACK ? 1 : 2, null, false));
        }
        out.sort((a, b) -> Integer.compare(a.group, b.group)); // stable: keeps the registry order inside a group
        return out;
    }

    private static final String[] GROUP_KEYS = {"inputs", "arms", "glyphs"};

    /** Row heights of the parts list: group header, input/output drawn as molecule, ordinary part. */
    private int paletteRowH(PaletteEntry e) {
        return roomyPalette ? (e == null ? 14 : e.molecule != null ? 30 : 16) : (e == null ? 12 : e.molecule != null ? 25 : 13);
    }

    /** True while the whole list fits with the larger rows. */
    private boolean roomyPalette;

    private void drawParts(GuiGraphics g, int mx, int my) {
        int y0 = 0, h = fy1;
        Gfx.panel(g, 0, y0, leftW, h);
        // rows: a null entry is the header of the group that follows
        List<PaletteEntry> rows = new ArrayList<>();
        List<Integer> groups = new ArrayList<>();
        int last = -1;
        for (PaletteEntry e : paletteEntries()) {
            if (e.group != last) {
                rows.add(null);
                groups.add(e.group);
                last = e.group;
            }
            rows.add(e);
            groups.add(e.group);
        }
        int top = y0 + 7, bottom = y0 + h - 6;
        roomyPalette = true;
        int roomy = 0;
        for (PaletteEntry e : rows) {
            roomy += paletteRowH(e);
        }
        roomyPalette = roomy <= bottom - top;
        int maxScroll = rows.size(), acc = 0;
        while (maxScroll > 0 && acc + paletteRowH(rows.get(maxScroll - 1)) <= bottom - top) {
            acc += paletteRowH(rows.get(maxScroll - 1));
            maxScroll--;
        }
        paletteScroll = Math.max(0, Math.min(paletteScroll, maxScroll));
        boolean edit = canEdit();
        int y = top;
        for (int i = paletteScroll; i < rows.size(); i++) {
            PaletteEntry e = rows.get(i);
            int rowH = paletteRowH(e);
            if (y + rowH > bottom) {
                break;
            }
            if (e == null) {
                plate(g, 7, y, leftW - 14, tr("gui.resourcemod.group." + GROUP_KEYS[groups.get(i)]));
                y += rowH;
                continue;
            }
            boolean usable = edit && !e.placed;
            boolean hov = hover(6, y, leftW - 12, rowH, mx, my);
            boolean active = held != null && held.type().equals(e.part.type()) && held.inputIndex() == e.part.inputIndex();
            if (active || hov && usable) {
                g.fill(6, y, leftW - 6, y + rowH, active ? Gfx.ACTIVE : Gfx.PANEL_LIGHT);
            }
            if (active) {
                Gfx.frame(g, 6, y, leftW - 12, rowH, Gfx.GOLD);
            }
            if (!usable) {
                g.setColor(1f, 1f, 1f, 0.45f);
            }
            Gfx.icon(g, e.icon, 8, y + (rowH - 16) / 2);
            if (e.molecule != null) {
                // inputs and the output are shown as what they are: the molecule itself
                int mx0 = 27;
                PartType pt = typeOf(e.part);
                if (pt != null && pt.kind() == PartType.Kind.INPUT) {
                    g.drawString(font, Integer.toString(e.part.inputIndex() + 1), 27, y + (rowH - 8) / 2, Gfx.TEXT_DIM, false);
                    mx0 = 36;
                }
                clip(g, mx0, y + 1, leftW - 8, y + rowH - 1);
                drawMoleculeFitted(g, e.molecule, pt != null && pt.kind() == PartType.Kind.INPUT ? "io_input" : "io_output",
                        (mx0 + leftW - 8) / 2.0, y + rowH / 2.0, leftW - 10 - mx0, rowH - 2, roomyPalette ? 13 : 11,
                        e.placed ? 0x70FFFFFF : -1);
                unclip(g);
                g.setColor(1f, 1f, 1f, 1f);
            } else {
                g.setColor(1f, 1f, 1f, 1f);
                int valueW = e.value.isEmpty() ? 0 : font.width(e.value) + 3;
                g.drawString(font, fit(e.label, leftW - 36 - valueW), 27, y + (rowH - 7) / 2, usable ? Gfx.TEXT : Gfx.TEXT_DIM, false);
                if (!e.value.isEmpty()) {
                    g.drawString(font, e.value, leftW - 9 - font.width(e.value), y + (rowH - 7) / 2, Gfx.GOLD, false);
                }
            }
            if (hov) {
                tooltip = e.label + (e.value.isEmpty() ? "" : "  (" + tr("gui.resourcemod.value") + " " + e.value + ")")
                        + (e.placed ? "  " + tr("gui.resourcemod.already_placed") : "");
            }
            if (usable) {
                Part part = e.part;
                hits.add(new Hit(6, y, leftW - 12, rowH, () -> {
                    held = part;
                    selectedPart = -1;
                    focus = Focus.FIELD;
                }));
            }
            y += rowH;
        }
        if (maxScroll > 0) {
            String more = (paletteScroll > 0 ? "^" : "") + (paletteScroll < maxScroll ? "v" : "");
            g.drawString(font, more, leftW - 8 - font.width(more), y0 + h - 14, Gfx.GOLD, false);
        }
    }

    // right: goal, metrics, messages

    /** The instruction block at the bottom left, with its name plate; the tapes start right of it. */
    private void drawInstructions(GuiGraphics g, int mx, int my) {
        int bx0 = 4, by0 = fy1 + ctlH + 1;
        g.fill(tapeLeft - 1, by0, tapeLeft, tapeY0 + tapeRows * cell + 1, Gfx.BORDER);
        Gfx.diamond(g, tapeLeft - 1, (by0 + tapeY0 + tapeRows * cell) / 2, 1, Gfx.GOLD, Gfx.GOLD);
        plate(g, bx0, by0, tapeLeft - 12, tr("gui.resourcemod.instructions"));
        Puzzle p = puzzle();
        boolean edit = canEdit() && part(selectedPart) != null && typeOf(part(selectedPart)) != null
                && typeOf(part(selectedPart)).kind() == PartType.Kind.ARM;
        if (p == null) {
            return;
        }
        int extra = 0;
        for (InstructionType it : ModCore.REG.instructions.values()) {
            int slot = keySlot(it.id());
            int col, row;
            if (slot >= 0) {
                col = slot % 5;
                row = slot / 5;
            } else {
                col = extra % instrCols;
                row = 3 + extra / instrCols;
                extra++;
            }
            if (p.forbiddenInstructionsOrEmpty().contains(it.id())) {
                continue;
            }
            int ix = bx0 + 1 + col * 24, iy = by0 + 13 + row * 25;
            if (iy + 24 > tapeY0 + tapeRows * cell + 2) {
                continue;
            }
            boolean hov = hover(ix, iy, 22, 24, mx, my);
            buttonBase(g, ix, iy, 22, 24, edit, false, hov);
            if (!edit) {
                g.setColor(1f, 1f, 1f, 0.35f);
            }
            Gfx.icon(g, Gfx.orElse("instr_" + it.id().path(), "instr_unknown"), ix + 3, iy);
            g.setColor(1f, 1f, 1f, 1f);
            Character key = it.id().isDefaultNamespace() ? HOTKEYS.get(it.id().path()) : null;
            if (key != null) {
                String k = key.toString();
                g.drawString(font, k, ix + (22 - font.width(k)) / 2 + 1, iy + 15, edit ? Gfx.GOLD : Gfx.TEXT_DIM, false);
            }
            if (hov) {
                tooltip = nameOf("instruction", it.id()) + (key != null ? "  [" + key + "]" : "");
            }
            if (edit) {
                Id id = it.id();
                hits.add(new Hit(ix, iy, 22, 24, () -> setInstruction(id)));
            }
        }
    }

    private Map<Id, Long> knownMetrics() {
        return solvedMetrics != null ? solvedMetrics : currentConfirmed() ? status.current().metrics() : Map.of();
    }

    /** Live value of a metric while building and running, otherwise the value of the last test run. */
    private String metricValue(ScoreMetric m, Map<Id, Long> known) {
        if (m.id().equals(ScoreMetric.PART_VALUE)) {
            return Long.toString(Scoring.partValue(content.program(), library().values()));
        } else if (m.id().equals(ScoreMetric.CYCLES) && run != null) {
            return Integer.toString(run.inBackground() ? run.backgroundCycle() : run.cycle());
        } else if (m.id().equals(ScoreMetric.AREA) && run != null && !run.inBackground()) {
            return Long.toString(run.sim().area());
        } else if (m.id().equals(ScoreMetric.INSTRUCTIONS) && previewInstructions >= 0) {
            return Long.toString(previewInstructions);
        }
        return known.containsKey(m.id()) ? Long.toString(known.get(m.id())) : "-";
    }

    private static String shortMetricName(Id id) {
        String key = "metric." + id.namespace() + "." + id.path().replace('/', '.') + ".short";
        return I18n.exists(key) ? I18n.get(key) : nameOf("metric", id);
    }

    /** The metrics as compact chips, right aligned in the control row. Returns the left edge of the chips. */
    private int drawMetricChips(GuiGraphics g, int left, int right, int y, int mx, int my) {
        Puzzle p = puzzle();
        if (p == null) {
            return right;
        }
        Map<Id, Long> known = knownMetrics();
        List<String[]> chips = new ArrayList<>(); // full name, short name, value, "1" if scoring
        for (ScoreMetric m : ModCore.REG.metrics.values()) {
            chips.add(new String[]{nameOf("metric", m.id()), shortMetricName(m.id()), metricValue(m, known),
                    m.id().equals(p.metric()) ? "1" : ""});
        }
        chips.add(new String[]{tr("gui.resourcemod.reference"), tr("gui.resourcemod.reference.short"),
                trimNumber(p.reference()), "r"});
        int mode = 0;
        for (; mode < 2; mode++) {
            int total = 0;
            for (String[] c : chips) {
                total += font.width(c[mode]) + font.width(c[2]) + 12;
            }
            if (total <= right - left) {
                break;
            }
        }
        mode = Math.min(mode, 1);
        int x = right;
        for (int i = chips.size() - 1; i >= 0; i--) {
            String[] c = chips.get(i);
            int w = font.width(c[mode]) + font.width(c[2]) + 10;
            if (x - w < left) {
                break;
            }
            x -= w;
            boolean scoring = c[3].equals("1"), ref = c[3].equals("r");
            g.fill(x, y, x + w, y + 14, Gfx.PANEL_DARK);
            Gfx.frame(g, x, y, w, 14, scoring ? Gfx.GOLD : Gfx.BORDER);
            g.drawString(font, c[mode], x + 4, y + 3, scoring ? Gfx.GOLD : Gfx.TEXT_DIM, false);
            g.drawString(font, c[2], x + w - 4 - font.width(c[2]), y + 3, ref ? Gfx.TEXT_DIM : scoring ? Gfx.GOLD : Gfx.TEXT, false);
            if (hover(x, y, w, 14, mx, my)) {
                tooltip = c[0] + ": " + c[2] + (scoring ? "  (" + tr("gui.resourcemod.scoring") + ")" : "");
            }
            x -= 2;
        }
        return x;
    }

    private static String trimNumber(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : String.format(Locale.ROOT, "%.2f", d);
    }

    private int drawWrapped(GuiGraphics g, String text, int x, int y, int w, int color, int maxY) {
        for (FormattedCharSequence line : font.split(Component.literal(text), w)) {
            if (y + 9 > maxY) {
                break;
            }
            g.drawString(font, line, x, y, color, false);
            y += 9;
        }
        return y;
    }

    /** Draws a molecule centred in a box, as large as fits but never larger than maxSize. */
    private void drawMoleculeFitted(GuiGraphics g, Molecule m, double cx, double cy, double boxW, double boxH, double maxSize) {
        drawMoleculeFitted(g, m, null, cx, cy, boxW, boxH, maxSize, -1);
    }

    /** As above; with a tile name the coloured input/output hexes are drawn under the atoms, as on the field. */
    private void drawMoleculeFitted(GuiGraphics g, Molecule m, String tileName, double cx, double cy, double boxW, double boxH,
                                    double maxSize, int tint) {
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (Molecule.Atom a : m.atoms()) {
            double x = SQRT3 * (a.pos().q() + a.pos().r() / 2.0), y = 1.5 * a.pos().r();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        double s = Math.min(maxSize, Math.min(boxW / (maxX - minX + 2), boxH / (maxY - minY + 2)));
        double ox = cx - s * (minX + maxX) / 2, oy = cy - s * (minY + maxY) / 2;
        if (tileName != null) {
            for (Molecule.Atom a : m.atoms()) {
                Gfx.sprite(g, tileName, ox + s * SQRT3 * (a.pos().q() + a.pos().r() / 2.0), oy + s * 1.5 * a.pos().r(),
                        28 * s / 16.0, 2 * s, 28, 32, 0, tint);
            }
        }
        for (Molecule.Bond b : m.bonds()) {
            Gfx.segment(g, "bond", ox + s * SQRT3 * (b.from().q() + b.from().r() / 2.0), oy + s * 1.5 * b.from().r(),
                    ox + s * SQRT3 * (b.to().q() + b.to().r() / 2.0), oy + s * 1.5 * b.to().r(), Math.max(1.5, s * 0.3), 16, 4, tint);
        }
        for (Molecule.Atom a : m.atoms()) {
            drawAtom(g, a.element(), ox + s * SQRT3 * (a.pos().q() + a.pos().r() / 2.0), oy + s * 1.5 * a.pos().r(), s, tint);
        }
    }

    private void drawAtom(GuiGraphics g, Id element, double x, double y, double s, int argb) {
        String name = element.isDefaultNamespace() ? Gfx.orElse("atom_" + element.path(), "atom_unknown") : "atom_unknown";
        Gfx.sprite(g, name, x, y, s * 1.5, s * 1.5, 16, 16, 0, argb);
    }

    // field

    private void tile(GuiGraphics g, String name, Hex h, int argb) {
        double s = size();
        Gfx.sprite(g, name, px(h), py(h), 28 * s / 16.0, 2 * s, 28, 32, 0, argb);
    }

    private void drawField(GuiGraphics g, int mx, int my) {
        clip(g, fx0, fy0, fx1, fy1);
        // starry backdrop
        for (int y = fy0; y < fy1; y += 64) {
            for (int x = fx0; x < fx1; x += 64) {
                g.blit(Gfx.tex("background"), x, y, 0, 0, 64, 64, 64, 64);
            }
        }
        if (loaded && puzzle() != null) {
            HexField f = field();
            Puzzle p = puzzle();
            // tiles: the whole bounded field with its wall ring, or everything visible of an unbounded field
            Hex a = pick(fx0, fy0), b = pick(fx1, fy0), c = pick(fx0, fy1), d = pick(fx1, fy1);
            int rMin = Math.min(a.r(), b.r()) - 1, rMax = Math.max(c.r(), d.r()) + 1;
            int qMin = Math.min(Math.min(a.q(), b.q()), Math.min(c.q(), d.q())) - 1;
            int qMax = Math.max(Math.max(a.q(), b.q()), Math.max(c.q(), d.q())) + 1;
            for (int r = rMin; r <= rMax; r++) {
                for (int q = qMin; q <= qMax; q++) {
                    Hex h = new Hex(q, r);
                    if (f.contains(h)) {
                        tile(g, "hex", h, -1);
                    } else if (f.isWall(h)) {
                        tile(g, "hex_wall", h, -1);
                    }
                }
            }
            List<Part> parts = content.program().parts();
            List<Integer> arms = armParts();
            // tracks first, then glyphs and inputs/outputs, arms on top
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < parts.size(); i++) {
                    PartType pt = typeOf(parts.get(i));
                    boolean isTrack = pt != null && pt.kind() == PartType.Kind.TRACK;
                    boolean isArm = pt != null && pt.kind() == PartType.Kind.ARM;
                    if (isArm || isTrack != (pass == 0)) {
                        continue;
                    }
                    Part shown = parts.get(i);
                    if (i == trackExtendPart && trackDraft != null) {
                        shown = draftTrack(shown);
                    }
                    drawPart(g, shown, p, 1f, 0);
                }
            }
            // parts outside the field or overlapping are marked red while building
            if (run == null) {
                for (Part part : parts) {
                    for (Hex h : PartShapes.hexes(part, p, ModCore.REG)) {
                        if (!f.contains(h)) {
                            tile(g, "hex_fill", h, 0xFFFF4040);
                            tile(g, "hex_outline", h, 0xFFFF6B6B);
                        }
                    }
                }
            }
            if (selectedPart >= 0 && run == null && part(selectedPart) != null) {
                for (Hex h : PartShapes.hexes(part(selectedPart), p, ModCore.REG)) {
                    tile(g, "hex_outline", h, 0xFFE2B04A);
                }
            }
            if (run != null) {
                drawRunAtoms(g);
                for (int n = 0; n < arms.size() && n < run.arms().size(); n++) {
                    drawRunArm(g, parts.get(arms.get(n)), run.prevArms().get(n), run.arms().get(n), n + 1);
                }
            } else {
                for (int n = 0; n < arms.size(); n++) {
                    drawPart(g, parts.get(arms.get(n)), p, 1f, n + 1);
                }
            }
            if (errorPos != null) {
                tile(g, "hex_fill", errorPos, 0xFFFF4040);
                tile(g, "hex_outline", errorPos, 0xFFFFFFFF);
            }
            // ghosts: part in hand, part being dragged, track being drawn
            if (hoverHex != null && run == null) {
                if (trackDraft != null && trackExtendPart < 0) {
                    drawPart(g, Part.track(PartType.TRACK, trackDraft.get(0), relative(trackDraft, trackDraft.get(0))), p, 0.8f, 0);
                } else if (held != null) {
                    drawPart(g, held.withPos(hoverHex), p, 0.6f, 0);
                } else if (dragPart >= 0 && dragMoved && part(dragPart) != null) {
                    drawPart(g, part(dragPart).withPos(hoverHex.minus(dragOffset)), p, 0.6f, 0);
                }
                tile(g, "hex_outline", hoverHex, 0x90FFFFFF);
            }
        }
        unclip(g);
        Gfx.frame(g, fx0, fy0, fx1 - fx0, fy1 - fy0, Gfx.INK);
        Gfx.frame(g, fx0 + 1, fy0 + 1, fx1 - fx0 - 2, fy1 - fy0 - 2, focus == Focus.FIELD ? Gfx.BORDER_BRIGHT : Gfx.BORDER);
        drawProductReference(g, mx, my);
    }

    /** The product as a faint display in the field corner: only a reference, clearly weaker than real parts. */
    private void drawProductReference(GuiGraphics g, int mx, int my) {
        Puzzle p = puzzle();
        if (p == null) {
            return;
        }
        int w = 96, h = 62;
        int x = fx0 + 6, y = fy1 - h - 6;
        g.fill(x, y, x + w, y + h, 0x70120D1E);
        Gfx.frame(g, x, y, w, h, Gfx.BORDER_DIM);
        String label = tr("gui.resourcemod.product");
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3, Gfx.TEXT_DIM, false);
        clip(g, x + 1, y + 13, x + w - 1, y + h - 1);
        drawMoleculeFitted(g, p.product(), "io_output", x + w / 2.0, y + 13 + (h - 14) / 2.0, w - 10, h - 18, 12, 0x60FFFFFF);
        unclip(g);
        if (hover(x, y, w, h, mx, my)) {
            tooltip = tr("gui.resourcemod.product.tip");
        }
    }

    private static int alpha(float a) {
        return ((int) (a * 255) << 24) | 0xFFFFFF;
    }

    private void drawPart(GuiGraphics g, Part part, Puzzle p, float a, int armNumber) {
        PartType pt = typeOf(part);
        int tint = alpha(a);
        double s = size();
        if (pt == null) {
            tile(g, "part_unknown_tile", part.pos(), tint);
            return;
        }
        switch (pt.kind()) {
            case INPUT, OUTPUT -> {
                String tileName = pt.kind() == PartType.Kind.INPUT ? "io_input" : "io_output";
                Molecule m = PartShapes.molecule(part, p, ModCore.REG);
                if (m == null) {
                    tile(g, tileName, part.pos(), tint);
                    return;
                }
                for (Molecule.Atom atom : m.atoms()) {
                    tile(g, tileName, part.pos().plus(atom.pos()), tint);
                }
                int ghost = alpha(a * (pt.kind() == PartType.Kind.OUTPUT ? 0.8f : 0.45f));
                for (Molecule.Bond bond : m.bonds()) {
                    Hex from = part.pos().plus(bond.from()), to = part.pos().plus(bond.to());
                    Gfx.segment(g, "bond", px(from), py(from), px(to), py(to), s * 0.3, 16, 4, ghost);
                }
                for (Molecule.Atom atom : m.atoms()) {
                    Hex h = part.pos().plus(atom.pos());
                    drawAtom(g, atom.element(), px(h), py(h), s, ghost);
                }
            }
            case TRACK -> {
                List<Hex> hexes = part.trackHexes();
                for (Hex h : hexes) {
                    tile(g, "track", part.pos().plus(h), tint);
                }
                for (int i = 1; i < hexes.size(); i++) {
                    Hex h0 = part.pos().plus(hexes.get(i - 1)), h1 = part.pos().plus(hexes.get(i));
                    Gfx.line(g, px(h0), py(h0), px(h1), py(h1), Math.max(1, s / 6), ((int) (a * 255) << 24) | 0xE2B04A);
                }
                if (hexes.size() > 1) {
                    // a bright dot marks the end the "forward" instruction moves to
                    Hex end = part.pos().plus(hexes.get(hexes.size() - 1));
                    g.fill((int) px(end) - 2, (int) py(end) - 2, (int) px(end) + 2, (int) py(end) + 2, 0xFFFFFFFF);
                }
            }
            case GLYPH -> {
                String base = "glyph_" + part.type().path();
                boolean known = part.type().isDefaultNamespace() && Gfx.exists(base + "_a");
                tile(g, known ? base + "_a" : "part_unknown_tile", part.pos(), tint);
                int rot = Math.floorMod(part.rotation(), 6);
                for (Hex h : pt.footprintBesidesOrigin()) {
                    tile(g, known ? base + "_b" : "part_unknown_tile", part.pos().plus(h.rotateCcw(rot)), tint);
                }
            }
            case ARM -> drawArm(g, pt, px(part.pos()), py(part.pos()), part.rotation(), part.length(), false, armNumber, tint);
        }
    }

    /** Draws an arm. The rotation is in steps of 60 degrees counter-clockwise and may be fractional (animation). */
    private void drawArm(GuiGraphics g, PartType pt, double bx, double by, double rotation, double length,
                         boolean grabbing, int number, int tint) {
        double s = size();
        double reach = length * s * SQRT3;
        for (int k = 0; k < 6; k += pt.grabberStep()) {
            double angle = Math.toRadians(-60.0 * (rotation + k));
            double tx = bx + Math.cos(angle) * reach, ty = by + Math.sin(angle) * reach;
            Gfx.segment(g, "arm_segment", bx, by, tx, ty, s * 0.4, 16, 6, tint);
            Gfx.sprite(g, grabbing ? "grabber_closed" : "grabber_open", tx, ty, s * 1.7, s * 1.7, 16, 16,
                    (float) (-60.0 * (rotation + k)), tint);
        }
        Gfx.sprite(g, pt.isPiston() ? "arm_base_piston" : "arm_base", bx, by, s * 1.3, s * 1.3, 16, 16, 0, tint);
        if (number > 0 && s >= 10) {
            String label = Integer.toString(number);
            g.drawString(font, label, (int) (bx - font.width(label) / 2.0), (int) by - 4, Gfx.ARM_NO, false);
        }
    }

    private void drawRunArm(GuiGraphics g, Part part, Simulation.ArmView from, Simulation.ArmView to, int number) {
        PartType pt = typeOf(part);
        if (pt == null) {
            return;
        }
        double t = run.progress();
        int diff = Math.floorMod(to.rotation() - from.rotation() + 3, 6) - 3; // shortest way round
        double rotation = from.rotation() + diff * t;
        double length = from.length() + (to.length() - from.length()) * t;
        double bx = px(from.pos()) + (px(to.pos()) - px(from.pos())) * t;
        double by = py(from.pos()) + (py(to.pos()) - py(from.pos())) * t;
        drawArm(g, pt, bx, by, rotation, length, t < 1 ? from.grabbing() || to.grabbing() : to.grabbing(), number, -1);
    }

    private void drawRunAtoms(GuiGraphics g) {
        double t = run.progress();
        double s = size();
        if (t >= 1.0) {
            for (Simulation.AtomView a : run.atoms()) {
                for (int d = 0; d < 3; d++) {
                    if (a.bondedIn(d)) {
                        Hex n = a.pos().neighbor(d);
                        Gfx.segment(g, "bond", px(a.pos()), py(a.pos()), px(n), py(n), s * 0.3, 16, 4, -1);
                    }
                }
            }
            for (Simulation.AtomView a : run.atoms()) {
                drawAtom(g, a.element(), px(a.pos()), py(a.pos()), s, -1);
            }
            return;
        }
        // between two cycles: every atom of the state before, moved along its rigid movement
        List<Simulation.AtomView> atoms = run.prevAtoms();
        double[][] at = new double[atoms.size()][];
        for (int i = 0; i < atoms.size(); i++) {
            Simulation.AtomView a = atoms.get(i);
            Simulation.Motion m = run.motionOf(a.pos());
            at[i] = moved(px(a.pos()), py(a.pos()), m, t);
            for (int d = 0; d < 3; d++) {
                if (a.bondedIn(d)) {
                    Hex n = a.pos().neighbor(d);
                    double[] other = moved(px(n), py(n), m, t);
                    Gfx.segment(g, "bond", at[i][0], at[i][1], other[0], other[1], s * 0.3, 16, 4, -1);
                }
            }
        }
        for (int i = 0; i < atoms.size(); i++) {
            drawAtom(g, atoms.get(i).element(), at[i][0], at[i][1], s, -1);
        }
    }

    private double[] moved(double x, double y, Simulation.Motion m, double t) {
        if (m == null) {
            return new double[]{x, y};
        }
        if (m.rotation() != 0) {
            double cx = px(m.center()), cy = py(m.center());
            double angle = Math.toRadians(-60.0 * m.rotation() * t);
            double dx = x - cx, dy = y - cy;
            x = cx + dx * Math.cos(angle) - dy * Math.sin(angle);
            y = cy + dx * Math.sin(angle) + dy * Math.cos(angle);
        }
        Hex tr = m.translation();
        x += (px(tr) - px(Hex.ZERO)) * t;
        y += (py(tr) - py(Hex.ZERO)) * t;
        return new double[]{x, y};
    }

    // selection bar

    private void drawSelectionBar(GuiGraphics g, int mx, int my) {
        Part p = held != null ? held : run == null ? part(selectedPart) : null;
        if (p == null || !loaded) {
            return;
        }
        PartType pt = typeOf(p);
        String name = pt == null ? tr("gui.resourcemod.unknown_part", p.type().toString())
                : pt.kind() == PartType.Kind.INPUT ? tr("gui.resourcemod.input.short", p.inputIndex() + 1)
                : pt.kind() == PartType.Kind.OUTPUT ? tr("gui.resourcemod.output") : nameOf("part", p.type());
        if (held != null) {
            name = tr("gui.resourcemod.placing", name);
        }
        boolean canRotate = pt != null && pt.hasRotation();
        boolean isArm = pt != null && pt.kind() == PartType.Kind.ARM;
        boolean isTrack = pt != null && pt.kind() == PartType.Kind.TRACK;
        boolean sel = held == null;
        int w = font.width(name) + 12 + 5 * 19;
        int x = fx0 + 4, y = fy0 + 4;
        Gfx.panel(g, x, y, w, 22);
        g.drawString(font, name, x + 5, y + 7, Gfx.TEXT, false);
        int bx = x + font.width(name) + 10;
        iconButton(g, bx, y + 2, "ctl_rotate_ccw", canRotate, false, tr("gui.resourcemod.rotate_ccw.tip"), mx, my, () -> rotateSelected(1));
        iconButton(g, bx + 19, y + 2, "ctl_rotate_cw", canRotate, false, tr("gui.resourcemod.rotate_cw.tip"), mx, my, () -> rotateSelected(-1));
        iconButton(g, bx + 38, y + 2, "ctl_shorter", sel && (isArm && p.length() > 1 || isTrack && p.trackHexes().size() > 1), false,
                tr(isTrack ? "gui.resourcemod.track_shorter.tip" : "gui.resourcemod.shorter.tip"), mx, my, () -> resizeSelected(-1));
        iconButton(g, bx + 57, y + 2, "ctl_longer", sel && isArm && p.length() < 3, false,
                tr(isTrack ? "gui.resourcemod.track_longer.tip" : "gui.resourcemod.longer.tip"), mx, my, () -> resizeSelected(1));
        iconButton(g, bx + 76, y + 2, "ctl_remove", true, false, tr(sel ? "gui.resourcemod.remove.tip" : "gui.resourcemod.cancel"), mx, my,
                () -> {
                    if (held != null) {
                        held = null;
                    } else {
                        removeSelected();
                    }
                });
    }

    // bottom: controls, tapes, instructions

    private void drawBottom(GuiGraphics g, int mx, int my) {
        int y0 = fy1;
        Gfx.panel(g, 0, y0, W, bottomH);
        Puzzle p = puzzle();
        boolean ready = loaded && p != null && dialog == null && !puzzleList;
        // controls
        int x = 6, y = y0 + 5;
        iconButton(g, x, y, "ctl_stop", run != null, false, tr("gui.resourcemod.stop.tip"), mx, my, this::stopRun);
        boolean playing = run != null && run.playing();
        iconButton(g, x + 19, y, playing ? "ctl_pause" : "ctl_play", ready, playing,
                tr(playing ? "gui.resourcemod.pause.tip" : "gui.resourcemod.play.tip"), mx, my, this::togglePlay);
        iconButton(g, x + 38, y, "ctl_step", ready && !playing, false, tr("gui.resourcemod.step.tip"), mx, my, this::stepRun);
        x += 61;
        for (int i = 0; i <= SimRunner.MAX_SPEED; i++) {
            int index = i;
            String label = i < SimRunner.MAX_SPEED ? SPEED_LABELS[i] : tr("gui.resourcemod.speed.max");
            int w = font.width(label) + 8;
            textButton(g, x, y + 2, w, 14, label, true, speed == i,
                    i == SimRunner.MAX_SPEED ? tr("gui.resourcemod.speed.max.tip")
                            : tr("gui.resourcemod.speed.tip", label, trimNumber(SimRunner.SPEEDS[i])), mx, my,
                    () -> speed = index);
            x += w + 1;
        }
        // editing buttons: they used to sit in the top bar
        x += 3;
        g.fill(x, y + 2, x + 1, y + 16, Gfx.BORDER);
        Gfx.diamond(g, x, y + 9, 1, Gfx.GOLD, Gfx.GOLD);
        x += 5;
        boolean canEdit = canEdit();
        iconButton(g, x, y, "ctl_undo", canEdit && history.canUndo(), false,
                history.canUndo() ? tr("gui.resourcemod.undo", tr("gui.resourcemod." + history.peekUndo().nameKey()))
                        : tr("gui.resourcemod.undo.none"), mx, my, this::undo);
        iconButton(g, x + 19, y, "ctl_redo", canEdit && history.canRedo(), false,
                history.canRedo() ? tr("gui.resourcemod.redo", tr("gui.resourcemod." + history.peekRedo().nameKey()))
                        : tr("gui.resourcemod.redo.none"), mx, my, this::redo);
        boolean pending = pendingExists() && run == null;
        iconButton(g, x + 38, y, "ctl_save", pending, false, tr("gui.resourcemod.save.tip"), mx, my, this::requestSave);
        iconButton(g, x + 57, y, "ctl_discard", pending, false, tr("gui.resourcemod.discard.tip"), mx, my, this::doDiscard);
        iconButton(g, x + 76, y, "ctl_close", true, false, tr("gui.resourcemod.close.tip"), mx, my, this::tryClose);
        x += 100;
        // metrics on the right, the state of the run in whatever room is left between
        int chipsX = drawMetricChips(g, x, W - 7, y + 2, mx, my);
        String info = run != null ? tr("gui.resourcemod.cycle", run.inBackground() ? run.backgroundCycle() : run.cycle())
                : tr("gui.resourcemod.editing");
        Puzzle pz = puzzle();
        if (pz != null) {
            int made = run != null && !run.inBackground() ? run.sim().products() : 0;
            String full = info + "   " + tr("gui.resourcemod.products") + " " + made + " / " + pz.effectiveTestProducts();
            if (chipsX - x - 6 >= font.width(full)) {
                info = full;
            }
        }
        if (chipsX - x - 6 >= font.width(info)) {
            g.drawString(font, info, x, y + 5, run != null ? Gfx.CYAN : Gfx.TEXT_DIM, false);
        }

        drawInstructions(g, mx, my);
        // tapes
        List<Integer> arms = armParts();
        int tx0 = tapeLeft + 4 + TAPE_LABEL_W, cols = Math.max(1, (W - 4 - tx0) / cell);
        tapeRowScroll = Math.max(0, Math.min(tapeRowScroll, Math.max(0, arms.size() - tapeRows)));
        int headY = y0 + ctlH + 2;
        g.drawString(font, tr("gui.resourcemod.tapes"), tapeLeft + 5, headY, Gfx.GOLD, false);
        for (int c = 0; c < cols; c++) {
            int slot = tapeScroll + c;
            if (slot % 5 == 0) {
                g.drawString(font, Integer.toString(slot), tx0 + c * cell + 1, headY, Gfx.TEXT_DIM, false);
            }
        }
        Gfx.frame(g, tapeLeft + 3, tapeY0 - 1, W - tapeLeft - 6, tapeRows * cell + 2, focus == Focus.TAPE ? Gfx.BORDER_BRIGHT : Gfx.BORDER);
        if (arms.isEmpty()) {
            g.drawString(font, tr("gui.resourcemod.tapes.empty"), tx0 + 4, tapeY0 + 4, Gfx.TEXT_DIM, false);
        }
        for (int r = 0; r < tapeRows && r + tapeRowScroll < arms.size(); r++) {
            int armNo = r + tapeRowScroll;
            int partIndex = arms.get(armNo);
            Part arm = part(partIndex);
            int ry = tapeY0 + r * cell;
            boolean selectedRow = partIndex == selectedPart;
            g.fill(tapeLeft + 4, ry, tx0 - 1, ry + cell - 1, selectedRow ? Gfx.ACTIVE : Gfx.PANEL_DARK);
            g.drawString(font, tr("gui.resourcemod.arm_label", armNo + 1), tapeLeft + 7, ry + 4, selectedRow ? Gfx.GOLD : Gfx.TEXT, false);
            if (hover(tapeLeft + 4, ry, TAPE_LABEL_W - 1, cell - 1, mx, my)) {
                tooltip = tr("gui.resourcemod.arm_label.tip", armNo + 1);
            }
            if (run == null && dialog == null && !puzzleList) {
                hits.add(new Hit(tapeLeft + 4, ry, TAPE_LABEL_W - 1, cell - 1, () -> {
                    selectedPart = partIndex;
                    focus = Focus.TAPE;
                }));
            }
            int playSlot = run != null && !run.inBackground() && run.cycle() > 0 ? run.sim().tapeSlot(armNo, run.cycle() - 1) : -1;
            for (int c = 0; c < cols; c++) {
                int slot = tapeScroll + c;
                int cx = tx0 + c * cell;
                boolean cursor = selectedRow && slot == tapeSlot && run == null && focus == Focus.TAPE;
                g.fill(cx, ry, cx + cell - 1, ry + cell - 1,
                        slot == playSlot ? Gfx.TAPE_PLAY : cursor ? Gfx.ACTIVE : slot % 2 == 0 ? Gfx.PANEL_DARK : Gfx.TAPE_ALT);
                Id inst = arm.instructions().get(slot);
                if (inst != null) {
                    String icon = inst.isDefaultNamespace() ? Gfx.orElse("instr_" + inst.path(), "instr_unknown") : "instr_unknown";
                    boolean dragging = instrDragPart == partIndex && instrDragSlot == slot;
                    Gfx.sprite(g, icon, cx + (cell - 1) / 2.0, ry + (cell - 1) / 2.0, cell - 2, cell - 2, 16, 16, 0,
                            dragging ? 0x80FFFFFF : -1);
                    if (hover(cx, ry, cell - 1, cell - 1, mx, my)) {
                        tooltip = nameOf("instruction", inst);
                    }
                }
                if (cursor) {
                    Gfx.frame(g, cx, ry, cell - 1, cell - 1, Gfx.GOLD);
                }
                if (slot == playSlot) {
                    // the running step: a bright bar at the cell edge, continuous over arms that are on the same step
                    g.fill(cx - 1, ry - (r > 0 ? 1 : 0), cx + 1, ry + cell, Gfx.CYAN);
                    if (r == 0) {
                        Gfx.diamond(g, cx, tapeY0 - 3, 2, Gfx.CYAN, Gfx.TEXT);
                    }
                }
            }
        }
        if (arms.size() > tapeRows) {
            g.drawString(font, (tapeRowScroll > 0 ? "^" : " ") + (tapeRowScroll + tapeRows < arms.size() ? "v" : ""),
                    tapeLeft + TAPE_LABEL_W - 10, headY, Gfx.TEXT_DIM, false);
        }

        // message line: version problems, run result, program problems, otherwise what can be done right now
        String line = hint();
        int lineColor = Gfx.TEXT_DIM;
        if (!ClientData.mismatches().isEmpty()) {
            line = tr("gui.resourcemod.version_mismatch", String.join("; ", ClientData.mismatches()));
            lineColor = Gfx.BAD;
        } else if (!message.isEmpty()) {
            line = message;
            lineColor = messageKind == 2 ? Gfx.BAD : Gfx.GOOD;
        } else if (run == null && !errors.isEmpty()) {
            String first = coreText(errors.get(0).key(), errors.get(0).detail());
            String more = errors.size() > 1 ? " " + tr("gui.resourcemod.more_problems", errors.size() - 1) : "";
            line = tr("gui.resourcemod.problem", first) + more;
            lineColor = Gfx.GOLD;
        }
        int lineY = y0 + bottomH - hintH - 2;
        g.drawString(font, fit(line, W - 8), 4, lineY, lineColor, false);
        if (hover(0, lineY - 1, W, hintH, mx, my) && font.width(line) > W - 8) {
            tooltip = line;
        }
    }

    private String hint() {
        if (!loaded) {
            return "";
        }
        if (puzzle() == null) {
            return tr("gui.resourcemod.hint.no_puzzle");
        }
        if (run != null) {
            return tr("gui.resourcemod.hint.running");
        }
        if (held != null) {
            PartType pt = typeOf(held);
            return tr(pt != null && pt.kind() == PartType.Kind.TRACK ? "gui.resourcemod.hint.track" : "gui.resourcemod.hint.placing");
        }
        if (focus == Focus.TAPE && part(selectedPart) != null) {
            return tr("gui.resourcemod.hint.tape");
        }
        if (part(selectedPart) != null) {
            PartType pt = typeOf(part(selectedPart));
            return tr(pt != null && pt.kind() == PartType.Kind.TRACK ? "gui.resourcemod.hint.selected_track" : "gui.resourcemod.hint.selected");
        }
        return tr("gui.resourcemod.hint.idle");
    }

    // overlays

    private void drawDialog(GuiGraphics g, int mx, int my) {
        hits.clear();
        g.fill(0, 0, W, H, 0xA0000000);
        int w = Math.min(300, W - 20);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String line : dialog.lines) {
            lines.addAll(font.split(Component.literal(line), w - 16));
        }
        int h = 24 + lines.size() * 10 + 24;
        int x = (W - w) / 2, y = (H - h) / 2;
        Gfx.panel(g, x, y, w, h);
        g.drawString(font, dialog.title, x + 8, y + 7, Gfx.GOLD, false);
        int ly = y + 21;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 8, ly, Gfx.TEXT, false);
            ly += 10;
        }
        int total = 0;
        for (DialogButton b : dialog.buttons) {
            total += font.width(b.label) + 16 + 4;
        }
        int bx = x + w - 6 - total;
        for (DialogButton b : dialog.buttons) {
            int bw = font.width(b.label) + 16;
            textButton(g, bx, y + h - 20, bw, 15, b.label, true, false, null, mx, my, b.action);
            bx += bw + 4;
        }
    }

    private List<Map.Entry<Id, Puzzle>> filteredPuzzles() {
        List<Map.Entry<Id, Puzzle>> out = new ArrayList<>();
        String needle = search.toLowerCase(Locale.ROOT);
        for (Map.Entry<Id, Puzzle> e : library().puzzles().entrySet()) {
            if (namespaceFilter != null && !namespaceFilter.equals(e.getKey().namespace())) {
                continue;
            }
            if (metricFilter != null && !metricFilter.equals(e.getValue().metric())) {
                continue;
            }
            if (!needle.isEmpty() && !e.getKey().toString().contains(needle)
                    && !puzzleName(e.getKey(), e.getValue()).toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    private void drawPuzzleList(GuiGraphics g, int mx, int my) {
        hits.clear();
        g.fill(0, 0, W, H, 0xA0000000);
        int w = Math.min(360, W - 20), h = Math.min(260, H - 16);
        int x = (W - w) / 2, y = (H - h) / 2;
        Gfx.panel(g, x, y, w, h);
        g.drawString(font, tr("gui.resourcemod.puzzles.title"), x + 8, y + 7, Gfx.GOLD, false);
        iconButton(g, x + w - 21, y + 2, "ctl_close", true, false, tr("gui.resourcemod.cancel"), mx, my, () -> puzzleList = false);
        // search and filters
        int sy = y + 22;
        g.fill(x + 8, sy, x + w - 8, sy + 14, Gfx.PANEL_DARK);
        Gfx.frame(g, x + 8, sy, w - 16, 14, Gfx.BORDER_BRIGHT);
        String shown = search.isEmpty() ? tr("gui.resourcemod.puzzles.search") : search + "_";
        g.drawString(font, fit(shown, w - 24), x + 12, sy + 3, search.isEmpty() ? Gfx.TEXT_DIM : Gfx.TEXT, false);
        int fy = sy + 17;
        String nsLabel = tr("gui.resourcemod.puzzles.namespace", namespaceFilter == null ? tr("gui.resourcemod.all") : namespaceFilter);
        String mLabel = tr("gui.resourcemod.puzzles.metric", metricFilter == null ? tr("gui.resourcemod.all") : nameOf("metric", metricFilter));
        textButton(g, x + 8, fy, font.width(nsLabel) + 10, 14, nsLabel, true, namespaceFilter != null,
                tr("gui.resourcemod.puzzles.filter.tip"), mx, my, this::cycleNamespace);
        textButton(g, x + 22 + font.width(nsLabel), fy, font.width(mLabel) + 10, 14, mLabel, true, metricFilter != null,
                tr("gui.resourcemod.puzzles.filter.tip"), mx, my, this::cycleMetric);
        // list
        int ly = fy + 18, rowH = 30, visible = Math.max(1, (y + h - 6 - ly) / rowH);
        List<Map.Entry<Id, Puzzle>> list = filteredPuzzles();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, list.size() - visible)));
        if (list.isEmpty()) {
            g.drawString(font, tr("gui.resourcemod.puzzles.none"), x + 10, ly + 4, Gfx.TEXT_DIM, false);
        }
        for (int i = 0; i < visible && i + listScroll < list.size(); i++) {
            Map.Entry<Id, Puzzle> e = list.get(i + listScroll);
            int ry = ly + i * rowH;
            boolean hov = hover(x + 6, ry, w - 12, rowH - 2, mx, my);
            boolean current = e.getKey().equals(content.puzzle());
            g.fill(x + 6, ry, x + w - 6, ry + rowH - 2, hov ? Gfx.PANEL_LIGHT : Gfx.PANEL_DARK);
            if (current) {
                Gfx.frame(g, x + 6, ry, w - 12, rowH - 2, Gfx.GOLD);
            }
            clip(g, x + 8, ry + 1, x + 70, ry + rowH - 3);
            drawMoleculeFitted(g, e.getValue().product(), x + 39, ry + (rowH - 2) / 2.0, 58, rowH - 6, 7);
            unclip(g);
            g.drawString(font, fit(puzzleName(e.getKey(), e.getValue()), w - 90), x + 74, ry + 4, Gfx.TEXT, false);
            String info = e.getKey() + "   " + nameOf("metric", e.getValue().metric()) + ": " + trimNumber(e.getValue().reference());
            g.drawString(font, fit(info, w - 90), x + 74, ry + 15, Gfx.TEXT_DIM, false);
            Id id = e.getKey();
            hits.add(new Hit(x + 6, ry, w - 12, rowH - 2, () -> choosePuzzle(id)));
        }
        if (list.size() > visible) {
            String pos = (listScroll + 1) + "-" + Math.min(list.size(), listScroll + visible) + " / " + list.size();
            g.drawString(font, pos, x + w - 30 - font.width(pos), y + 7, Gfx.TEXT_DIM, false);
        }
    }

    private void cycleNamespace() {
        TreeSet<String> all = new TreeSet<>();
        for (Id id : library().puzzles().keySet()) {
            all.add(id.namespace());
        }
        namespaceFilter = namespaceFilter == null ? (all.isEmpty() ? null : all.first()) : all.higher(namespaceFilter);
        listScroll = 0;
    }

    private void cycleMetric() {
        List<ScoreMetric> all = ModCore.REG.metrics.values();
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(metricFilter)) {
                index = i;
            }
        }
        metricFilter = index + 1 < all.size() ? all.get(index + 1).id() : null;
        listScroll = 0;
    }

    // actions

    private void choosePuzzle(Id id) {
        puzzleList = false;
        if (id.equals(content.puzzle())) {
            return;
        }
        if (content.puzzle() != null && !content.program().parts().isEmpty()) {
            // programs only fit their own puzzle: ask before starting with an empty program
            dialog = new Dialog(tr("gui.resourcemod.switch.title"),
                    List.of(tr(status != null && status.saved().confirmed() ? "gui.resourcemod.switch.text.confirmed"
                            : "gui.resourcemod.switch.text")),
                    List.of(new DialogButton(tr("gui.resourcemod.switch.yes"), () -> {
                        dialog = null;
                        switchPuzzle(id);
                    }), new DialogButton(tr("gui.resourcemod.cancel"), () -> dialog = null)));
        } else {
            switchPuzzle(id);
        }
    }

    /** A puzzle change empties the history and cannot be undone. */
    private void switchPuzzle(Id id) {
        stopRun();
        content = new SlotContent(id, content.stage(), new Program(List.of()));
        history.clear();
        selectedPart = -1;
        held = null;
        fitted = false;
        onContentChanged(true);
    }

    private void changeStage(int delta) {
        int stage = Math.max(1, Math.min(ClientData.stages(), content.stage() + delta));
        applyEdit(Edit.stage(content, stage));
        fitted = false;
    }

    private void undo() {
        if (canEdit() && history.canUndo()) {
            boolean stage = history.peekUndo().kind() == Edit.Kind.STAGE;
            content = history.undo(content);
            onContentChanged(true);
            fitted = fitted && !stage;
        }
    }

    private void redo() {
        if (canEdit() && history.canRedo()) {
            boolean stage = history.peekRedo().kind() == Edit.Kind.STAGE;
            content = history.redo(content);
            onContentChanged(true);
            fitted = fitted && !stage;
        }
    }

    private void rotateSelected(int steps) {
        if (held != null) {
            PartType pt = typeOf(held);
            if (pt != null && pt.hasRotation()) {
                held = held.withRotation(Math.floorMod(held.rotation() + steps, 6));
            }
            return;
        }
        Part p = part(selectedPart);
        PartType pt = typeOf(p);
        if (canEdit() && pt != null && pt.hasRotation()) {
            applyEdit(Edit.change(Edit.Kind.ROTATE, content, selectedPart, p.withRotation(Math.floorMod(p.rotation() + steps, 6))));
        }
    }

    private void resizeSelected(int delta) {
        Part p = part(selectedPart);
        PartType pt = typeOf(p);
        if (!canEdit() || pt == null) {
            return;
        }
        if (pt.kind() == PartType.Kind.ARM) {
            int length = Math.max(1, Math.min(3, p.length() + delta));
            applyEdit(Edit.change(Edit.Kind.ARM_LENGTH, content, selectedPart, p.withLength(length)));
        } else if (pt.kind() == PartType.Kind.TRACK && delta < 0 && p.trackHexes().size() > 1) {
            List<Hex> hexes = new ArrayList<>(p.trackHexes());
            Hex removed = hexes.remove(hexes.size() - 1);
            if (removed.equals(Hex.ZERO)) {
                return; // the track's own position stays part of it
            }
            applyEdit(Edit.change(Edit.Kind.TRACK_SHORTEN, content, selectedPart, p.withTrackHexes(hexes)));
        }
    }

    private void removeSelected() {
        if (canEdit() && part(selectedPart) != null) {
            applyEdit(Edit.remove(content, selectedPart));
            selectedPart = -1;
        }
    }

    private void setInstruction(Id instruction) {
        Part p = part(selectedPart);
        PartType pt = typeOf(p);
        if (!canEdit() || pt == null || pt.kind() != PartType.Kind.ARM) {
            return;
        }
        Map<Integer, Id> tape = new TreeMap<>(p.instructions());
        tape.put(tapeSlot, instruction);
        applyEdit(Edit.change(Edit.Kind.INSTRUCTION_SET, content, selectedPart, p.withInstructions(tape)));
        tapeSlot++;
        focus = Focus.TAPE;
        keepCursorVisible();
    }

    private void deleteInstruction() {
        Part p = part(selectedPart);
        if (!canEdit() || p == null || !p.instructions().containsKey(tapeSlot)) {
            return;
        }
        Map<Integer, Id> tape = new TreeMap<>(p.instructions());
        tape.remove(tapeSlot);
        applyEdit(Edit.change(Edit.Kind.INSTRUCTION_DELETE, content, selectedPart, p.withInstructions(tape)));
    }

    private void keepCursorVisible() {
        int cols = Math.max(1, (W - tapeLeft - 8 - TAPE_LABEL_W) / cell);
        if (tapeSlot < tapeScroll) {
            tapeScroll = tapeSlot;
        } else if (tapeSlot >= tapeScroll + cols) {
            tapeScroll = tapeSlot - cols + 1;
        }
    }

    // running

    private boolean startRun() {
        if (run != null) {
            return true;
        }
        Puzzle p = puzzle();
        if (!loaded || p == null) {
            return false;
        }
        if (!errors.isEmpty()) {
            setMessage(2, tr("gui.resourcemod.cannot_run"), null);
            return false;
        }
        try {
            run = new SimRunner(p, content.program(), field(), ModCore.REG);
        } catch (RuntimeException e) {
            setMessage(2, tr("gui.resourcemod.cannot_run") + " " + e.getMessage(), null);
            return false;
        }
        runReported = false;
        held = null;
        message = "";
        errorPos = null;
        return true;
    }

    private void togglePlay() {
        if (run != null && run.playing()) {
            run.setPlaying(false);
        } else if (startRun()) {
            run.setPlaying(true);
        }
    }

    private void stepRun() {
        if (startRun()) {
            run.setPlaying(false);
            run.stepOnce();
        }
    }

    private void stopRun() {
        if (run != null) {
            run.cancelBackground();
            run = null;
        }
    }

    /** Every run in the GUI that reaches the required number of products is a test run. */
    private void checkRunFinished() {
        if (run == null || runReported || run.inBackground() || !run.finished()) {
            return;
        }
        runReported = true;
        if (run.solved()) {
            Map<Id, Long> metrics = new TreeMap<>();
            var m = new org.example.resourcemod.core.score.Measurements(run.cycle(),
                    Scoring.partValue(content.program(), library().values()), run.sim().area(), run.sim().instructionCount());
            for (ScoreMetric metric : ModCore.REG.metrics.values()) {
                metrics.put(metric.id(), metric.value(m));
            }
            solvedMetrics = metrics;
            setMessage(1, tr("gui.resourcemod.solved", run.cycle()), null);
            submit();
        } else {
            SimError e = run.error();
            String where = e.position() == null ? "" : " [" + e.position().q() + ", " + e.position().r() + "]";
            setMessage(2, tr("gui.resourcemod.run_failed", coreText(e.key(), Integer.toString(e.cycle())) + where), e.position());
        }
    }

    private void submit() {
        if (!ClientData.mismatches().isEmpty() || currentConfirmed()) {
            return; // version differs (the server would reject) or this revision is already confirmed
        }
        lastSentSeq = ++seq;
        lastSent = content;
        ClientNet.sendProgram(menu.containerId, seq, content, true, run.cycle());
    }

    // saving and closing

    private void sendInterimIfDirty() {
        if (loaded && dirty()) {
            lastSentSeq = ++seq;
            lastSent = content;
            ClientNet.sendProgram(menu.containerId, seq, content, false, 0);
        }
    }

    @Override
    protected void containerTick() {
        // an interim state a few seconds after the last change, so a crash loses at most those seconds
        if (loaded && dirty() && Util.getMillis() - lastEditTime > INTERIM_DELAY_MS) {
            sendInterimIfDirty();
        }
    }

    private void requestSave() {
        if (!pendingExists()) {
            return;
        }
        if (!currentConfirmed()) {
            dialog = new Dialog(tr("gui.resourcemod.save.unconfirmed.title"), List.of(tr("gui.resourcemod.save.unconfirmed.text")),
                    List.of(new DialogButton(tr("gui.resourcemod.save.anyway"), () -> {
                        dialog = null;
                        doSave();
                    }), new DialogButton(tr("gui.resourcemod.cancel"), () -> dialog = null)));
        } else {
            doSave();
        }
    }

    private void doSave() {
        sendInterimIfDirty();
        ClientNet.sendCommand(menu.containerId, Net.C_SAVE);
        savedContent = content;
    }

    private void doDiscard() {
        stopRun();
        ClientNet.sendCommand(menu.containerId, Net.C_DISCARD); // the server answers with the saved program
        lastSent = savedContent;
    }

    private void tryClose() {
        stopRun();
        if (loaded && pendingExists()) {
            dialog = new Dialog(tr("gui.resourcemod.close.title"),
                    List.of(tr("gui.resourcemod.close.text"),
                            currentConfirmed() ? "" : tr("gui.resourcemod.save.unconfirmed.text")),
                    List.of(new DialogButton(tr("gui.resourcemod.save"), () -> {
                        doSave();
                        closeNow();
                    }), new DialogButton(tr("gui.resourcemod.discard"), () -> {
                        ClientNet.sendCommand(menu.containerId, Net.C_DISCARD);
                        lastSent = content;
                        closeNow();
                    }), new DialogButton(tr("gui.resourcemod.back"), () -> dialog = null)));
            return;
        }
        closeNow();
    }

    private void closeNow() {
        sendInterimIfDirty();
        super.onClose();
    }

    @Override
    public void onClose() {
        tryClose();
    }

    @Override
    public void removed() {
        stopRun();
        super.removed();
    }

    // input

    @Override
    public boolean mouseClicked(double realX, double realY, int button) {
        double mx = realX * mouseFactor, my = realY * mouseFactor;
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                if (hits.get(i).contains(mx, my)) {
                    hits.get(i).action.run();
                    return true;
                }
            }
        }
        if (dialog != null || puzzleList || !loaded) {
            return true;
        }
        if (inField(mx, my)) {
            focus = Focus.FIELD;
            if (button != 0) {
                if (held != null && button == 1) {
                    held = null;
                } else {
                    panning = true;
                }
                return true;
            }
            if (!canEdit()) {
                panning = true;
                return true;
            }
            Hex h = pick(mx, my);
            if (held != null) {
                PartType pt = typeOf(held);
                if (pt != null && pt.kind() == PartType.Kind.TRACK) {
                    trackDraft = new ArrayList<>(List.of(h));
                    trackExtendPart = -1;
                } else {
                    applyEdit(Edit.place(content, held.withPos(h)));
                    selectedPart = content.program().parts().size() - 1;
                    held = null;
                }
                return true;
            }
            int index = partAt(h);
            if (index < 0) {
                selectedPart = -1;
                panning = true;
                return true;
            }
            selectedPart = index;
            Part p = part(index);
            PartType pt = typeOf(p);
            if (pt != null && pt.kind() == PartType.Kind.TRACK && !hasShiftDown()) {
                List<Hex> abs = absolute(p);
                if (h.equals(abs.get(abs.size() - 1)) || h.equals(abs.get(0))) {
                    // dragging from an end of a track lays more track
                    trackDraft = new ArrayList<>(abs);
                    trackExtendPart = index;
                    trackExtendFront = !h.equals(abs.get(abs.size() - 1));
                    return true;
                }
            }
            dragPart = index;
            dragOffset = h.minus(p.pos());
            dragMoved = false;
            return true;
        }
        if (inTape(mx, my) && run == null) {
            List<Integer> arms = armParts();
            int row = (int) ((my - tapeY0) / cell) + tapeRowScroll;
            int slot = (int) ((mx - tapeLeft - 4 - TAPE_LABEL_W) / cell) + tapeScroll;
            if (row >= 0 && row < arms.size()) {
                selectedPart = arms.get(row);
                tapeSlot = slot;
                focus = Focus.TAPE;
                if (button == 1) {
                    deleteInstruction();
                } else if (part(selectedPart).instructions().containsKey(slot)) {
                    instrDragPart = selectedPart;
                    instrDragSlot = slot;
                }
            }
            return true;
        }
        return true;
    }

    private static List<Hex> absolute(Part track) {
        List<Hex> out = new ArrayList<>();
        for (Hex h : track.trackHexes()) {
            out.add(track.pos().plus(h));
        }
        return out;
    }

    private static List<Hex> relative(List<Hex> abs, Hex origin) {
        List<Hex> out = new ArrayList<>();
        for (Hex h : abs) {
            out.add(h.minus(origin));
        }
        return out;
    }

    private Part draftTrack(Part original) {
        return original.withTrackHexes(relative(trackDraft, original.pos()));
    }

    @Override
    public boolean mouseDragged(double realX, double realY, int button, double realDx, double realDy) {
        double mx = realX * mouseFactor, my = realY * mouseFactor, dx = realDx * mouseFactor, dy = realDy * mouseFactor;
        if (panning) {
            panX += dx;
            panY += dy;
            return true;
        }
        if (trackDraft != null && inField(mx, my)) {
            Hex h = pick(mx, my);
            boolean front = trackExtendPart >= 0 && trackExtendFront;
            Hex end = front ? trackDraft.get(0) : trackDraft.get(trackDraft.size() - 1);
            int originalSize = trackExtendPart >= 0 ? part(trackExtendPart).trackHexes().size() : 1;
            if (trackDraft.size() > originalSize && trackDraft.size() > 1
                    && h.equals(front ? trackDraft.get(1) : trackDraft.get(trackDraft.size() - 2))) {
                trackDraft.remove(front ? 0 : trackDraft.size() - 1); // moved back: take the last field away again
            } else if (h.distanceTo(end) == 1 && !trackDraft.contains(h) && field().contains(h)) {
                trackDraft.add(front ? 0 : trackDraft.size(), h);
            }
            return true;
        }
        if (dragPart >= 0) {
            dragMoved = true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double realX, double realY, int button) {
        double mx = realX * mouseFactor, my = realY * mouseFactor;
        panning = false;
        if (trackDraft != null) {
            if (canEdit()) {
                if (trackExtendPart >= 0 && part(trackExtendPart) != null) {
                    Part p = part(trackExtendPart);
                    if (trackDraft.size() != p.trackHexes().size()) {
                        applyEdit(Edit.change(Edit.Kind.TRACK_EXTEND, content, trackExtendPart, draftTrack(p)));
                    }
                } else {
                    applyEdit(Edit.place(content, Part.track(PartType.TRACK, trackDraft.get(0), relative(trackDraft, trackDraft.get(0)))));
                    selectedPart = content.program().parts().size() - 1;
                    held = null;
                }
            }
            trackDraft = null;
            trackExtendPart = -1;
            return true;
        }
        if (dragPart >= 0) {
            Part p = part(dragPart);
            if (dragMoved && p != null && canEdit() && inField(mx, my)) {
                Hex target = pick(mx, my).minus(dragOffset);
                if (!target.equals(p.pos())) {
                    applyEdit(Edit.change(Edit.Kind.MOVE, content, dragPart, p.withPos(target)));
                }
            }
            dragPart = -1;
            return true;
        }
        if (instrDragPart >= 0) {
            Part p = part(instrDragPart);
            if (p != null && canEdit() && inTape(mx, my)) {
                int slot = (int) ((mx - tapeLeft - 4 - TAPE_LABEL_W) / cell) + tapeScroll;
                Id inst = p.instructions().get(instrDragSlot);
                if (inst != null && slot != instrDragSlot && slot >= 0) {
                    Map<Integer, Id> tape = new TreeMap<>(p.instructions());
                    tape.remove(instrDragSlot);
                    tape.put(slot, inst);
                    applyEdit(Edit.change(Edit.Kind.INSTRUCTION_MOVE, content, instrDragPart, p.withInstructions(tape)));
                    tapeSlot = slot;
                }
            }
            instrDragPart = -1;
            instrDragSlot = -1;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double realX, double realY, double delta) {
        double mx = realX * mouseFactor, my = realY * mouseFactor;
        int steps = delta > 0 ? 1 : -1;
        if (puzzleList) {
            listScroll = Math.max(0, listScroll - steps);
            return true;
        }
        if (dialog != null) {
            return true;
        }
        if (inField(mx, my)) {
            int next = Math.max(0, Math.min(ZOOMS.length - 1, zoom + steps));
            if (next != zoom) {
                // keep the point under the cursor where it is
                double cx = (fx0 + fx1) / 2.0, cy = (fy0 + fy1) / 2.0;
                double k = ZOOMS[next] / (double) ZOOMS[zoom];
                panX = (mx - cx) - (mx - cx - panX) * k;
                panY = (my - cy) - (my - cy - panY) * k;
                zoom = next;
            }
        } else if (mx < leftW && my < fy1) {
            paletteScroll = Math.max(0, paletteScroll - steps);
        } else if (my >= fy1) {
            if (mx < tapeLeft + 4 + TAPE_LABEL_W) {
                tapeRowScroll = Math.max(0, tapeRowScroll - steps);
            } else {
                tapeScroll = Math.max(0, tapeScroll - steps * 3);
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (puzzleList && c >= ' ' && search.length() < 40) {
            search += c;
            listScroll = 0;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (puzzleList) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                puzzleList = false;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
            }
            return true;
        }
        if (dialog != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                dialog = null;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (held != null) {
                held = null;
            } else if (run != null) {
                stopRun();
            } else {
                tryClose();
            }
            return true;
        }
        if (!loaded) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE) {
            togglePlay();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PERIOD) {
            stepRun();
            return true;
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Z) {
            if (hasShiftDown()) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (!canEdit()) {
            return true;
        }
        if (focus == Focus.TAPE && part(selectedPart) != null) {
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> tapeSlot = Math.max(0, tapeSlot - 1);
                case GLFW.GLFW_KEY_RIGHT -> tapeSlot++;
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                    List<Integer> arms = armParts();
                    int row = arms.indexOf(selectedPart) + (key == GLFW.GLFW_KEY_UP ? -1 : 1);
                    if (row >= 0 && row < arms.size()) {
                        selectedPart = arms.get(row);
                        tapeRowScroll = Math.max(Math.min(tapeRowScroll, row), row - tapeRows + 1);
                    }
                }
                case GLFW.GLFW_KEY_DELETE -> deleteInstruction();
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    tapeSlot = Math.max(0, tapeSlot - 1);
                    deleteInstruction();
                }
                default -> {
                    Puzzle p = puzzle();
                    for (Map.Entry<String, Character> e : HOTKEYS.entrySet()) {
                        Id id = Id.of(e.getKey());
                        if (key == e.getValue() && !p.forbiddenInstructionsOrEmpty().contains(id)
                                && ModCore.REG.instructions.contains(id)) {
                            setInstruction(id);
                        }
                    }
                }
            }
            keepCursorVisible();
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_Q -> rotateSelected(1);
            case GLFW.GLFW_KEY_E -> rotateSelected(-1);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD, GLFW.GLFW_KEY_RIGHT_BRACKET -> resizeSelected(1);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT, GLFW.GLFW_KEY_SLASH -> resizeSelected(-1);
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> removeSelected();
            default -> {
            }
        }
        return true;
    }
}
