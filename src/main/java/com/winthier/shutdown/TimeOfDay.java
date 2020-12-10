package com.winthier.shutdown;

import lombok.Value;

@Value
public final class TimeOfDay {
    public final int hour;
    public final int minute;

    public static TimeOfDay parse(String in) { // throws RuntimeException!
        String[] toks = in.split(":", 2);
        return new TimeOfDay(Integer.parseInt(toks[0]), Integer.parseInt(toks[1]));
    }

    @Override
    public String toString() {
        return String.format("%02d:%02d", hour, minute);
    }
}
