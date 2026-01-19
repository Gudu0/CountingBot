package org.gudu0.countingbot.stats;

import org.gudu0.countingbot.util.JsonStore;

import java.nio.file.Path;

@SuppressWarnings("unused")
public class StatsStore {
    public final Object lock;
    private final JsonStore<StatsData> store;

    public StatsStore(Path path) {
        this.store = new JsonStore<>(path, StatsData.class, StatsData::new, "stats.json");
        this.lock = store.lock;
    }

    public StatsData data() { return store.get(); }
    public void markDirty() { store.markDirty(); }
    public void startAutoFlush(long periodSeconds) { store.startAutoFlush(periodSeconds); }
    public void tryFlush() { store.tryFlush(); }

    public void flushNow() throws java.io.IOException { store.flushNow(); }
}
