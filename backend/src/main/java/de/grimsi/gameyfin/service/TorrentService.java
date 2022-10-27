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

    @Value("${gameyfin.trackerhostname}")
    private String trackerHostname;

    @Value("${gameyfin.trackerport}")
    private int trackerPort;

    public void createTorrentFolder() throws IOException {
        Files.createDirectories(Path.of(torrentFolderPath));
    }

    public static String getAnnounceURL(){
        if(tracker != null)
            return tracker.getAnnounceUrl();
        return "";
    }

    public static boolean isTrackerEnabled(){
        if(tracker != null)
            return true;
        return false;
    }

    public static boolean announceTorrent(File torrentFile) {
        if (tracker == null) {
            log.info("Failed to announce torrent file, tracker is not started");
            return false;
        } else {
            try {
                tracker.announce(TrackedTorrent.load(torrentFile));
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (torrentFolderPath.equals("")) {
            log.info("No torrent path set, not starting with torrent support");
            return;
        }
        try {
            this.createTorrentFolder();
        } catch (IOException e) {

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
                announceTorrent(f);
            }
            //Finally start the tracker
            tracker.start(true);
        } catch (IOException e) {
            log.error("Failed to start internal torrent tracker");
        }
    }
}
