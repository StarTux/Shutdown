package com.winthier.shutdown;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum MessageType {
    BOSS_BAR,
    BROADCAST,
    HOUR,
    KICK,
    LATE_LOGIN,
    MINUTE,
    MINUTES,
    SECOND,
    SECONDS,
    SUBTITLE,
    TITLE;

    public final String key;

    MessageType() {
        this.key = Stream.of(name().split("_"))
            .map(s -> s.substring(0, 1) + s.substring(1).toLowerCase())
            .collect(Collectors.joining(""));
    }
}
