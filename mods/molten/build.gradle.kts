plugins {
    id("fabric-loom") version "1.6-SNAPSHOT"
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
    id("maven-publish")
}

base {
    archivesName = project.property("archives_base_name").toString()
    group = project.property("maven_group").toString()
    version = project.property("mod_version").toString()
}

repositories {
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven/")
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven {
        name = "TerraformersMC"
        url = uri("https://maven.terraformersmc.com/")
    }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("fabric_kotlin_version")}")

    modImplementation("me.shedaniel.cloth:cloth-config-fabric:${project.property("cloth_config_version")}")
    modImplementation("com.terraformersmc:modmenu:${project.property("mod_menu_version")}")
}

tasks {
    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "21"
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all"
            )
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21

        withSourcesJar()
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${base.archivesName.get()}" }
        }
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
            }
        }

        repositories {
            maven {
                url = uri("file:///${projectDir}/mcmodsrepo")
            }
        }
    }
}

loom {
    mixin {
        defaultRefmapName = "${base.archivesName.get()}.refmap.json"
    }
}
