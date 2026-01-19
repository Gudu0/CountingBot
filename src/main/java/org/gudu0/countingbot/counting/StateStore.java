package org.gudu0.countingbot.counting;

import org.gudu0.countingbot.util.JsonStore;

import java.nio.file.Path;

@SuppressWarnings("unused")
public class StateStore {
    public final Object lock;
    private final JsonStore<CountingState> store;

    public StateStore(Path path) {
        this.store = new JsonStore<>(path, CountingState.class, CountingState::new, "state.json");
        this.lock = store.lock;
    }

    public CountingState state() { return store.get(); }
    public void markDirty() { store.markDirty(); }
    public void startAutoFlush(long periodSeconds) { store.startAutoFlush(periodSeconds); }
    public void tryFlush() { store.tryFlush(); }

    public void flushNow() throws java.io.IOException { store.flushNow(); }
}
