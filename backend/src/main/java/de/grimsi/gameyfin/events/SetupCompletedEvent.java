package de.grimsi.gameyfin.events;

import org.springframework.context.ApplicationEvent;

public class SetupCompletedEvent extends ApplicationEvent {
    public SetupCompletedEvent(Object source) {
        super(source);
    }
}
