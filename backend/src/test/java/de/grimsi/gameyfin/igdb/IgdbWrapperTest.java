package de.grimsi.gameyfin.igdb;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IgdbWrapperTest {

    private static final MockWebServer igdbApiMock = new MockWebServer();
    private static final MockWebServer twitchApiMock = new MockWebServer();

    private IgdbWrapper target;

    @DynamicPropertySource
    static void setupProperties(DynamicPropertyRegistry registry) {
        registry.add("gameyfin.igdb.api.endpoints.base", () -> "http://localhost:%s".formatted(igdbApiMock.getPort()));
        registry.add("gameyfin.igdb.api.endpoints.auth", () -> "http://localhost:%s".formatted(twitchApiMock.getPort()));
    }

    @BeforeAll
    static void setup() throws IOException {
        igdbApiMock.start();
        twitchApiMock.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        igdbApiMock.shutdown();
        twitchApiMock.shutdown();
    }

    @Test
    void authenticate() {
    }

    @Test
    void getGameById() {
    }

    @Test
    void getGameBySlug() {
    }

    @Test
    void findPossibleMatchingTitles() {
    }

    @Test
    void searchForGameByTitle() {
    }
}
