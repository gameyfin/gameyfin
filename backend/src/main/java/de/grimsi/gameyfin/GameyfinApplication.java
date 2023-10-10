package de.grimsi.gameyfin;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GameyfinApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(GameyfinApplication.class)
                .properties( "file.encoding=UTF-8", "spring.config.name=application,gameyfin,database,secure")
                .build()
                .run(args);
    }

}
