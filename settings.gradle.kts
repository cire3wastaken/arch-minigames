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

    "microgames:events",
    "microgames:events-api",
    "microgames:events-lobby",

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

    "services:application",
    "services:replications",
    "services:queue",
    "services:metadata",
    "services:hosted-world-gateway",

    "services:application:api",
    "services:games:game-manager",

    "minigames:skywars",
    "minigames:skywars-lobby",
    "minigames:skywars-shared",

    "minigames:bedwars",
    "minigames:bedwars-lobby",
    "minigames:bedwars-shared",

    "minigames:miniwalls",
    "minigames:miniwalls-lobby",
    "minigames:miniwalls-shared",

    "minigames:hunger-games",
    "minigames:hunger-games-lobby",
    "minigames:hunger-games-shared",

    "minigames:pof",
    "minigames:pof-lobby",
    "minigames:pof-shared",

    "minigames:duels-modern-lobby",

    "spigot-integration"
)
