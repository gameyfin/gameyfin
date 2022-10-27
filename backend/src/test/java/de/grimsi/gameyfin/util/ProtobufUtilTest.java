package de.grimsi.gameyfin.util;

import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProtobufUtilTest {

    @Test
    void toInstant() {
        Timestamp t = Timestamp.newBuilder().setSeconds(1).build();

        Instant i = ProtobufUtil.toInstant(t);

        assertThat(i.getEpochSecond()).isEqualTo(1);
    }
}