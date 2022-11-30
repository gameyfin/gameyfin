package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.UnmappableFile;
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
class UnmappableFileRepositoryTest {

    @Autowired
    private UnmappableFileRepository target;

    private final EasyRandom easyRandom = new EasyRandom();

    @BeforeEach
    void dropTable() {
        target.deleteAll();
    }

    @Test
    void existsByPath() {
        String path = "some/random/path";
        UnmappableFile input = new UnmappableFile(path);

        assertThat(target.existsByPath(path)).isFalse();

        target.save(input);

        assertThat(target.existsByPath(path)).isTrue();
    }

    @Test
    void findByPath() {
        String path = "some/random/path";
        UnmappableFile input = new UnmappableFile(path);

        assertThat(target.findByPath(path)).isEmpty();

        target.save(input);

        Optional<UnmappableFile> optionalResult = target.findByPath(path);

        assertThat(optionalResult).isPresent();

        UnmappableFile result = optionalResult.get();

        assertThat(result).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "some/random/library/path/"})
    void getAllByPathNotInAndPathStartsWith(String library) {
        String libraryPath = Path.of(library).toString();
        String otherLibraryPath = Path.of("another/random/library/path/").toString();

        List<UnmappableFile> UnmappableFiles = easyRandom.objects(UnmappableFile.class, 2).peek(g -> g.setPath(Path.of(libraryPath, g.getPath()).toString())).toList();
        List<UnmappableFile> UnmappableFilesDifferentLibrary = easyRandom.objects(UnmappableFile.class, 2).peek(g -> g.setPath(Path.of(otherLibraryPath, g.getPath()).toString())).toList();
        List<UnmappableFile> deletedGames = easyRandom.objects(UnmappableFile.class, 2).peek(g -> g.setPath(Path.of(libraryPath, g.getPath()).toString())).toList();
        List<Path> gamePaths = UnmappableFiles.stream().map(UnmappableFile::getPath).map(Path::of).collect(Collectors.toList());
        gamePaths.addAll(UnmappableFilesDifferentLibrary.stream().map(UnmappableFile::getPath).map(Path::of).toList());

        target.saveAll(UnmappableFiles);
        target.saveAll(UnmappableFilesDifferentLibrary);

        assertThat(target.getAllByPathNotInAndPathStartsWith(gamePaths, libraryPath)).isEmpty();

        target.saveAll(deletedGames);

        List<UnmappableFile> result = target.getAllByPathNotInAndPathStartsWith(gamePaths, libraryPath);
        assertThat(result)
                .hasSize(2)
                .containsOnlyOnceElementsOf(deletedGames);
    }
}
