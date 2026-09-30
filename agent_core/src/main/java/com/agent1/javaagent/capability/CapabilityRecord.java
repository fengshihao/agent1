package com.agent1.javaagent.capability;

/** 能力索引一条记录（与 seed JSONL / SQLite 行对应）。 */
public record CapabilityRecord(
    String id,
    String kind,
    String title,
    String summary,
    String tags,
    String platforms,
    String entry,
    String docPath,
    String docAnchor,
    String requiresJson,
    String source,
    double weight
) {
    public CapabilityRecord {
        id = normalize(id);
        kind = normalize(kind);
        title = normalize(title);
        summary = normalize(summary);
        tags = tags == null ? "" : tags.trim();
        platforms = platforms == null || platforms.isBlank() ? "any" : platforms.trim();
        entry = normalize(entry);
        docPath = normalize(docPath);
        docAnchor = normalize(docAnchor);
        requiresJson = requiresJson == null ? "" : requiresJson.trim();
        source = normalize(source);
        if (weight <= 0) {
            weight = 1.0;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
