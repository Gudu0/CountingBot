package org.gudu0.countingbot.achievements;

import org.gudu0.countingbot.util.JsonStore;

import java.nio.file.Path;

public class AchievementsStore {
    public final Object lock;
    private final JsonStore<AchievementsState> store;

    public AchievementsStore(Path path) {
        this.store = new JsonStore<>(path, AchievementsState.class, AchievementsState::new, "achievements.json");
        this.lock = store.lock;
    }

    public AchievementsState state() { return store.get(); }
    public void markDirty() { store.markDirty(); }
    public void startAutoFlush(long periodSeconds) { store.startAutoFlush(periodSeconds); }
}
