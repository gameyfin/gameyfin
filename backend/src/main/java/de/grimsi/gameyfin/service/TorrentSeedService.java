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

public class TorrentSeedService{

    private CommunicationManager comm;

    public TorrentSeedService() {
        InetAddress seedAddress = new InetSocketAddress(0).getAddress();

        ExecutorService workingExecutor = Executors.newFixedThreadPool(8);
        ExecutorService validatorExecutor = Executors.newFixedThreadPool(8);
        this.comm = new CommunicationManager(workingExecutor, validatorExecutor);

        try {
            this.comm.start(seedAddress);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addTorrentToSeed(Path torrentPath, String gamePath){
        try {
            String torrentHash = this.comm.addTorrent(torrentPath.toString(), gamePath, FullyPieceStorageFactory.INSTANCE).getHexInfoHash();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
