package org.gudu0.countingbot.goals;

import org.gudu0.countingbot.util.JsonStore;

import java.nio.file.Path;

@SuppressWarnings("unused")
public class GoalsStore {
    public final Object lock;
    private final JsonStore<GoalState> store;

    public GoalsStore(Path path) {
        this.store = new JsonStore<>(path, GoalState.class, GoalState::new, "goals.json");
        this.lock = store.lock;
    }

    public GoalState state() { return store.get(); }
    public void markDirty() { store.markDirty(); }
    public void startAutoFlush(long periodSeconds) { store.startAutoFlush(periodSeconds); }
    public void tryFlush() { store.tryFlush(); }

    public void flushNow() throws java.io.IOException { store.flushNow(); }
}
