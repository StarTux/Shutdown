package com.winthier.shutdown;

public enum ShutdownReason {
    MANUAL("Manual shutdown"),
    LAG("Server lag"),
    LOWMEM("Low memory"),
    UPTIME("Uptime too long"),
    EMPTY("Server is empty"),
    SCHEDULED("Scheduled shutdown");

    public final String human;

    ShutdownReason(final String human) {
        this.human = human;
    }
}
