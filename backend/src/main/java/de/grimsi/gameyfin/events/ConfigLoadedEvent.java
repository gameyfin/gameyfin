package de.grimsi.gameyfin.events;

import org.springframework.context.ApplicationEvent;

public class ConfigLoadedEvent extends ApplicationEvent {
    public ConfigLoadedEvent(Object source) {
        super(source);
    }
}
