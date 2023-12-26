package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.config.properties.ConfigKey;
import de.grimsi.gameyfin.dto.SetupDto;
import de.grimsi.gameyfin.events.SetupCompletedEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SetupService {

    @Getter
    @Setter
    private boolean isSetupCompleted = true;

    // These values have to be set in order for Gameyfin to function properly
    private final List<ConfigKey> obligatoryConfigKeys = List.of(
            ConfigKey.TWITCH_CLIENT_ID,
            ConfigKey.TWITCH_CLIENT_SECRET,
            ConfigKey.GAMEYFIN_ADMIN_USERNAME,
            ConfigKey.GAMEYFIN_ADMIN_PASSWORD);

    private final ConfigService configService;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener(ApplicationReadyEvent.class)
    private void checkGameyfinSetupStatus() {
        List<ConfigKey> missingProperties = new ArrayList<>();

        log.debug("Checking setup status...");

        for (ConfigKey c : obligatoryConfigKeys) {
            if (!configService.hasValue(c)) {
                setSetupCompleted(false);
                missingProperties.add(c);
            }
        }

        if (missingProperties.isEmpty()) {
            eventPublisher.publishEvent(new SetupCompletedEvent(this));
            return;
        }

        log.warn("The following properties have not been configured: {}", missingProperties);
        log.info("Entering setup mode...");
    }

    public void completeSetup(SetupDto setupDto) {
        configService.write(ConfigKey.TWITCH_CLIENT_ID, setupDto.igdbClientSecret());
        configService.write(ConfigKey.TWITCH_CLIENT_ID, setupDto.igdbClientId());
        configService.write(ConfigKey.GAMEYFIN_ADMIN_PASSWORD, setupDto.gameyfinAdminPassword());
        configService.write(ConfigKey.GAMEYFIN_ADMIN_USERNAME, setupDto.gameyfinAdminUsername());
        configService.write(ConfigKey.GAMEYFIN_SOURCES, setupDto.gameyfinLibraryPaths());

        log.info("Gameyfin setup completed, exiting setup mode...");
        eventPublisher.publishEvent(new SetupCompletedEvent(this));
        setSetupCompleted(true);
    }
}
