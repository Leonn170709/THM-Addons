/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

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

// Generates the 7 API endpoint URLs as an AES/GCM-encrypted vault (fresh random key per build,
// never committed) instead of plain constants - a compromised/decompiled jar still needs to break
// the cipher to recover an endpoint. APIUtils.java itself stays plain, readable, and untouched.
val generateApiEndpoints by tasks.registering {
    val secretsFile = file("secrets.properties")
    val secretsExampleFile = file("secrets.properties.example")
    // Falls back to the example (placeholder example.com URLs) so contributors without the
    // real secrets.properties can still build - real API calls just won't resolve to anything.
    val activeSecretsFile = if (secretsFile.exists()) secretsFile else secretsExampleFile
    val outputFile = file("src/main/java/xyz/thm/addon/utils/GeneratedApiEndpoints.java")

    inputs.file(activeSecretsFile)
    outputs.file(outputFile)

    doLast {
        if (!activeSecretsFile.exists()) error(
            "Neither secrets.properties nor secrets.properties.example found."
        )
        if (activeSecretsFile == secretsExampleFile) {
            logger.lifecycle("secrets.properties not found - building with placeholder URLs from secrets.properties.example. Copy it to secrets.properties and fill in real URLs for working API calls.")
        }

        val props = Properties()
        activeSecretsFile.bufferedReader(Charsets.UTF_8).use<java.io.Reader, Unit> { props.load(it) }
        fun req(k: String) = props.getProperty(k) ?: error("secrets.properties missing key: $k")

        val urls = linkedMapOf(
            "memberHud" to req("api.memberHud"),
            "highway" to req("api.highway"),
            "status" to req("api.status"),
            "highwayStatus" to req("api.highwayStatus"),
            "capeList" to req("api.cape"),
            "capePost" to req("api.capePost"),
            "capeIndex" to req("api.capeIndex"),
        )
        for ((name, url) in urls) {
            if (!url.startsWith("https://")) error("secrets.properties key api.$name must be an https:// URL")
        }

        val rng = SecureRandom()
        val masterKey = ByteArray(32).also { rng.nextBytes(it) }
        val keyA = ByteArray(32).also { rng.nextBytes(it) }
        val keyB = ByteArray(32) { (masterKey[it].toInt() xor keyA[it].toInt()).toByte() }

        fun encryptUrl(url: String): String {
            val iv = ByteArray(12).also { rng.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(128, iv))
            val packed = iv + cipher.doFinal(url.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(packed)
        }

        fun ba(bytes: ByteArray) = bytes.joinToString(",") { it.toInt().toString() }

        val encrypted = urls.mapValues { encryptUrl(it.value) }

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            |package xyz.thm.addon.utils;
            |
            |import javax.crypto.Cipher;
            |import javax.crypto.spec.GCMParameterSpec;
            |import javax.crypto.spec.SecretKeySpec;
            |import java.nio.charset.StandardCharsets;
            |import java.util.Base64;
            |
            |/**
            | * Build-generated vault for API endpoint URLs (see generateApiEndpoints in build.gradle.kts).
            | * Method names and HTTP behaviour live in APIUtils/TrustedHttp - only ciphertext is here.
            | */
            |final class GeneratedApiEndpoints {
            |    private static final byte[] KEY_A = {${ba(keyA)}};
            |    private static final byte[] KEY_B = {${ba(keyB)}};
            |    private static final String MEMBER_HUD = "${encrypted["memberHud"]}";
            |    private static final String HIGHWAY = "${encrypted["highway"]}";
            |    private static final String STATUS = "${encrypted["status"]}";
            |    private static final String HIGHWAY_STATUS = "${encrypted["highwayStatus"]}";
            |    private static final String CAPE_LIST = "${encrypted["capeList"]}";
            |    private static final String CAPE_POST = "${encrypted["capePost"]}";
            |    private static final String CAPE_INDEX = "${encrypted["capeIndex"]}";
            |
            |    private GeneratedApiEndpoints() {}
            |
            |    static String memberHudUrl() { return decrypt(MEMBER_HUD); }
            |    static String highwayUrl() { return decrypt(HIGHWAY); }
            |    static String statusUrl() { return decrypt(STATUS); }
            |    static String highwayStatusUrl() { return decrypt(HIGHWAY_STATUS); }
            |    static String capeListUrl() { return decrypt(CAPE_LIST); }
            |    static String capePostUrl() { return decrypt(CAPE_POST); }
            |    static String capeIndexUrl() { return decrypt(CAPE_INDEX); }
            |
            |    private static byte[] masterKey() {
            |        byte[] key = new byte[32];
            |        for (int i = 0; i < 32; i++) key[i] = (byte) (KEY_A[i] ^ KEY_B[i]);
            |        return key;
            |    }
            |
            |    private static String decrypt(String packed) {
            |        try {
            |            byte[] raw = Base64.getDecoder().decode(packed);
            |            if (raw.length < 13) return null;
            |            byte[] iv = new byte[12];
            |            byte[] ciphertext = new byte[raw.length - 12];
            |            System.arraycopy(raw, 0, iv, 0, 12);
            |            System.arraycopy(raw, 12, ciphertext, 0, ciphertext.length);
            |            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            |            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey(), "AES"), new GCMParameterSpec(128, iv));
            |            String url = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            |            if (!url.startsWith("https://")) return null;
            |            return url;
            |        } catch (Exception e) {
            |            return null;
            |        }
            |    }
            |}
            """.trimMargin(),
            Charsets.UTF_8
        )
        logger.lifecycle("Generated GeneratedApiEndpoints.java (URL ciphertext only; APIUtils stays readable)")
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
        dependsOn(generateApiEndpoints)
        options.encoding = "UTF-8"
        options.release = 21
        options.isFork = true
        options.forkOptions.memoryMaximumSize = "3g"
options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
