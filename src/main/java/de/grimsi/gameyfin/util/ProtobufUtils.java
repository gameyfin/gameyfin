package de.grimsi.gameyfin.util;

import com.google.protobuf.Timestamp;

import java.time.Instant;

public class ProtobufUtils {
    public static Instant toInstant(Timestamp t) {
        return Instant.ofEpochSecond(t.getSeconds(), t.getNanos());
    }
}
