package de.grimsi.gameyfin.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// see https://stackoverflow.com/questions/26699385/spring-boot-yaml-configuration-for-a-list-of-strings
@ConfigurationProperties("gameyfin")
// Technically SonarQube is correct, but I like to keep the lowercase method names since it correlates better with the config keys
@SuppressWarnings("java:S3010")
public record GameyfinProperties(
        folders folders,
        List<String> fileExtensions,
        List<String> fileSuffixes,
        igdb igdb) {

    public record folders(String data) {}


    public record igdb(config config) {
        public record config(List<Integer> preferredPlatforms) {}
    }
}

