/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// No network needed: every address is a literal and every rejected hostname is refused before DNS.
class TrustedHttpTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        " ", "not-a-token", "1-1-1-1-1", "00112233445566778899aabbccddeeff",
        "00112233-4455-6677-8899-aabbccddeefg", "00112233-4455-6677-8899-aabbccddeeff\r\n",
        " 00112233-4455-6677-8899-aabbccddeeff"
    })
    void rejectsTokensWithoutTheFullUuidFormat(String token) {
        assertFalse(TrustedHttp.isValidApiToken(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "00112233-4455-6677-8899-aabbccddeeff", "00112233-4455-6677-8899-AABBCCDDEEFF",
        "00000000-0000-0000-0000-000000000000"
    })
    void acceptsFullUuidFormatWithoutClaimingServerAuthorization(String token) {
        assertTrue(TrustedHttp.isValidApiToken(token));
    }

    @Test
    @ResourceLock("java.net.ProxySelector")
    void missingConfiguredTokenStopsApiTrafficBeforeConnecting() throws Exception {
        ProxySelector previous = ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override public List<Proxy> select(URI uri) { throw new AssertionError("Unexpected HTTP connection"); }
            @Override public void connectFailed(URI uri, SocketAddress address, IOException error) { fail("Unexpected HTTP connection"); }
        });
        try {
            String url = "https://1.1.1.1/test";
            String capturedToken = "00112233-4455-6677-8899-aabbccddeeff";
            assertNull(TrustedHttp.getBytes(url, TrustedHttp.Kind.API, 100));
            assertNull(TrustedHttp.getString(url, TrustedHttp.Kind.API, 100));
            assertFalse(TrustedHttp.postJson(url, "{}", TrustedHttp.Kind.API, capturedToken));
            assertFalse(TrustedHttp.postJson(url, "{}", TrustedHttp.Kind.API, "invalid"));
            assertFalse(TrustedHttp.postBytes(url, new byte[]{1}, "application/octet-stream", TrustedHttp.Kind.API, 100));
            assertNull(TrustedHttp.exchangeDirect(null, "GET", null, TrustedHttp.Kind.API, null, null, 100, capturedToken));
        } finally {
            ProxySelector.setDefault(previous);
        }
    }

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

    @ParameterizedTest
    @CsvSource({
        "https://example.com/feed, https://example.com/a.jar, true",
        "https://example.com/feed, https://EXAMPLE.com:443/a.jar, true",
        "https://example.com/feed, https://example.com:8443/a.jar, false",
        "https://example.com/feed, http://example.com/a.jar, false",
        "https://example.com/feed, https://evil.example.com/a.jar, false",
        "http://example.com/feed, http://example.com:80/a.jar, true"
    })
    void redirectsMustKeepTheSchemeHostAndPort(String from, String to, boolean allowed) {
        assertEquals(allowed, TrustedHttp.sameOrigin(URI.create(from), URI.create(to)));
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
