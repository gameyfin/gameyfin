package de.grimsi.gameyfin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement


@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
class GameyfinApplication

fun main(args: Array<String>) {
    System.setProperty("spring.devtools.restart.enabled", "false");
    runApplication<GameyfinApplication>(*args)
}