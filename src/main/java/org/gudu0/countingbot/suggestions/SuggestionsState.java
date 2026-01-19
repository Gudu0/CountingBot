package org.gudu0.countingbot.suggestions;

import java.util.ArrayList;
import java.util.List;

public class SuggestionsState {
    public long nextId = 1;
    @SuppressWarnings("CanBeFinal")
    public List<SuggestionEntry> suggestions = new ArrayList<>();
}
