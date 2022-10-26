package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.Library;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LibraryRepositoryTest {

    @Autowired
    private LibraryRepository target;

    @Test
    void existsByPathIgnoreCase() {
        String path = "Some/Random/Path";
        Library input = Library.builder().path(path).build();

        assertThat(target.existsByPathIgnoreCase(path)).isFalse();

        target.save(input);

        assertThat(target.existsByPathIgnoreCase(path)).isTrue();
        assertThat(target.existsByPathIgnoreCase(path.toLowerCase(Locale.ENGLISH))).isTrue();
        assertThat(target.existsByPathIgnoreCase(path.toUpperCase(Locale.ENGLISH))).isTrue();
    }

    @Test
    void findByPath() {
        String path = "Some/Random/Path";
        Library input = Library.builder().path(path).build();

        target.save(input);

        Optional<Library> optionalResult = target.findByPath(path);

        assertThat(optionalResult).isPresent();

        Library result = optionalResult.get();

        assertThat(result).isEqualTo(input);
    }
}