/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import xyz.thm.addon.system.THMSystem;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

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

    private static String envelope(String payload, java.security.PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return "{\"payload\":\"" + Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
            + "\",\"signature\":\"" + Base64.getEncoder().encodeToString(signer.sign()) + "\"}";
    }
}
