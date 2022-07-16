package de.grimsi.gameyfin.mapper;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.Company;

import java.util.List;

public class CompanyMapper {

    public static Company toCompany(Igdb.InvolvedCompany c) {
        return Company.builder()
                .slug(c.getCompany().getSlug())
                .name(c.getCompany().getName())
                .logoId(c.getCompany().getLogo().getId())
                .build();
    }

    public static List<Company> toCompanies(List<Igdb.InvolvedCompany> c) {
        return c.stream().map(CompanyMapper::toCompany).toList();
    }
}
