dependencies {
    api(project(":minigames:pof-shared"))
    compileOnly(project(":lobby"))
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
}

repositories {
    maven("https://repo.dmulloy2.net/repository/public/")
}
