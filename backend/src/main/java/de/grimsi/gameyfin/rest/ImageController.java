package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.service.FilesystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This controller handles functionality for images.
 */
@RestController
@RequestMapping("/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final FilesystemService filesystemService;

    @GetMapping(value = "/{imageId}", produces = MediaType.IMAGE_PNG_VALUE)
    public Resource getCoverImageForGame(@PathVariable String imageId) {
        return filesystemService.getImage(imageId);
    }
}
