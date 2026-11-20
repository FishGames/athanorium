package io.github.fishgames.athanorium.client;

import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.slot.Validity;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/** The status message of the server: both states of the slot plus an optional message. */
public record SlotStatus(int seq, State saved, State pending, int messageKind, String messageKey, String messageDetail) {
    public record State(int revision, boolean confirmed, Validity validity, String reasonKey, String reasonDetail,
                        Map<Id, Long> metrics) {
    }

    /** The state the editor works on. */
    public State current() {
        return pending != null ? pending : saved;
    }

    static SlotStatus read(DataInputStream in) throws IOException {
        int seq = in.readInt();
        State saved = readState(in);
        State pending = in.readBoolean() ? readState(in) : null;
        return new SlotStatus(seq, saved, pending, in.readByte(), in.readUTF(), in.readUTF());
    }

    private static State readState(DataInputStream in) throws IOException {
        int revision = in.readInt();
        boolean confirmed = in.readBoolean();
        Validity validity = Validity.values()[Math.floorMod(in.readByte(), Validity.values().length)];
        String key = in.readUTF();
        String detail = in.readUTF();
        Map<Id, Long> metrics = new TreeMap<>();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            metrics.put(Id.parse(in.readUTF()), in.readLong());
        }
        return new State(revision, confirmed, validity, key, detail, metrics);
    }
}
