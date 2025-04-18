package de.grimsi.gameyfin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement


@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
@EnableAsync
class GameyfinApplication

fun main(args: Array<String>) {
    runApplication<GameyfinApplication>(*args)
}