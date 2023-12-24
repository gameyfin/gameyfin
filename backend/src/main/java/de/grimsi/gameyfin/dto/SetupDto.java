package de.grimsi.gameyfin.dto;

import java.util.ArrayList;

public record SetupDto(
        String igdbClientId,
        String igdbClientSecret,
        String gameyfinAdminUsername,
        String gameyfinAdminPassword,
        // use ArrayList instead of List because it implements the Serializable interface
        ArrayList<String> gameyfinLibraryPaths
) {
}
