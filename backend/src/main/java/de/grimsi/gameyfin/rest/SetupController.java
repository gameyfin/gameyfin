package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.dto.SetupDto;
import de.grimsi.gameyfin.service.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public void completeSetup(@RequestBody SetupDto setupDto) {
        setupService.completeSetup(setupDto);
    }
}
