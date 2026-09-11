/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.net.InetAddress;
import java.net.URI;

/**
 * Offline self-check for the SSRF guards in {@link TrustedHttp} — the addon's only security
 * boundary, and one nothing else fails on if it silently breaks. Run with {@code ./gradlew checkSsrf}.
 * Every case uses IP literals or {@code localhost}, so it needs no network.
 */
public final class TrustedHttpSelfCheck {
    private static int failures = 0;

    private TrustedHttpSelfCheck() {}

    public static void main(String[] args) throws Exception {
        // Blocked: the addresses an attacker-supplied webhook would use to reach inside the machine.
        blocked("127.0.0.1");
        blocked("127.1.2.3");
        blocked("0.0.0.0");
        blocked("10.0.0.1");
        blocked("172.16.0.1");
        blocked("192.168.1.1");
        blocked("169.254.169.254");   // cloud metadata
        blocked("100.64.0.1");        // CGNAT
        blocked("192.0.0.1");
        blocked("198.18.0.1");        // benchmarking range
        blocked("::1");
        blocked("fd00::1");           // unique local
        blocked("fe80::1");           // link local
        blocked("::ffff:127.0.0.1");  // v4-mapped loopback

        allowed("1.1.1.1");
        allowed("2001:4860:4860::8888");

        // Hostnames resolved without DNS.
        hostRejected("localhost");
        hostRejected("anything.localhost");
        hostRejected("metadata.google.internal");
        hostRejected("db.internal");

        // URL shape: scheme, credentials, size.
        uriRejected("file:///etc/passwd", TrustedHttp.Kind.USER_WEBHOOK);
        uriRejected("ftp://example.com/x", TrustedHttp.Kind.USER_WEBHOOK);
        uriRejected("http://1.1.1.1/x", TrustedHttp.Kind.API);    // API is HTTPS-only
        uriRejected("http://1.1.1.1/x", TrustedHttp.Kind.IMAGE);  // so is IMAGE
        uriRejected("https://user:pass@1.1.1.1/x", TrustedHttp.Kind.USER_WEBHOOK);
        uriRejected("https://127.0.0.1/hook", TrustedHttp.Kind.USER_WEBHOOK);
        uriRejected("https://[::1]/hook", TrustedHttp.Kind.USER_WEBHOOK);
        uriRejected("https://1.1.1.1/" + "x".repeat(2048), TrustedHttp.Kind.USER_WEBHOOK);
        uriRejected("", TrustedHttp.Kind.USER_WEBHOOK);
        uriRejected(null, TrustedHttp.Kind.USER_WEBHOOK);

        uriAccepted("http://1.1.1.1/hook", TrustedHttp.Kind.USER_WEBHOOK);
        uriAccepted("https://1.1.1.1/hook", TrustedHttp.Kind.USER_WEBHOOK);
        uriAccepted("https://1.1.1.1/v1/x", TrustedHttp.Kind.API);

        String logged = TrustedHttp.describe(new java.io.IOException("wrap",
            new java.net.UnknownHostException("API.Secret.example")), "https://api.secret.example/v1/x");
        expect(!logged.toLowerCase(java.util.Locale.ROOT).contains("secret"), "log line must not reveal the host: " + logged);
        logged = TrustedHttp.describe(new java.io.IOException("bad https://api.secret.example/v1/x"), "https://api.secret.example/v1/x");
        expect(!logged.contains("secret") && !logged.contains("/v1/x"), "log line must not reveal the url: " + logged);

        if (failures > 0) {
            System.err.println(failures + " SSRF guard check(s) FAILED");
            System.exit(1);
        }
        System.out.println("TrustedHttp SSRF guards: all checks passed");
    }

    private static void blocked(String ip) throws Exception {
        expect(!TrustedHttp.isPublicAddress(InetAddress.getByName(ip)), "must block address " + ip);
    }

    private static void allowed(String ip) throws Exception {
        expect(TrustedHttp.isPublicAddress(InetAddress.getByName(ip)), "must allow address " + ip);
    }

    private static void hostRejected(String host) {
        expect(!TrustedHttp.isPublicHostname(host), "must reject host " + host);
    }

    private static void uriRejected(String url, TrustedHttp.Kind kind) {
        expect(TrustedHttp.parseAllowedUri(url, kind) == null, "must reject " + kind + " url " + url);
    }

    private static void uriAccepted(String url, TrustedHttp.Kind kind) {
        URI uri = TrustedHttp.parseAllowedUri(url, kind);
        expect(uri != null, "must accept " + kind + " url " + url);
    }

    private static void expect(boolean condition, String what) {
        if (!condition) {
            failures++;
            System.err.println("FAIL: " + what);
        }
    }
}
