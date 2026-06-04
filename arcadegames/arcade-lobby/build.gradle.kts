dependencies {
    api(project(":arcadegames:arcade-api"))
    compileOnly(project(":lobby"))

    // Bundled (not compileOnly): ArcadeCatalog reads these games' MiniGameTypeMetadata
    // at runtime to derive the lobby grid, and those classes are not otherwise present
    // on an arcade-lobby server.
    implementation(project(":arcadegames:skywars-shared"))
    implementation(project(":arcadegames:miniwalls-shared"))
    implementation(project(":arcadegames:hunger-games-shared"))
    implementation(project(":arcadegames:pof-shared"))
}
