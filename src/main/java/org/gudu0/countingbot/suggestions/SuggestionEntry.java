package org.gudu0.countingbot.suggestions;

@SuppressWarnings("unused")
public class SuggestionEntry {
    public long id;
    public long guildId;
    public long authorId;
    public String authorTag;      // optional snapshot
    public String text;
    public long createdAtMillis;

    public SuggestionEntry() {}

    public SuggestionEntry(long id, long guildId, long authorId, String authorTag, String text, long createdAtMillis) {
        this.id = id;
        this.guildId = guildId;
        this.authorId = authorId;
        this.authorTag = authorTag;
        this.text = text;
        this.createdAtMillis = createdAtMillis;
    }
}
