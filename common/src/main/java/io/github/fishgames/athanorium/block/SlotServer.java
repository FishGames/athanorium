package io.github.fishgames.athanorium.block;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import io.github.fishgames.athanorium.ModCore;
import io.github.fishgames.athanorium.base.config.ModConfig;
import io.github.fishgames.athanorium.base.network.Net;
import io.github.fishgames.athanorium.core.hex.Hex;
import io.github.fishgames.athanorium.core.hex.HexField;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.json.DataException;
import io.github.fishgames.athanorium.core.program.Part;
import io.github.fishgames.athanorium.core.program.Program;
import io.github.fishgames.athanorium.core.program.ProgramCodec;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.score.PartValues;
import io.github.fishgames.athanorium.core.slot.CalcResult;
import io.github.fishgames.athanorium.core.slot.Computation;
import io.github.fishgames.athanorium.core.slot.Library;
import io.github.fishgames.athanorium.core.slot.ProgramSlot;
import io.github.fishgames.athanorium.core.slot.SlotChecker;
import io.github.fishgames.athanorium.core.slot.SlotCodec;
import io.github.fishgames.athanorium.core.slot.SlotContent;
import io.github.fishgames.athanorium.core.slot.SlotState;
import io.github.fishgames.athanorium.core.slot.Validity;
import io.github.fishgames.athanorium.data.ServerData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Server side of the program slots: the server decides, the client only shows. Handles opening with
 * exclusive access, interim states, submissions, save and discard, and the re-check after loading and /reload.
 * Everything in here runs on the server main thread; packets of one player are handled in the order received.
 */
public final class SlotServer {
    public static final int MSG_NONE = 0;
    public static final int MSG_INFO = 1;
    public static final int MSG_ERROR = 2;

    /** Per-player network state: frame buffer, rate limit and a state held back by the rate limit. */
    private static final class Session {
        Net.Reassembler reassembler;
        final ArrayDeque<Long> programTimes = new ArrayDeque<>();
        SlotContent held;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static SimWorker worker;

    private SlotServer() {
    }

    // lifecycle

    public static void serverTick(MinecraftServer server) {
        if (worker != null) {
            worker.drain();
        }
    }

    public static void onServerStopping() {
        if (worker != null) {
            worker.shutdown();
            worker = null;
        }
        SESSIONS.clear();
    }

    private static SimWorker worker() {
        if (worker == null) {
            worker = new SimWorker();
        }
        return worker;
    }

    // opening and closing

    /** Creative mode or operator rights (level 2). */
    public static boolean mayUse(Player player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    public static void tryOpen(ServerPlayer player, DeveloperBlockEntity be) {
        if (!mayUse(player)) {
            player.displayClientMessage(Component.translatable("message.athanorium.no_permission"), true);
            return;
        }
        ProgramSlot slot = be.slot();
        UUID editor = slot.editor();
        if (editor != null && !editor.equals(player.getUUID())) {
            ServerPlayer other = player.server.getPlayerList().getPlayer(editor);
            if (other != null && other.containerMenu instanceof DeveloperMenu m && m.blockEntity() == be) {
                player.displayClientMessage(Component.translatable("message.athanorium.in_use",
                        other.getDisplayName()), true);
                return;
            }
        }
        slot.normalizeStages(ServerData.config().stages());
        OptionalInt id = player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new DeveloperMenu(containerId, inventory, be),
                Component.translatable("block.athanorium.developer_block")));
        if (id.isEmpty()) {
            return;
        }
        slot.setEditor(player.getUUID());
        sendOpen(player, be, id.getAsInt());
    }

    /** The one place where the editing lock is released. A state held back by the rate limit is taken over first. */
    public static void onMenuClosed(ServerPlayer player, DeveloperBlockEntity be) {
        Session session = SESSIONS.remove(player.getUUID());
        ProgramSlot slot = be.slot();
        if (!player.getUUID().equals(slot.editor())) {
            return;
        }
        if (session != null && session.held != null && checkRanges(session.held) == null) {
            slot.applyInterim(session.held);
            be.setChanged();
        }
        slot.setEditor(null);
    }

    // incoming messages

    /** Called by the loader for every frame from a client, on the server main thread. */
    public static void receive(ServerPlayer player, byte[] frame) {
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), u -> new Session());
        if (session.reassembler == null) {
            session.reassembler = new Net.Reassembler(ServerData.config().maxTransferBytes());
        }
        byte[] message = session.reassembler.feed(frame);
        if (message == null || message.length == 0) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            int type = in.readByte();
            int containerId = in.readInt();
            if (!(player.containerMenu instanceof DeveloperMenu menu) || menu.containerId != containerId
                    || menu.blockEntity() == null || !player.getUUID().equals(menu.blockEntity().slot().editor())) {
                return; // the sender is not the current editor
            }
            DeveloperBlockEntity be = menu.blockEntity();
            switch (type) {
                case Net.C_INTERIM, Net.C_SUBMIT -> {
                    int seq = in.readInt();
                    ModConfig cfg = ServerData.config();
                    SlotContent content = SlotCodec.readContent(in,
                            new ProgramCodec.Limits(cfg.maxParts(), cfg.maxInstructions()));
                    int cycles = type == Net.C_SUBMIT ? in.readInt() : -1;
                    onProgram(player, session, be, containerId, seq, content, type == Net.C_SUBMIT, cycles);
                }
                case Net.C_SAVE -> {
                    be.slot().save();
                    be.setChanged();
                    recheck(be);
                    sendStatus(player, be, containerId, 0, MSG_INFO, "slot.saved", "");
                }
                case Net.C_DISCARD -> {
                    be.slot().discard();
                    be.setChanged();
                    sendOpen(player, be, containerId);
                }
                default -> {
                }
            }
        } catch (DataException e) {
            ModCore.LOGGER.warn("Rejected program data from {}: {}", player.getGameProfile().getName(), e.getMessage());
            if (player.containerMenu instanceof DeveloperMenu menu && menu.blockEntity() != null) {
                sendStatus(player, menu.blockEntity(), menu.containerId, 0, MSG_ERROR, "slot.reject.malformed",
                        e.getMessage());
            }
        } catch (IOException e) {
            ModCore.LOGGER.warn("Malformed message from {}", player.getGameProfile().getName());
        }
    }

    private static void onProgram(ServerPlayer player, Session session, DeveloperBlockEntity be, int containerId,
                                  int seq, SlotContent content, boolean submit, int cycles) {
        // rate limit, counted per complete program
        long now = System.currentTimeMillis();
        while (!session.programTimes.isEmpty() && now - session.programTimes.peekFirst() > 10_000) {
            session.programTimes.pollFirst();
        }
        if (session.programTimes.size() >= ServerData.config().programsPerTenSeconds()) {
            session.held = content; // never lost: taken over when the menu really closes
            sendStatus(player, be, containerId, 0, MSG_ERROR, "slot.reject.rate_limited", "");
            return;
        }
        session.programTimes.addLast(now);
        session.held = null;

        String problem = checkRanges(content);
        if (problem != null) {
            sendStatus(player, be, containerId, 0, MSG_ERROR, problem, "");
            return;
        }
        ProgramSlot slot = be.slot();
        SlotState state = slot.applyInterim(content);
        be.setChanged();
        if (!submit || state.confirmed()) {
            sendStatus(player, be, containerId, seq, MSG_NONE, "", "");
            return;
        }
        Library library = ServerData.library();
        Puzzle puzzle = library.puzzle(content.puzzle());
        if (puzzle == null) {
            sendStatus(player, be, containerId, seq, MSG_ERROR, SlotChecker.PUZZLE_MISSING, "");
            return;
        }
        if (cycles < 0 || cycles > puzzle.effectiveMaxCycles()) {
            // reported cycle counts above max_cycles are rejected without computing
            sendStatus(player, be, containerId, seq, MSG_ERROR, "slot.reject.too_many_cycles",
                    Integer.toString(puzzle.effectiveMaxCycles()));
            return;
        }
        int revision = state.revision();
        int generation = library.generation();
        HexField field = SlotChecker.field(content, ServerData.config().stages());
        Program program = content.program();
        PartValues values = library.values();
        UUID playerId = player.getUUID();
        be.computing.add(revision);
        sendStatus(player, be, containerId, seq, MSG_NONE, "", "");
        worker().enqueue(
                () -> new Computation(puzzle, program, field, ModCore.REG, values, cycles, cycles),
                result -> {
                    be.computing.remove(revision);
                    ServerPlayer p = player.server.getPlayerList().getPlayer(playerId);
                    boolean open = p != null && p.containerMenu instanceof DeveloperMenu m && m.blockEntity() == be;
                    if (be.isRemoved()) {
                        return;
                    }
                    int kind;
                    String key;
                    String detail = "";
                    if (ServerData.generation() != generation) {
                        // a /reload happened meanwhile: the result is dropped, the block is re-checked anyway
                        kind = MSG_ERROR;
                        key = "slot.reject.outdated";
                    } else if (!result.passed()) {
                        kind = MSG_ERROR;
                        key = result.failKey();
                        detail = result.failDetail();
                    } else if (slot.confirm(revision, result)) {
                        be.setChanged();
                        kind = MSG_INFO;
                        key = "slot.confirmed";
                    } else {
                        return; // result for an older revision: dropped, even though it passed
                    }
                    if (open) {
                        sendStatus(p, be, p.containerMenu.containerId, seq, kind, key, detail);
                    }
                }, true);
    }

    /** Checks of an interim state: value ranges only, not solvability. Returns a message key or null. */
    private static String checkRanges(SlotContent content) {
        if (content.puzzle() != null && ServerData.library().puzzle(content.puzzle()) == null) {
            return SlotChecker.PUZZLE_MISSING;
        }
        if (content.stage() < 1 || content.stage() > ServerData.config().stages()) {
            return "slot.reject.stage_out_of_range";
        }
        for (Part p : content.program().parts()) {
            if (outOfLimit(p.pos())) {
                return "program.error.coordinate_limit";
            }
            for (Hex h : p.trackHexes()) {
                if (outOfLimit(p.pos().plus(h))) {
                    return "program.error.coordinate_limit";
                }
            }
        }
        return null;
    }

    private static boolean outOfLimit(Hex h) {
        return Math.abs(h.q()) > HexField.COORDINATE_LIMIT || Math.abs(h.r()) > HexField.COORDINATE_LIMIT;
    }

    // re-check

    /**
     * Checks the confirmed states of a block against the current datapack data and starts a re-computation
     * where the solvability checksum changed. Until the new result exists the state is "checking".
     */
    public static void recheck(DeveloperBlockEntity be) {
        ProgramSlot slot = be.slot();
        recheck(be, slot.saved());
        if (slot.pending() != null) {
            recheck(be, slot.pending());
        }
        pushStatus(be);
    }

    private static void recheck(DeveloperBlockEntity be, SlotState state) {
        Library library = ServerData.library();
        CalcResult before = state.result();
        SlotChecker.Check check = SlotChecker.check(state, library, ModCore.REG, ServerData.config().stages());
        if (state.result() != before) {
            be.setChanged();
        }
        int revision = state.revision();
        if (!check.needsComputation() || be.computing.contains(revision)) {
            return;
        }
        Puzzle puzzle = library.puzzle(state.content().puzzle());
        HexField field = SlotChecker.field(state.content(), ServerData.config().stages());
        Program program = state.content().program();
        PartValues values = library.values();
        int generation = library.generation();
        be.computing.add(revision);
        worker().enqueue(
                () -> new Computation(puzzle, program, field, ModCore.REG, values, puzzle.effectiveMaxCycles(), -1),
                result -> {
                    be.computing.remove(revision);
                    if (be.isRemoved()) {
                        return;
                    }
                    if (ServerData.generation() != generation) {
                        recheck(be); // dropped; queue the block again with the new data
                        return;
                    }
                    SlotState target = be.slot().byRevision(revision);
                    if (target != null) {
                        target.setResult(result); // replaces the old result even if it failed
                        be.setChanged();
                    }
                    pushStatus(be);
                }, false);
    }

    /** Sends the current status to the editor of the block, if there is one. */
    private static void pushStatus(DeveloperBlockEntity be) {
        UUID editor = be.slot().editor();
        if (editor == null || be.getLevel() == null || be.getLevel().getServer() == null) {
            return;
        }
        ServerPlayer p = be.getLevel().getServer().getPlayerList().getPlayer(editor);
        if (p != null && p.containerMenu instanceof DeveloperMenu m && m.blockEntity() == be) {
            sendStatus(p, be, m.containerId, 0, MSG_NONE, "", "");
        }
    }

    // outgoing messages

    private static void sendOpen(ServerPlayer player, DeveloperBlockEntity be, int containerId) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(Net.S_OPEN);
            out.writeInt(containerId);
            byte[] slot = SlotCodec.encode(be.slot());
            out.writeInt(slot.length);
            out.write(slot);
            writeStatus(out, be, 0, MSG_NONE, "", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Net.sendToPlayer(player, bytes.toByteArray());
    }

    private static void sendStatus(ServerPlayer player, DeveloperBlockEntity be, int containerId, int seq, int kind,
                                   String key, String detail) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(Net.S_STATUS);
            out.writeInt(containerId);
            writeStatus(out, be, seq, kind, key, detail);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Net.sendToPlayer(player, bytes.toByteArray());
    }

    /**
     * The status block: for the saved program and the unsaved change each revision, confirmation, validity
     * with reason and metrics, plus the echo of the client's sequence number and an optional message.
     */
    private static void writeStatus(DataOutputStream out, DeveloperBlockEntity be, int seq, int kind, String key,
                                    String detail) throws IOException {
        ProgramSlot slot = be.slot();
        out.writeInt(seq);
        writeStateStatus(out, be, slot.saved());
        out.writeBoolean(slot.pending() != null);
        if (slot.pending() != null) {
            writeStateStatus(out, be, slot.pending());
        }
        out.writeByte(kind);
        out.writeUTF(key);
        out.writeUTF(detail.length() > 2000 ? detail.substring(0, 2000) : detail);
    }

    private static void writeStateStatus(DataOutputStream out, DeveloperBlockEntity be, SlotState state)
            throws IOException {
        SlotChecker.Check check = SlotChecker.check(state, ServerData.library(), ModCore.REG,
                ServerData.config().stages());
        Validity validity = be.computing.contains(state.revision()) && state.confirmed() ? Validity.CHECKING
                : check.validity();
        out.writeInt(state.revision());
        out.writeBoolean(state.confirmed());
        out.writeByte(validity.ordinal());
        out.writeUTF(check.reasonKey());
        out.writeUTF(check.reasonDetail());
        CalcResult r = state.result();
        Map<Id, Long> metrics = r != null && r.passed() ? r.metrics() : Map.of();
        out.writeInt(metrics.size());
        for (Map.Entry<Id, Long> e : metrics.entrySet()) {
            out.writeUTF(e.getKey().toString());
            out.writeLong(e.getValue());
        }
    }
}
