repositories {
    mavenCentral()
    maven("https://repo.glaremasters.me/repository/concuncan/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.infernalsuite.com/repository/maven-snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

configurations.all {
    exclude(group = "gg.scala.spigot", module = "server")
}

dependencies {
    api(project(":shared"))
    api(project(":arcadegames:skywars-shared"))

    api(project(":versioned:generics"))
    api(project(":versioned:legacy"))
    api(project(":versioned:modern"))

    compileOnly("com.grinderwolf:slimeworldmanager-plugin:2.2.1")
    compileOnly("com.infernalsuite.asp:api:4.0.0-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    // legacy WorldEdit 6 symbols still referenced by WorldComponentCommand (clipboard ops);
    // schematic paste is routed through SchematicProvider abstraction.
    compileOnly("mc.arch.worldedit:WorldEdit:1.0.0")
}
