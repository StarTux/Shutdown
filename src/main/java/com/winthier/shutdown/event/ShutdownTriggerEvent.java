package com.winthier.shutdown.event;

import com.winthier.shutdown.ShutdownReason;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

@RequiredArgsConstructor
public final class ShutdownTriggerEvent extends Event implements Cancellable {
    /** Required by Event. */
    @Getter private static HandlerList handlerList = new HandlerList();
    @Getter @Setter protected boolean cancelled;
    @Getter protected final ShutdownReason reason;
    @Getter protected String cancelledBy;

    /** Required by Event. */
    @Override public HandlerList getHandlers() {
        return handlerList;
    }

    public void cancelBy(Plugin plugin) {
        this.cancelled = true;
        this.cancelledBy = plugin.getName();
    }
}
