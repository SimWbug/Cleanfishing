plugins {
    java
}

group = "fr.cleangang"
version = "1.0.0"

repositories {
    mavenCentral()
    maven(url = "https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// Resource pack (dossier pack/) zippé et embarqué dans le plugin : extrait au premier démarrage
// dans plugins/CleanFishing/pack/ où l'admin peut le modifier.
val packZip by tasks.registering(Zip::class) {
    from("pack")
    archiveFileName.set("CleanFishing-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/pack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks {
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
        from(packZip)
    }
    jar {
        archiveBaseName.set("CleanFishing")
    }
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}
