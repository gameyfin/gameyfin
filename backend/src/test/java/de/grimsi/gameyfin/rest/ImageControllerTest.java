package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.service.DownloadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;
import static org.mockito.Mockito.*;
import static reactor.core.publisher.Mono.when;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    @InjectMocks
    private ImageController target;

    @Mock
    private DownloadService downloadServiceMock;

    @Test
    void getImage() {
        byte[] content = "content".getBytes();
        Resource resource = new ByteArrayResource(content);
        String input = "imageId";

        doReturn(resource).when(downloadServiceMock).sendImageToClient(input);

        ResponseEntity<Resource> result = target.getImage(input);

        verify(downloadServiceMock, times(1)).sendImageToClient(input);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(resource);
    }
}
