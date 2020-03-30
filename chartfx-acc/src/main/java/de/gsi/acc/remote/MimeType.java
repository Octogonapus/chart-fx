package de.gsi.acc.remote;

public enum MimeType {
    PLAINTEXT("text/plain"),
    BINARY("application/octet-stream"),
    JSON("application/json"),
    XML("text/xml"),
    HTML("text/html"),
    PNG("image/png"),
    EVENT_STREAM("text/event-stream"),
    UNKNOWN("application/octet-stream");

    private final String typeDef;

    private MimeType(String definition) {
        typeDef = definition;
    }

    @Override
    public String toString() {
        return typeDef;
    }

    public static MimeType parse(final String text) {
        if (text == null || text.isEmpty() || text.isBlank()) {
            return UNKNOWN;
        }
        for (MimeType type : MimeType.values()) {
            if (type.toString().equalsIgnoreCase(text)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
