package de.grimsi.gameyfin.resources;

public enum PublicResources {
    GAMEYFIN_LOGO("public/images/Logo.svg");

    public final String path;

    PublicResources(String path) {
        this.path = path;
    }
}