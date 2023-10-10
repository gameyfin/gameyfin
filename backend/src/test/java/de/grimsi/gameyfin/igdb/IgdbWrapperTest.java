package de.grimsi.gameyfin.igdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.config.WebClientConfig;
import de.grimsi.gameyfin.config.properties.GameyfinProperties;
import de.grimsi.gameyfin.dto.AutocompleteSuggestionDto;
import de.grimsi.gameyfin.entities.Platform;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.grimsi.gameyfin.igdb.IgdbApiQueryBuilder.equal;
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
    static void setup() throws IOException, InterruptedException {
        WebClientConfig webClientConfigMock = mock(WebClientConfig.class);
        GameMapper gameMapperMock = mock(GameMapper.class);
        GameyfinProperties gameyfinPropertiesMock = mock(GameyfinProperties.class, Mockito.RETURNS_DEEP_STUBS);

        target = new IgdbWrapper(WebClient.builder(), webClientConfigMock, gameMapperMock, gameyfinPropertiesMock);

        igdbApiMock.start();
        twitchApiMock.start();

        ReflectionTestUtils.setField(target, "clientId", "client_id_value");
        ReflectionTestUtils.setField(target, "clientSecret", "client_secret_value");
        ReflectionTestUtils.setField(target, "igdbApiBaseUrl", "http://localhost:%s".formatted(igdbApiMock.getPort()));
        ReflectionTestUtils.setField(target, "twitchAuthUrl", "http://localhost:%s/oauth2/token".formatted(twitchApiMock.getPort()));

        when(gameyfinPropertiesMock.igdb().config().preferredPlatforms()).thenReturn(List.of(6));

        when(webClientConfigMock.getIgdbConcurrencyLimiter()).thenReturn(Bulkhead.of("test_bulkhead", BulkheadConfig.ofDefaults()));
        when(webClientConfigMock.getIgdbRateLimiter()).thenReturn(RateLimiter.of("test_ratelimiter", RateLimiterConfig.ofDefaults()));

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

        twitchApiMock.shutdown();
    }

    @AfterAll
    static void tearDown() throws IOException {
        igdbApiMock.shutdown();
    }

    @Test
    void getGameById() throws InterruptedException {
        //Igdb.GameResult gameResult = easyRandom.nextObject(Igdb.GameResult.class);
        Igdb.GameResult gameResult = Igdb.GameResult.newBuilder()
                .addAllGames(List.of(
                        Igdb.Game.newBuilder().setId(easyRandom.nextLong()).build(),
                        Igdb.Game.newBuilder().setId(easyRandom.nextLong()).build(),
                        Igdb.Game.newBuilder().setId(easyRandom.nextLong()).build()))
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
        assertThat(r.getRequestUrl().encodedPath()).isEqualTo("/%s".formatted(IgdbApiProperties.ENDPOINT_GAMES_PROTOBUF));

        String expectedQuery = "fields %s;limit 1;where id = %d;".formatted(IgdbApiProperties.GAME_QUERY_FIELDS_STRING, gameId);

        assertThat(r.getBody().readUtf8()).isEqualTo(expectedQuery);
    }

    @Test
    void getGameBySlug() throws InterruptedException {
        Igdb.GameResult gameResult = Igdb.GameResult.newBuilder()
                .addAllGames(List.of(
                        Igdb.Game.newBuilder().setSlug("game_slug_1").build(),
                        Igdb.Game.newBuilder().setSlug("game_slug_2").build(),
                        Igdb.Game.newBuilder().setSlug("game_slug_3").build()))
                .build();

        String gameSlug = gameResult.getGames(0).getSlug();

        igdbApiMock.enqueue(new MockResponse()
                .setBody(toBuffer(gameResult))
                .setHeader("Content-Type", "application/protobuf")
        );

        Optional<Igdb.Game> gameOptional = target.getGameBySlug("game_slug_1");

        assertThat(gameOptional).isPresent();

        Igdb.Game game = gameOptional.get();

        assertThat(game.getSlug()).isEqualTo(gameSlug);

        RecordedRequest r = igdbApiMock.takeRequest();
        assertThat(r.getRequestUrl()).isNotNull();
        assertThat(r.getRequestUrl().encodedPath()).isEqualTo("/%s".formatted(IgdbApiProperties.ENDPOINT_GAMES_PROTOBUF));

        String expectedQuery = "fields %s;limit 1;where slug = \"%s\";".formatted(IgdbApiProperties.GAME_QUERY_FIELDS_STRING, gameSlug);

        assertThat(r.getBody().readUtf8()).isEqualTo(expectedQuery);
    }

    @Test
    void findPossibleMatchingTitles() throws InterruptedException {
        Igdb.GameResult gameResult = Igdb.GameResult.newBuilder()
                .addAllGames(List.of(
                        Igdb.Game.newBuilder().setName("title_1").build(),
                        Igdb.Game.newBuilder().setName("title_2").build(),
                        Igdb.Game.newBuilder().setName("title_3").build()))
                .build();

        String gameTitle = gameResult.getGames(0).getName();

        igdbApiMock.enqueue(new MockResponse()
                .setBody(toBuffer(gameResult))
                .setHeader("Content-Type", "application/protobuf")
        );

        List<AutocompleteSuggestionDto> suggestions = target.findPossibleMatchingTitles(gameTitle, gameResult.getGamesCount());

        assertThat(suggestions).hasSize(gameResult.getGamesCount());

        RecordedRequest r = igdbApiMock.takeRequest();
        assertThat(r.getRequestUrl()).isNotNull();
        assertThat(r.getRequestUrl().encodedPath()).isEqualTo("/%s".formatted(IgdbApiProperties.ENDPOINT_GAMES_PROTOBUF));

        String expectedQuery = "search \"%s\";fields slug,name,first_release_date,platforms.name;limit %d;where platforms = (6);".formatted(gameTitle, gameResult.getGamesCount());

        assertThat(r.getBody().readUtf8()).isEqualTo(expectedQuery);
    }

    @Test
    void searchForGameByTitle_exactMatch() throws InterruptedException {
        Igdb.GameResult gameResult = Igdb.GameResult.newBuilder()
                .addAllGames(List.of(
                        Igdb.Game.newBuilder().setName("title_1").build(),
                        Igdb.Game.newBuilder().setName("title_2").build(),
                        Igdb.Game.newBuilder().setName("title_3").build()))
                .build();

        String searchTerm = gameResult.getGames(0).getName();

        igdbApiMock.enqueue(new MockResponse()
                .setBody(toBuffer(gameResult))
                .setHeader("Content-Type", "application/protobuf")
        );

        Optional<Igdb.Game> gameOptional = target.searchForGameByTitle(searchTerm);

        assertThat(gameOptional).isPresent();

        Igdb.Game game = gameOptional.get();

        assertThat(game.getName()).isEqualTo(searchTerm);

        RecordedRequest r = igdbApiMock.takeRequest();
        assertThat(r.getRequestUrl()).isNotNull();
        assertThat(r.getRequestUrl().encodedPath()).isEqualTo("/%s".formatted(IgdbApiProperties.ENDPOINT_GAMES_PROTOBUF));

        String expectedQuery = "search \"%s\";fields %s;where platforms = (6);".formatted(searchTerm, IgdbApiProperties.GAME_QUERY_FIELDS_STRING);

        assertThat(r.getBody().readUtf8()).isEqualTo(expectedQuery);
    }

    @Test
    void searchForGameByTitle_EndsWith() throws InterruptedException {
        Igdb.GameResult gameResult = Igdb.GameResult.newBuilder()
                .addAllGames(List.of(
                        Igdb.Game.newBuilder().setName("some_prefix title_1").build(),
                        Igdb.Game.newBuilder().setName("title_2)").build(),
                        Igdb.Game.newBuilder().setName("title_3").build()))
                .build();

        String searchTerm = "title_1";

        igdbApiMock.enqueue(new MockResponse()
                .setBody(toBuffer(gameResult))
                .setHeader("Content-Type", "application/protobuf")
        );

        Optional<Igdb.Game> gameOptional = target.searchForGameByTitle(searchTerm);

        assertThat(gameOptional).isPresent();

        Igdb.Game game = gameOptional.get();

        assertThat(game.getName()).isEqualTo(gameResult.getGames(0).getName());

        RecordedRequest r = igdbApiMock.takeRequest();
        assertThat(r.getRequestUrl()).isNotNull();
        assertThat(r.getRequestUrl().encodedPath()).isEqualTo("/%s".formatted(IgdbApiProperties.ENDPOINT_GAMES_PROTOBUF));

        String expectedQuery = "search \"%s\";fields %s;where platforms = (6);".formatted(searchTerm, IgdbApiProperties.GAME_QUERY_FIELDS_STRING);

        assertThat(r.getBody().readUtf8()).isEqualTo(expectedQuery);
    }

    @Test
    void searchForGameByTitle_Brackets() throws InterruptedException {
        Igdb.GameResult gameResult = Igdb.GameResult.newBuilder()
                .addAllGames(List.of(
                        Igdb.Game.newBuilder().setName("title_1").build(),
                        Igdb.Game.newBuilder().setName("title_2").build(),
                        Igdb.Game.newBuilder().setName("title_3").build()))
                .build();

        String searchTerm = gameResult.getGames(0).getName() + " (Text in brackets should be ignored)";

        // First request should result in an empty response
        igdbApiMock.enqueue(new MockResponse().setHeader("Content-Type", "application/protobuf"));

        // Second request should contain the same query, but with brackets removed
        igdbApiMock.enqueue(new MockResponse()
                .setBody(toBuffer(gameResult))
                .setHeader("Content-Type", "application/protobuf")
        );

        Optional<Igdb.Game> gameOptional = target.searchForGameByTitle(searchTerm);

        assertThat(gameOptional).isPresent();

        Igdb.Game game = gameOptional.get();

        // Result should be game with title equal to search term with brackets removed
        assertThat(game.getName()).isEqualTo(gameResult.getGames(0).getName());

        // First query (should contain brackets)
        RecordedRequest r1 = igdbApiMock.takeRequest();
        assertThat(r1.getRequestUrl()).isNotNull();
        assertThat(r1.getRequestUrl().encodedPath()).isEqualTo("/%s".formatted(IgdbApiProperties.ENDPOINT_GAMES_PROTOBUF));

        String r1_expectedQuery = "search \"%s\";fields %s;where platforms = (6);".formatted(searchTerm, IgdbApiProperties.GAME_QUERY_FIELDS_STRING);

        assertThat(r1.getBody().readUtf8()).isEqualTo(r1_expectedQuery);

        // Second query (should not contain brackets)
        RecordedRequest r2 = igdbApiMock.takeRequest();
        assertThat(r2.getRequestUrl()).isNotNull();
        assertThat(r2.getRequestUrl().encodedPath()).isEqualTo("/%s".formatted(IgdbApiProperties.ENDPOINT_GAMES_PROTOBUF));

        String r2_expectedQuery = "search \"%s\";fields %s;where platforms = (6);".formatted(gameResult.getGames(0).getName(), IgdbApiProperties.GAME_QUERY_FIELDS_STRING);

        assertThat(r2.getBody().readUtf8()).isEqualTo(r2_expectedQuery);
    }

    @Test
    void findPlatforms() throws InterruptedException {
        Igdb.PlatformResult platformResult = Igdb.PlatformResult.newBuilder()
                .addAllPlatforms(List.of(
                        Igdb.Platform.newBuilder().setSlug("platform_1").setName("Platform 1").build(),
                        Igdb.Platform.newBuilder().setSlug("platform_2").setName("Platform 2").build(),
                        Igdb.Platform.newBuilder().setSlug("platform_3").setName("Platform 3").build()))
                .build();

        String searchTerm = platformResult.getPlatforms(0).getSlug();
        int limit = 10;

        igdbApiMock.enqueue(new MockResponse()
                .setBody(toBuffer(platformResult))
                .setHeader("Content-Type", "application/protobuf")
        );

        List<Platform> result = target.findPlatforms(searchTerm, limit);

        assertThat(result.get(0).getSlug()).isEqualTo(searchTerm);

        RecordedRequest r = igdbApiMock.takeRequest();
        assertThat(r.getRequestUrl()).isNotNull();
        assertThat(r.getRequestUrl().encodedPath()).isEqualTo("/%s".formatted(IgdbApiProperties.ENDPOINT_PLATFORMS_PROTOBUF));

        String expectedQuery = "search \"%s\";fields slug,name;limit %s;".formatted(searchTerm, limit);

        assertThat(r.getBody().readUtf8()).isEqualTo(expectedQuery);
    }

    @Test
    void getPlatformBySlug() throws InterruptedException {
        Igdb.PlatformResult platformResult = Igdb.PlatformResult.newBuilder()
                .addAllPlatforms(List.of(
                        Igdb.Platform.newBuilder().setSlug("platform_1").setName("Platform 1").build()))
                .build();

        String slug = platformResult.getPlatforms(0).getSlug();

        igdbApiMock.enqueue(new MockResponse()
                .setBody(toBuffer(platformResult))
                .setHeader("Content-Type", "application/protobuf")
        );

        Optional<Platform> result = target.getPlatformBySlug(slug);

        assertThat(result).isPresent();

        Platform platform = result.get();

        assertThat(platform.getSlug()).isEqualTo(slug);

        RecordedRequest r = igdbApiMock.takeRequest();
        assertThat(r.getRequestUrl()).isNotNull();
        assertThat(r.getRequestUrl().encodedPath()).isEqualTo("/%s".formatted(IgdbApiProperties.ENDPOINT_PLATFORMS_PROTOBUF));

        String expectedQuery = "fields slug,name,platform_logo;where slug = \"%s\";".formatted(slug);

        assertThat(r.getBody().readUtf8()).isEqualTo(expectedQuery);
    }

    private static Buffer toBuffer(Message input) {
        Buffer b = new Buffer();
        b.write(input.toByteArray());
        return b;
    }
}
