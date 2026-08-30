/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

import java.util.Properties

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

// APIUtils.java is checked into git with placeholder URL literals (PLACEHOLDER_*) - this task
// rewrites those same string literals in place with real values from secrets.properties before
// compiling. It targets each named constant by regex (not a one-shot token), so it's safe to run
// again after secrets.properties changes even though the placeholder text is already gone after
// the first run. The file is git-ignored, so the real values never get committed.
val generateApiSecrets by tasks.registering {
    val secretsFile = file("secrets.properties")
    val secretsExampleFile = file("secrets.properties.example")
    // Falls back to the example (placeholder example.com URLs) so contributors without the
    // real secrets.properties can still build - real API calls just won't resolve to anything.
    val activeSecretsFile = if (secretsFile.exists()) secretsFile else secretsExampleFile
    if (activeSecretsFile == secretsExampleFile) {
        logger.lifecycle("secrets.properties not found - building with placeholder URLs from secrets.properties.example. Copy it to secrets.properties and fill in real URLs for working API calls.")
    }

    val props = Properties()
    activeSecretsFile.bufferedReader(Charsets.UTF_8).use<java.io.Reader, Unit> { props.load(it) }
    fun req(k: String) = props.getProperty(k) ?: error("secrets.properties missing key: $k")

    val apiUtilsFile = file("src/main/java/xyz/thm/addon/utils/APIUtils.java")
    val urlsByConstant = mapOf(
        "MEMBER_HUD_URL" to req("api.memberHud"),
        "HIGHWAY_URL" to req("api.highway"),
        "STATUS_URL" to req("api.status"),
        "HIGHWAY_STATUS_URL" to req("api.highwayStatus"),
        "CAPE_URL" to req("api.cape"),
        "CAPE_POST_URL" to req("api.capePost"),
        "CAPE_INDEX_URL" to req("api.capeIndex"),
    )

    inputs.file(activeSecretsFile)
    outputs.file(apiUtilsFile)
    outputs.upToDateWhen { false }

    doLast {
        var content = apiUtilsFile.readText(Charsets.UTF_8)
        for ((constant, url) in urlsByConstant) {
            val pattern = Regex("""(private static final String $constant\s*=\s*")[^"]*(";)""")
            if (!pattern.containsMatchIn(content)) error("APIUtils.java: could not find constant $constant to fill in")
            content = pattern.replace(content) { m -> m.groupValues[1] + url + m.groupValues[2] }
        }
        apiUtilsFile.writeText(content, Charsets.UTF_8)
    }
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
        dependsOn(generateApiSecrets)
        options.encoding = "UTF-8"
        options.release = 21
        options.isFork = true
        options.forkOptions.memoryMaximumSize = "3g"
options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
