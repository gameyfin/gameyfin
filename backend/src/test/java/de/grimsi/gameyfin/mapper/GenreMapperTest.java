package de.grimsi.gameyfin.mapper;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.Genre;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenreMapperTest extends RandomMapperTest<Igdb.Genre, Genre> {

    @Test
    void toGenre() {
        Igdb.Genre input = generateRandomInput();

        Genre output = GenreMapper.toGenre(input);

        assertThat(output.getSlug()).isEqualTo(input.getSlug());
        assertThat(output.getName()).isEqualTo(input.getName());
    }

    @Test
    void toGenres() {
        List<Igdb.Genre> input = List.of(generateRandomInput(), generateRandomInput(), generateRandomInput());

        List<Genre> output = GenreMapper.toGenres(input);

        for (int i = 0; i < output.size(); i++) {
            assertThat(output.get(i).getSlug()).isEqualTo(input.get(i).getSlug());
            assertThat(output.get(i).getName()).isEqualTo(input.get(i).getName());
        }
    }
}
