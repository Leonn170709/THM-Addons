/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

// No network needed: every address is a literal and every rejected hostname is refused before DNS.
class TrustedHttpTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "127.0.0.1", "127.1.2.3", "0.0.0.0", "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.1",
        "169.254.169.254", "100.64.0.1", "192.0.0.1", "198.18.0.1",
        "::1", "fd00::1", "fe80::1", "::ffff:127.0.0.1"
    })
    void blocksPrivateAddresses(String ip) throws Exception {
        assertFalse(TrustedHttp.isPublicAddress(InetAddress.getByName(ip)), ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.1.1.1", "8.8.8.8", "172.32.0.1", "2001:4860:4860::8888"})
    void allowsPublicAddresses(String ip) throws Exception {
        assertTrue(TrustedHttp.isPublicAddress(InetAddress.getByName(ip)), ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "LOCALHOST", "localhost.", "anything.localhost", "metadata.google.internal", "db.internal", ""})
    void rejectsInternalHostnames(String host) {
        assertFalse(TrustedHttp.isPublicHostname(host), host);
    }

    @ParameterizedTest
    @CsvSource({
        "file:///etc/passwd, USER_WEBHOOK",
        "ftp://example.com/x, USER_WEBHOOK",
        "http://1.1.1.1/x, API",
        "http://1.1.1.1/x, IMAGE",
        "https://user:pass@1.1.1.1/x, USER_WEBHOOK",
        "https://127.0.0.1/hook, USER_WEBHOOK",
        "https://[::1]/hook, USER_WEBHOOK",
        "https://10.0.0.5/hook, USER_WEBHOOK",
        "https://localhost/hook, USER_WEBHOOK",
    })
    void rejectsUnsafeUrls(String url, TrustedHttp.Kind kind) {
        assertNull(TrustedHttp.parseAllowedUri(url, kind), url);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingUrl(String url) {
        assertNull(TrustedHttp.parseAllowedUri(url, TrustedHttp.Kind.USER_WEBHOOK));
    }

    @Test
    void rejectsOverlongUrl() {
        assertNull(TrustedHttp.parseAllowedUri("https://1.1.1.1/" + "x".repeat(2048), TrustedHttp.Kind.USER_WEBHOOK));
    }

    @ParameterizedTest
    @CsvSource({
        "http://1.1.1.1/hook, USER_WEBHOOK",
        "https://1.1.1.1/hook, USER_WEBHOOK",
        "https://1.1.1.1/v1/x, API",
        "https://1.1.1.1/cape.webp, IMAGE",
    })
    void acceptsPublicUrls(String url, TrustedHttp.Kind kind) {
        assertNotNull(TrustedHttp.parseAllowedUri(url, kind), url);
    }

    @Test
    void describeHidesHostInCause() {
        String logged = TrustedHttp.describe(new IOException("wrap", new UnknownHostException("API.Secret.example")), "https://api.secret.example/v1/x");
        assertFalse(logged.toLowerCase(Locale.ROOT).contains("secret"), logged);
        assertTrue(logged.contains("UnknownHostException"), logged);
    }

    @Test
    void describeHidesUrlInMessage() {
        String logged = TrustedHttp.describe(new IOException("bad https://api.secret.example/v1/x"), "https://api.secret.example/v1/x");
        assertFalse(logged.contains("secret") || logged.contains("/v1/x"), logged);
    }

    @Test
    void describeWithoutUrlKeepsMessage() {
        assertEquals("IOException: boom", TrustedHttp.describe(new IOException("boom"), null));
    }
}
