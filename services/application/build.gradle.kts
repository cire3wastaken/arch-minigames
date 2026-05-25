plugins {
    application
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation(project(":services:games:game-manager"))
    implementation(project(":services:replications"))
    implementation(project(":shared"))
    implementation(project(":services:queue"))
    implementation(project(":services:hosted-world-gateway"))
    implementation(project(":services:application:api"))

    implementation(project(":minigames:skywars-shared"))
    implementation(project(":minigames:miniwalls-shared"))
    implementation(project(":minigames:hunger-games-shared"))
    implementation(project(":persistentgames:housing-api"))
    implementation(project(":persistentgames:prison-shared"))
    implementation(project(":minigames:bedwars-shared"))
    implementation(project(":minigames:pof-shared"))
    implementation(project(":microgames:bridging-api"))
    implementation(project(":microgames:events-api"))

    implementation("org.apache.commons:commons-lang3:3.14.0")

    val commonsVersion = property("commonsVersion")
    implementation("gg.scala.commons:core:$commonsVersion")
    implementation("gg.scala.commons:commons:$commonsVersion")
    implementation("gg.scala.commons:store:$commonsVersion")
    implementation("gg.scala.commons:serversync:$commonsVersion")
    implementation("gg.scala.commons:consensus:$commonsVersion")
    implementation("gg.scala.commons:rpc:$commonsVersion")
    implementation("gg.scala.commons:serializers:$commonsVersion")

    implementation("net.kyori:adventure-key:4.18.0")
    implementation("net.kyori:adventure-text-serializer-gson:4.18.0")

    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.mongodb:mongo-java-driver:3.12.14")

    implementation("com.xenomachina:kotlin-argparser:2.0.7")
    implementation("gg.scala.store:shared:1.0.0")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("gg.scala.aware:aware:1.2.1")
    implementation("io.lettuce:lettuce-core:6.2.6.RELEASE")

    implementation("io.sentry:sentry:7.22.0")

    implementation("org.litote.kmongo:kmongo:4.11.0")
}

application {
    mainClass.set("gg.tropic.practice.application.ApplicationServerKt")
}
