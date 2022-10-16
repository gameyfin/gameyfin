package de.grimsi.gameyfin.igdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.config.WebClientConfig;
import de.grimsi.gameyfin.igdb.dto.TwitchOAuthTokenDto;
import de.grimsi.gameyfin.mapper.GameMapper;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IgdbWrapperTest {

    private static final MockWebServer igdbApiMock = new MockWebServer();
    private static final MockWebServer twitchApiMock = new MockWebServer();
    private static final EasyRandom easyRandom = new EasyRandom();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static IgdbWrapper target;

    @BeforeAll
    static void setup() throws IOException {
        WebClientConfig webClientConfigMock = mock(WebClientConfig.class);
        GameMapper gameMapperMock = mock(GameMapper.class);

        target = new IgdbWrapper(WebClient.builder(), webClientConfigMock, gameMapperMock);

        igdbApiMock.start();
        twitchApiMock.start();

        ReflectionTestUtils.setField(target, "clientId", "client_id_value");
        ReflectionTestUtils.setField(target, "clientSecret", "client_secret_value");
        ReflectionTestUtils.setField(target, "igdbApiBaseUrl", "http://localhost:%s".formatted(igdbApiMock.getPort()));
        ReflectionTestUtils.setField(target, "twitchAuthUrl", "http://localhost:%s/oauth2/token".formatted(twitchApiMock.getPort()));

        when(webClientConfigMock.getIgdbConcurrencyLimiter()).thenReturn(Bulkhead.of("test_bulkhead", BulkheadConfig.ofDefaults()));
        when(webClientConfigMock.getIgdbRateLimiter()).thenReturn(RateLimiter.of("test_ratelimiter", RateLimiterConfig.ofDefaults()));
    }

    @AfterAll
    static void tearDown() throws IOException {
        igdbApiMock.shutdown();
        twitchApiMock.shutdown();
    }

    @Test
    @Order(0)
    void init() throws JsonProcessingException, InterruptedException {
        TwitchOAuthTokenDto mockToken = easyRandom.nextObject(TwitchOAuthTokenDto.class);

        twitchApiMock.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockToken))
                .addHeader("Content-Type", "application/json"));

        target.init();

        RecordedRequest r = twitchApiMock.takeRequest();
        assertThat(r.getRequestUrl()).isNotNull();
        assertThat(r.getRequestUrl().encodedPath()).isEqualTo("/oauth2/token");
        assertThat(r.getRequestUrl().queryParameter("client_id")).isEqualTo("client_id_value");
        assertThat(r.getRequestUrl().queryParameter("client_secret")).isEqualTo("client_secret_value");
        assertThat(r.getRequestUrl().queryParameter("grant_type")).isEqualTo("client_credentials");
    }

    @Test
    void getGameById() throws InterruptedException {
        //Igdb.GameResult gameResult = easyRandom.nextObject(Igdb.GameResult.class);
        Igdb.GameResult gameResult = Igdb.GameResult.newBuilder()
                .addGames(Igdb.Game.newBuilder().setId(easyRandom.nextLong()))
                .build();

        Long gameId = gameResult.getGames(0).getId();

        igdbApiMock.enqueue(new MockResponse()
                .setBody(toBuffer(gameResult))
                .setHeader("Content-Type", "application/protobuf")
        );

        Optional<Igdb.Game> gameOptional = target.getGameById(gameId);

        assertThat(gameOptional).isPresent();

        Igdb.Game game = gameOptional.get();

        assertThat(game.getId()).isEqualTo(gameId);

        RecordedRequest r = igdbApiMock.takeRequest();
        assertThat(r.getRequestUrl()).isNotNull();
        assertThat(r.getRequestUrl().encodedPath()).isEqualTo("/%s".formatted(IgdbApiProperties.ENPOINT_GAMES_PROTOBUF));

        String expectedQuery = "fields %s; where id = %d; limit 1;".formatted(IgdbApiProperties.GAME_QUERY_FIELDS_STRING, gameId);

        assertThat(r.getBody().readUtf8()).isEqualTo(expectedQuery);
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

    private static Buffer toBuffer(Message input) {
        Buffer b = new Buffer();
        b.write(input.toByteArray());
        return b;
    }
}
