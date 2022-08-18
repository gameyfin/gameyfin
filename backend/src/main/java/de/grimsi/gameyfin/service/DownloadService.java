package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.exceptions.DownloadAbortedException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static de.grimsi.gameyfin.util.FilenameUtil.getFilenameWithExtension;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadService {

    private final FilesystemService filesystemService;

    public String getDownloadFileName(DetectedGame g) {
        Path path = Path.of(g.getPath());

        if (!path.toFile().isDirectory()) return getFilenameWithExtension(path);
        return getFilenameWithExtension(path) + ".zip";
    }

    public Resource sendImageToClient(String imageId) {
        String filename = "%s.png".formatted(imageId);
        return filesystemService.getFileFromCache(filename);
    }

    public void sendGamefilesToClient(DetectedGame game, OutputStream outputStream) {

        StopWatch stopWatch = new StopWatch();

        log.info("Starting game file download for {}...", game.getTitle());

        stopWatch.start();

        Path path = Path.of(game.getPath());

        try {
            if (path.toFile().isDirectory()) {
                sendGamefilesAsZipToClient(path, outputStream);
            } else {
                sendGamefileToClient(path, outputStream);
            }
        } catch (DownloadAbortedException e) {
            stopWatch.stop();
            log.info("Download of game {} was aborted by client after {} seconds", game.getTitle(), (int) stopWatch.getTotalTimeSeconds());
            return;
        }

        stopWatch.stop();

        log.info("Downloaded game files of {} in {} seconds.", game.getTitle(), (int) stopWatch.getTotalTimeSeconds());
    }

    private void sendGamefileToClient(Path path, OutputStream outputStream) {
        try {
            Files.copy(path, outputStream);
        } catch (ClientAbortException e) {
            // Aborted downloads will be handled gracefully
            throw new DownloadAbortedException();
        } catch (IOException e) {
            log.error("Error while downloading file:", e);
        }
    }

    private void sendGamefilesAsZipToClient(Path path, OutputStream outputStream) {
        ZipOutputStream zos = new ZipOutputStream(outputStream) {{
            def.setLevel(Deflater.NO_COMPRESSION);
        }};

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @SneakyThrows
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    zos.putNextEntry(new ZipEntry(path.relativize(file).toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();

                    return FileVisitResult.CONTINUE;
                }
            });

            zos.close();
        } catch (ClientAbortException e) {
            // Aborted downloads will be handled gracefully
            throw new DownloadAbortedException();
        } catch (IOException e) {
            log.error("Error while zipping files:", e);
        }
    }
}
