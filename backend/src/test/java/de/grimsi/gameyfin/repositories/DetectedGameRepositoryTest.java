package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.service.FilesystemService;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DetectedGameRepositoryTest {

    @Autowired
    private DetectedGameRepository target;

    private final EasyRandom easyRandom = new EasyRandom();

    @BeforeEach
    void dropTable() {
        target.deleteAll();
    }

    @Test
    void existsByPath() {
        String path = "some/random/path";
        DetectedGame input = DetectedGame.builder()
                .slug("slug")
                .title("title")
                .coverId("coverId")
                .path(path)
                .build();

        assertThat(target.existsByPath(path)).isFalse();

        target.save(input);

        assertThat(target.existsByPath(path)).isTrue();
    }

    @Test
    void existsBySlug() {
        String slug = "some-random-slug";
        DetectedGame input = DetectedGame.builder()
                .slug(slug)
                .title("title")
                .coverId("coverId")
                .path("path")
                .build();

        assertThat(target.existsBySlug(slug)).isFalse();

        target.save(input);

        assertThat(target.existsBySlug(slug)).isTrue();
    }

    @Test
    void findByPath() {
        String path = "some/random/path";
        DetectedGame input = DetectedGame.builder()
                .slug("slug")
                .title("title")
                .coverId("coverId")
                .path(path)
                .build();

        assertThat(target.findByPath(path)).isEmpty();

        target.save(input);

        Optional<DetectedGame> optionalResult = target.findByPath(path);

        assertThat(optionalResult).isPresent();

        DetectedGame result = optionalResult.get();

        assertThat(result).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "some/random/library/path/"})
    void getAllByPathNotInAndPathStartsWith(String library) {
        String libraryPath = Path.of(library).toString();
        String otherLibraryPath = Path.of("another/random/library/path/").toString();

        List<DetectedGame> detectedGames = easyRandom.objects(DetectedGame.class, 2).peek(g -> g.setPath(Path.of(libraryPath, g.getPath()).toString())).toList();
        List<DetectedGame> detectedGamesDifferentLibrary = easyRandom.objects(DetectedGame.class, 2).peek(g -> g.setPath(Path.of(otherLibraryPath, g.getPath()).toString())).toList();
        List<DetectedGame> deletedGames = easyRandom.objects(DetectedGame.class, 2).peek(g -> g.setPath(Path.of(libraryPath, g.getPath()).toString())).toList();
        List<Path> gamePaths = detectedGames.stream().map(DetectedGame::getPath).map(Path::of).collect(Collectors.toList());
        gamePaths.addAll(detectedGamesDifferentLibrary.stream().map(DetectedGame::getPath).map(Path::of).toList());

        target.saveAll(detectedGames);
        target.saveAll(detectedGamesDifferentLibrary);

        assertThat(target.getAllByPathNotInAndPathStartsWith(gamePaths, libraryPath)).isEmpty();

        target.saveAll(deletedGames);

        List<DetectedGame> result = target.getAllByPathNotInAndPathStartsWith(gamePaths, libraryPath);
        assertThat(result).hasSize(2);
        assertThat(result).containsOnlyOnceElementsOf(deletedGames);
    }
}
