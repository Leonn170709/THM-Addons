/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    // Meteor
    modImplementation(libs.meteor.client)

    // Baritone
    modCompileOnly(libs.baritone)
}

sourceSets {
    main {
        java {
            exclude("xyz/thm/addon/modules/HandshakeHostTest.java")
            exclude("xyz/thm/addon/mixin/ClientConnectionMixin.java")
            exclude("xyz/thm/addon/mixin/HandshakeC2SPacketMixin.java")
            exclude("xyz/thm/addon/mixin/MultiplayerScreenRouteButtonMixin.java")
            exclude("xyz/thm/addon/mixin/TitleScreenFastestRouteButtonMixin.java")
        }
    }
}

tasks {
    processResources {
        // Falls back to the example (placeholder example.com URLs) so contributors without the
        // real secrets.properties can still build - real API calls just won't resolve to anything.
        val secretsFile = file("secrets.properties")
        val secretsExampleFile = file("secrets.properties.example")
        val activeSecretsFile = if (secretsFile.exists()) secretsFile else secretsExampleFile
        if (activeSecretsFile == secretsExampleFile) {
            logger.lifecycle("secrets.properties not found - building with placeholder URLs from secrets.properties.example. Copy it to secrets.properties and fill in real URLs for working API calls.")
        }
        inputs.file(activeSecretsFile)
        from(activeSecretsFile) {
            rename { "thm-secrets.properties" }
        }

        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get(),
            "gh_hash" to (System.getenv("GITHUB_SHA") ?: run {
                val process = ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(rootDir)
                    .start()
                process.inputStream.bufferedReader().readLine()?.trim() ?: ""
            }),
            "gh_branch" to (System.getenv("GITHUB_REF_NAME") ?: run {
                val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                    .directory(rootDir)
                    .start()
                process.inputStream.bufferedReader().readLine()?.trim() ?: ""
            }),
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        exclude("xyz/thm/addon/modules/ElytraUAV.class")
        exclude("xyz/thm/addon/modules/ElytraUAV$*.class")
        exclude("xyz/thm/addon/modules/HandshakeHostTest.class")
        exclude("xyz/thm/addon/modules/HandshakeHostTest$*.class")
        exclude("xyz/thm/addon/mixin/ClientConnectionMixin.class")
        exclude("xyz/thm/addon/mixin/HandshakeC2SPacketMixin.class")
        exclude("xyz/thm/addon/mixin/MultiplayerScreenRouteButtonMixin.class")
        exclude("xyz/thm/addon/mixin/TitleScreenFastestRouteButtonMixin.class")

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }

        manifest {
            attributes("Main-Class" to "xyz.thm.addon.Main")
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
        options.isFork = true
        options.forkOptions.memoryMaximumSize = "3g"
options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
