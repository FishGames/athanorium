package io.github.fishgames.athanorium.client;

import net.minecraft.client.Minecraft;
import io.github.fishgames.athanorium.ModCore;
import io.github.fishgames.athanorium.base.network.Net;
import io.github.fishgames.athanorium.core.json.DataException;
import io.github.fishgames.athanorium.core.slot.ProgramSlot;
import io.github.fishgames.athanorium.core.slot.SlotCodec;
import io.github.fishgames.athanorium.core.slot.SlotContent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Client side of the network: receives sync, open and status messages and sends program data and commands. */
public final class ClientNet {
    private static final Net.Reassembler REASSEMBLER = new Net.Reassembler(64 * 1024 * 1024);

    private ClientNet() {
    }

    /** Called by the loader for every frame from the server, on the client main thread. */
    public static void receive(byte[] frame) {
        byte[] message = REASSEMBLER.feed(frame);
        if (message == null || message.length == 0) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            int type = in.readByte();
            switch (type) {
                case Net.S_SYNC -> {
                    ClientData.readSync(in);
                    if (Minecraft.getInstance().screen instanceof PuzzleScreen screen) {
                        screen.onLibraryChanged();
                    }
                }
                case Net.S_OPEN -> {
                    int containerId = in.readInt();
                    byte[] slotBytes = new byte[in.readInt()];
                    in.readFully(slotBytes);
                    ProgramSlot slot = SlotCodec.decode(slotBytes);
                    SlotStatus status = SlotStatus.read(in);
                    if (Minecraft.getInstance().screen instanceof PuzzleScreen screen
                            && screen.getMenu().containerId == containerId) {
                        screen.onOpenData(slot, status);
                    }
                }
                case Net.S_STATUS -> {
                    int containerId = in.readInt();
                    SlotStatus status = SlotStatus.read(in);
                    if (Minecraft.getInstance().screen instanceof PuzzleScreen screen
                            && screen.getMenu().containerId == containerId) {
                        screen.onStatus(status);
                    }
                }
                default -> {
                }
            }
        } catch (IOException | DataException | IllegalArgumentException e) {
            ModCore.LOGGER.warn("Malformed message from the server: {}", e.getMessage());
        }
    }

    public static void sendProgram(int containerId, int seq, SlotContent content, boolean submit, int cycles) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(submit ? Net.C_SUBMIT : Net.C_INTERIM);
            out.writeInt(containerId);
            out.writeInt(seq);
            SlotCodec.writeContent(out, content);
            if (submit) {
                out.writeInt(cycles);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Net.sendToServer(bytes.toByteArray());
    }

    public static void sendCommand(int containerId, int type) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(type);
            out.writeInt(containerId);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Net.sendToServer(bytes.toByteArray());
    }
}
