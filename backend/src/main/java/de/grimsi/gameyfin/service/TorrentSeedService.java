package de.grimsi.gameyfin.service;

import com.turn.ttorrent.client.CommunicationManager;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.SimpleClient;
import com.turn.ttorrent.client.peer.SharingPeer;
import com.turn.ttorrent.client.storage.FullyPieceStorageFactory;
import org.checkerframework.checker.units.qual.C;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TorrentSeedService implements Runnable {

    private Path torrentPath;
    private String gamePath;

    public TorrentSeedService(Path torrentPath, String gamePath) {
        this.torrentPath = torrentPath;
        this.gamePath = gamePath;
    }

    public void run() {
        InetAddress seedAddress = new InetSocketAddress(0).getAddress();

        ExecutorService workingExecutor = Executors.newFixedThreadPool(8);
        ExecutorService validatorExecutor = Executors.newFixedThreadPool(8);
        CommunicationManager cm = new CommunicationManager(workingExecutor, validatorExecutor);
        try {
            cm.start(seedAddress);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            cm.addTorrent(torrentPath.toString(), gamePath, FullyPieceStorageFactory.INSTANCE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        while (true) {
            for (SharedTorrent torrent : cm.getTorrents()) {
                for (SharingPeer peer : cm.getPeersForTorrent(torrent.getHexInfoHash())) {
                    System.out.println(peer.getIp());
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
