dependencies {
    compileOnly(project(":shared"))
    compileOnly(project(":services:application:api"))
    compileOnly(project(":services:replications"))
    compileOnly(project(":services:games:game-manager"))

    api("lol.arch.symphony:api:1.1.1")

    compileOnly(project(":arcadegames:miniwalls-shared"))
    compileOnly(project(":arcadegames:skywars-shared"))
    compileOnly(project(":minigames:bedwars-shared"))
    compileOnly(project(":arcadegames:pof-shared"))
    compileOnly(project(":arcadegames:arcade-api"))
    implementation(project(":arcadegames:hunger-games-shared"))

    implementation("net.md-5:bungeecord-chat:1.20-R0.1")

    // Sentry for distributed tracing
    compileOnly("io.sentry:sentry:7.22.0")
}
