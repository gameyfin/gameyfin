package de.grimsi.gameyfin;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class GameyfinApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(GameyfinApplication.class)
                .properties("spring.config.name=application,gameyfin,database,secure")
                .build()
                .run(args);
    }

}
