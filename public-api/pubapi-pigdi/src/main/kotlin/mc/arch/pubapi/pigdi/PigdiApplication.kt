package mc.arch.pubapi.pigdi

import mc.arch.pubapi.pigdi.repository.AkersProfileRepository
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
@EnableMongoRepositories(basePackageClasses = [AkersProfileRepository::class])
class PigdiApplication

fun main(args: Array<String>)
{
    runApplication<PigdiApplication>(*args)
}
