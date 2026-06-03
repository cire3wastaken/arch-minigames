pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "arch-minigames"
include(
    "shared", "game", "lobby",
    "main-lobby", "devtools", "sbb",
    "parties", "agent",

    "public-api:pubapi-akers",
    "public-api:pubapi-pigdi",

    "persistentgames:housing",
    "persistentgames:housing-api",
    "persistentgames:housing-lobby",

    "persistentgames:prison",
    "persistentgames:prison-shared",
    "persistentgames:prison-lobby",

    "versioned:generics",
    "versioned:legacy",
    "versioned:modern",

    "microgames:bridging",
    "microgames:bridging-api",

    "arcadegames:arcade-api",
    "arcadegames:arcade",
    "arcadegames:arcade-lobby",

    "arcadegames:skywars",
    "arcadegames:skywars-shared",

    "arcadegames:miniwalls",
    "arcadegames:miniwalls-shared",

    "arcadegames:hunger-games",
    "arcadegames:hunger-games-shared",

    "arcadegames:pof",
    "arcadegames:pof-shared",

    "services:application",
    "services:replications",
    "services:queue",
    "services:metadata",
    "services:hosted-world-gateway",

    "services:application:api",
    "services:games:game-manager",

    "minigames:bedwars",
    "minigames:bedwars-lobby",
    "minigames:bedwars-shared",

    "minigames:duels-modern-lobby",

    "spigot-integration"
)
