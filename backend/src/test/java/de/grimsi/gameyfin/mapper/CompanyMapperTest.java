package de.grimsi.gameyfin.mapper;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.Company;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyMapperTest extends RandomMapperTest<Igdb.InvolvedCompany, Company> {

    @Test
    @Disabled
    void toCompany() {
        Igdb.InvolvedCompany input = generateRandomInput();

        Company output = CompanyMapper.toCompany(input);

        assertThat(output.getSlug()).isEqualTo(input.getCompany().getSlug());
        assertThat(output.getName()).isEqualTo(input.getCompany().getName());
        assertThat(output.getLogoId()).isEqualTo(input.getCompany().getLogo().getImageId());
    }

    @Test
    @Disabled
    void toCompanies() {
        List<Igdb.InvolvedCompany> input = List.of(generateRandomInput(), generateRandomInput(), generateRandomInput());

        List<Company> output = CompanyMapper.toCompanies(input);

        for (int i = 0; i < output.size(); i++) {
            assertThat(output.get(i).getSlug()).isEqualTo(input.get(i).getCompany().getSlug());
            assertThat(output.get(i).getName()).isEqualTo(input.get(i).getCompany().getName());
            assertThat(output.get(i).getLogoId()).isEqualTo(input.get(i).getCompany().getLogo().getImageId());
        }
    }

    private static Igdb.InvolvedCompany generateRandomInvolvedCompany() {
        Igdb.Company c = Igdb.Company.newBuilder()
                .setSlug(UUID.randomUUID().toString())
                .setName(UUID.randomUUID().toString())
                .setLogo(Igdb.CompanyLogo.newBuilder()
                        .setImageId(UUID.randomUUID().toString())
                        .build())
                .build();

        return Igdb.InvolvedCompany.newBuilder()
                .setCompany(c)
                .build();
    }
}
