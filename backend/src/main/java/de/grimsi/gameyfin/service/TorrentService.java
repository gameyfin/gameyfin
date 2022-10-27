package de.grimsi.gameyfin.service;

import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@Service
public class TorrentService implements ApplicationListener<ApplicationReadyEvent> {

    private static Tracker tracker;

    @Value("${gameyfin.torrent}")
    private String torrentFolderPath;

    @Value("${gameyfin.trackerport}")
    private int trackerPort;

    public void createTorrentFolder() throws IOException {
        Files.createDirectories(Path.of(torrentFolderPath));
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if(torrentFolderPath.equals("")){
            log.info("No torrent path set, not starting with torrent support");
            return;
        }
        log.info("Loading created torrents from {}", torrentFolderPath);
        try {
            tracker = new Tracker(trackerPort);
            //Load every .torrent file contained in the folder
            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".torrent");
                }
            };
            for (File f : new File(torrentFolderPath).listFiles(filter)) {
                log.info("Loaded torrentfile {}", f.toString());
                tracker.announce(TrackedTorrent.load(f));
            }
            //Finally start the tracker
            tracker.start(true);
        } catch (IOException e) {
            log.error("Failed to start internal torrent tracker");
        }
    }
}
