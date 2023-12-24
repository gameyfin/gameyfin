package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.config.properties.ConfigKey;
import de.grimsi.gameyfin.dto.SetupDto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SetupService {

    // These values have to be set in order for Gameyfin to function properly
    private final List<ConfigKey> obligatoryConfigKeys = List.of(
            ConfigKey.TWITCH_CLIENT_ID,
            ConfigKey.TWITCH_CLIENT_SECRET,
            ConfigKey.GAMEYFIN_ADMIN_USERNAME,
            ConfigKey.GAMEYFIN_ADMIN_PASSWORD);

    private final ConfigService configService;

    @EventListener(ApplicationReadyEvent.class)
    private void checkGameyfinSetupStatus() {
        if (!isSetupCompleted()) return;

        for (ConfigKey c : obligatoryConfigKeys) {
            if (!configService.hasValue(c)) {
                setSetupCompleted(false);
                break;
            }
        }
    }

    public boolean isSetupCompleted() {
        return configService.readBool(ConfigKey.SETUP_COMPLETED);
    }

    public void setSetupCompleted(boolean isComplete) {
        configService.write(ConfigKey.SETUP_COMPLETED, isComplete);
    }

    public void completeSetup(SetupDto setupDto) {
        configService.write(ConfigKey.TWITCH_CLIENT_ID, setupDto.igdbClientSecret());
        configService.write(ConfigKey.TWITCH_CLIENT_ID, setupDto.igdbClientId());
        configService.write(ConfigKey.GAMEYFIN_ADMIN_PASSWORD, setupDto.gameyfinAdminPassword());
        configService.write(ConfigKey.GAMEYFIN_ADMIN_USERNAME, setupDto.gameyfinAdminUsername());
        configService.write(ConfigKey.GAMEYFIN_SOURCES, setupDto.gameyfinLibraryPaths());

        setSetupCompleted(true);
    }
}
