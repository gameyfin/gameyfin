package de.grimsi.gameyfin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.transaction.annotation.EnableTransactionManagement


@SpringBootApplication
@EnableTransactionManagement
class GameyfinApplication

fun main(args: Array<String>) {
    runApplication<GameyfinApplication>(*args)
}