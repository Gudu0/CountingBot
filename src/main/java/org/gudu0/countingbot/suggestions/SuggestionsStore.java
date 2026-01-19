package org.gudu0.countingbot.suggestions;

import org.gudu0.countingbot.util.JsonStore;

import java.nio.file.Path;

@SuppressWarnings("unused")
public class SuggestionsStore {
    public final Object lock;
    private final JsonStore<SuggestionsState> store;

    public SuggestionsStore(Path path) {
        this.store = new JsonStore<>(path, SuggestionsState.class, SuggestionsState::new, "suggestions.json");
        this.lock = store.lock;
    }

    public SuggestionsState state() { return store.get(); }
    public void markDirty() { store.markDirty(); }
    public void startAutoFlush(long periodSeconds) { store.startAutoFlush(periodSeconds); }
    public void tryFlush() { store.tryFlush(); }

    public void flushNow() throws java.io.IOException { store.flushNow(); }
}
