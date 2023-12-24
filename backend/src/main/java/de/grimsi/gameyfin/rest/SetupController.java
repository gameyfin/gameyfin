package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.dto.SetupDto;
import de.grimsi.gameyfin.service.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    @GetMapping("/status")
    public boolean isSetupComplete() {
        return setupService.isSetupCompleted();
    }

    @PostMapping("/complete")
    public void completeSetup(SetupDto setupDto) {
        setupService.completeSetup(setupDto);
    }
}
