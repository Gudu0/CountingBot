package org.gudu0.countingbot.disconnects;

import org.gudu0.countingbot.util.JsonStore;

import java.nio.file.Path;

@SuppressWarnings("unused")
public class DisconnectStore {
    public final Object lock;
    private final JsonStore<DisconnectDailyState> store;

    public DisconnectStore(Path path) {
        this.store = new JsonStore<>(path, DisconnectDailyState.class, DisconnectDailyState::new, "disconnects.json");
        this.lock = store.lock;
    }

    public DisconnectDailyState state() { return store.get(); }

    public void markDirty() { store.markDirty(); }
    public void startAutoFlush(long periodSeconds) { store.startAutoFlush(periodSeconds); }
    public void tryFlush() { store.tryFlush(); }

    // keep old name
    public void save() throws Exception {
        store.markDirty();     // important: since disconnect store previously always wrote
        store.flushNow();
    }
}
