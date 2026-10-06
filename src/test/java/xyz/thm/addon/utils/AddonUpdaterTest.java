/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.thm.addon.system.THMSystem;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AddonUpdaterTest {
    private static final URI FEED = URI.create("https://updates.example.com/feed.json");
    private static final String COMMIT = "a".repeat(40);
    private static final String HASH = "b".repeat(64);

    @Test
    void installedMarkerTracksTheReplacementUntilRestart() throws Exception {
        var installed = new AddonUpdater.Entry(THMSystem.UpdateChannel.DEV, "26.2", "0.3.0", COMMIT,
            "New build", "Notes", "26.2", "https://updates.example.com/new.jar", HASH);
        var previous = new AddonUpdater.Entry(THMSystem.UpdateChannel.DEV, "26.2", "0.3.0", "c".repeat(40),
            "Previous build", "Notes", "26.2", "https://updates.example.com/old.jar", HASH);
        var differentVersion = new AddonUpdater.Entry(THMSystem.UpdateChannel.STABLE, "26.2", "0.2.0", COMMIT,
            "Different release", "Notes", "26.2", "https://updates.example.com/release.jar", HASH);
        var field = AddonUpdater.class.getDeclaredField("installedUpdate");
        field.setAccessible(true);
        Object original = field.get(null);
        try {
            field.set(null, previous);
            assertTrue(AddonUpdater.isInstalled(previous));
            assertFalse(AddonUpdater.isInstalled(installed));
            field.set(null, installed);
            assertTrue(AddonUpdater.isInstalled(installed));
            assertFalse(AddonUpdater.isInstalled(previous));
            assertFalse(AddonUpdater.isInstalled(differentVersion));
        } finally {
            field.set(null, original);
        }
    }

    @Test
    void acceptsOnlySignedSameOriginCompatibleBuilds() throws Exception {
        String payload = """
            {"format":1,"entries":[
              {"channel":"DEV","branch":"26.2","version":"0.3.0","commit":"%s","title":"Fix blocks","notes":"Commit description","minecraft":"26.2","url":"https://updates.example.com/26.2/dev/a.jar","sha256":"%s"},
              {"channel":"DEV","branch":"other","version":"0.3.0","commit":"%s","title":"Wrong branch","notes":"Notes","minecraft":"26.2","url":"https://updates.example.com/other/dev/a.jar","sha256":"%s"},
              {"channel":"STABLE","branch":"1.21.11","version":"0.2.0","commit":"%s","title":"Old release","notes":"Notes","minecraft":"1.21.11","url":"https://updates.example.com/1.21.11/stable/old.jar","sha256":"%s"}
            ]}
            """.formatted(COMMIT, HASH, COMMIT, HASH, COMMIT, HASH);
        var keypair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String key = Base64.getEncoder().encodeToString(keypair.getPublic().getEncoded());
        String valid = envelope(payload, keypair.getPrivate());
        var entries = AddonUpdater.parseSignedFeed(valid, FEED, key, "26.2", "26.2");
        assertEquals(1, entries.size());
        assertEquals("Fix blocks", entries.getFirst().title());

        String changed = payload.replace("Fix blocks", "Evil blocks");
        String tampered = valid.replace(Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)),
            Base64.getEncoder().encodeToString(changed.getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> AddonUpdater.parseSignedFeed(tampered, FEED, key, "26.2", "26.2"));

        String crossHost = payload.replace("updates.example.com/26.2/dev", "evil.example.com/26.2/dev");
        assertThrows(IllegalArgumentException.class, () -> AddonUpdater.parseSignedFeed(
            envelope(crossHost, keypair.getPrivate()), FEED, key, "26.2", "26.2"));
    }

    @TempDir Path directory;

    @Test
    void tamperedDownloadsDoNotTouchInstalledJarOrBackup() throws Exception {
        Path current = directory.resolve("addon.jar");
        Path backup = directory.resolve("thm-addon-previous.jar.bak");
        Files.writeString(current, "installed");
        Files.writeString(backup, "previous");
        byte[] valid = jarBytes("26.2");
        var entry = entryFor(valid);
        assertThrows(IllegalStateException.class, () -> AddonUpdater.replaceJar(current, directory, "tampered".getBytes(), entry));
        assertEquals("installed", Files.readString(current));
        assertEquals("previous", Files.readString(backup));
        assertNoPendingFiles();
    }

    @Test
    void signedButWrongMetadataIsDiscardedBeforeBackupOrReplacement() throws Exception {
        Path current = directory.resolve("addon.jar");
        Files.writeString(current, "installed");
        byte[] wrong = jarBytes("1.21.11");
        assertThrows(IllegalStateException.class, () -> AddonUpdater.replaceJar(current, directory, wrong, entryFor(wrong)));
        assertEquals("installed", Files.readString(current));
        assertFalse(Files.exists(directory.resolve("thm-addon-previous.jar.bak")));
        assertNoPendingFiles();
    }

    @Test
    void verifiedReplacementPreservesThePreviousJarAndAcceptsUppercaseDigests() throws Exception {
        Path current = directory.resolve("addon.jar");
        Files.writeString(current, "installed");
        byte[] bytes = jarBytes("26.2");
        AddonUpdater.replaceJar(current, directory, bytes, entryFor(bytes));
        assertArrayEquals(bytes, Files.readAllBytes(current));
        assertEquals("installed", Files.readString(directory.resolve("thm-addon-previous.jar.bak")));
        assertNoPendingFiles();
    }

    @Test
    void installsCannotEscapeTheModsDirectoryOrFollowSymlinks() throws Exception {
        Path mods = Files.createDirectory(directory.resolve("mods"));
        Path outside = directory.resolve("outside.jar");
        Files.writeString(outside, "other file");
        byte[] bytes = jarBytes("26.2");
        assertThrows(IllegalStateException.class, () -> AddonUpdater.replaceJar(outside, mods, bytes, entryFor(bytes)));
        Path link = mods.resolve("addon.jar");
        Files.createSymbolicLink(link, outside);
        assertThrows(IllegalStateException.class, () -> AddonUpdater.replaceJar(link, mods, bytes, entryFor(bytes)));
        assertEquals("other file", Files.readString(outside));
    }

    @Test
    void backupFailureLeavesTheInstallationIntactAndCleansTheDownload() throws Exception {
        Path current = directory.resolve("addon.jar");
        Files.writeString(current, "installed");
        Path backup = Files.createDirectory(directory.resolve("thm-addon-previous.jar.bak"));
        Files.writeString(backup.resolve("keep"), "unrelated file");
        byte[] bytes = jarBytes("26.2");
        assertThrows(java.io.IOException.class, () -> AddonUpdater.replaceJar(current, directory, bytes, entryFor(bytes)));
        assertEquals("installed", Files.readString(current));
        assertEquals("unrelated file", Files.readString(backup.resolve("keep")));
        assertNoPendingFiles();
    }

    @Test
    void repeatedRequestsAreNotQueuedBehindAnActiveOperation() throws Exception {
        var submit = AddonUpdater.class.getDeclaredMethod("submit", Runnable.class, Runnable.class);
        submit.setAccessible(true);
        CountDownLatch running = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        AtomicBoolean extraRan = new AtomicBoolean();
        submit.invoke(null, (Runnable) () -> {
            running.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { done.countDown(); }
        }, null);
        try {
            assertTrue(running.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 50; i++) submit.invoke(null, (Runnable) () -> extraRan.set(true), null);
            release.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            var worker = AddonUpdater.class.getDeclaredField("WORKER");
            worker.setAccessible(true);
            ((ExecutorService) worker.get(null)).submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertFalse(extraRan.get());
        } finally { release.countDown(); }
    }

    private void assertNoPendingFiles() throws Exception {
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".pending")));
        }
    }

    private static AddonUpdater.Entry entryFor(byte[] bytes) throws Exception {
        return new AddonUpdater.Entry(THMSystem.UpdateChannel.DEV, "26.2", "0.3.0", COMMIT,
            "Test build", "Notes", "26.2", "https://updates.example.com/new.jar",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).toUpperCase(java.util.Locale.ROOT));
    }

    private static byte[] jarBytes(String minecraft) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write(("{\"id\":\"thm-addon\",\"version\":\"0.3.0\",\"custom\":{\"github:sha\":\"" + COMMIT
                + "\",\"github:branch\":\"26.2\"},\"depends\":{\"minecraft\":\"" + minecraft + "\"}}")
                .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static String envelope(String payload, java.security.PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return "{\"payload\":\"" + Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
            + "\",\"signature\":\"" + Base64.getEncoder().encodeToString(signer.sign()) + "\"}";
    }
}
