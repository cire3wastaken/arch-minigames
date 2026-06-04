repositories {
    maven("https://repo.dmulloy2.net/repository/public/")
}

dependencies {
    compileOnly(project(":game"))
    compileOnly(project(":shared"))
    compileOnly(project(":spigot-integration"))
    compileOnly(fileTree("spigot"))
    api(project(":arcadegames:pof-shared"))
    api(project(":arcadegames:arcade-api"))
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
}
