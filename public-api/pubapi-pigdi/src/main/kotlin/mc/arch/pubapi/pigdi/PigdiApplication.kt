package mc.arch.pubapi.pigdi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * PIGDI - Public Immutable Game Data Interface
 * Spring Boot REST API for accessing game statistics.
 *
 * @author Subham
 * @since 12/27/24
 */
@SpringBootApplication
@EnableScheduling
@EnableMongoRepositories(basePackages = ["mc.arch.pubapi.pigdi.repository"])
class PigdiApplication

fun main(args: Array<String>)
{
    runApplication<PigdiApplication>(*args)
}
