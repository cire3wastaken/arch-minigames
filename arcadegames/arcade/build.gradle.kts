dependencies {
    compileOnly(project(":game"))
    compileOnly(project(":shared"))
    compileOnly(project(":spigot-integration"))
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    api(project(":arcadegames:arcade-api"))
}
