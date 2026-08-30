/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Outbound HTTP that cannot be turned into a backdoor by a compromised API host or a
 * carelessly-pasted webhook URL. Responses cannot: fetch {@code file://}/other non-http(s) URLs,
 * hit loopback/RFC1918/link-local/metadata addresses (SSRF), follow a redirect off to a different
 * host, or write an unbounded payload to memory.
 */
public final class TrustedHttp {
    public static final int MAX_JSON_BYTES = 1_048_576;
    public static final int CONNECT_TIMEOUT_MS = 8_000;
    public static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_REDIRECTS = 3;

    public enum Kind {
        API,           // THM API - HTTPS only, Bearer token attached
        USER_WEBHOOK,  // player-configured webhook URL - http(s), no token ever attached
        IMAGE,         // cape/texture download - HTTPS only, no token ever attached
    }

    private TrustedHttp() {}

    public static String getString(String url, Kind kind, int maxBytes) {
        byte[] body = getBytes(url, kind, maxBytes);
        return body == null ? null : new String(body, StandardCharsets.UTF_8);
    }

    public static byte[] getBytes(String url, Kind kind, int maxBytes) {
        try {
            URI uri = parseAllowedUri(url, kind);
            if (uri == null) return null;
            return exchange("GET", uri, kind, null, null, maxBytes, false, null);
        } catch (Exception e) {
            THMAddon.LOG.warn("Trusted HTTP GET failed: {}", e.getMessage());
            return null;
        }
    }

    public static boolean postJson(String url, String json, Kind kind, String bearerToken) {
        try {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            if (!allowOutboundPost(kind, body)) return false;
            if (body.length > MAX_JSON_BYTES) {
                THMAddon.LOG.warn("Refusing oversized JSON POST ({} bytes)", body.length);
                return false;
            }
            URI uri = parseAllowedUri(url, kind);
            if (uri == null) return false;
            exchange("POST", uri, kind, "application/json", body, MAX_JSON_BYTES, true, bearerToken);
            return true;
        } catch (Exception e) {
            THMAddon.LOG.warn("Trusted HTTP POST failed: {}", e.getMessage());
            return false;
        }
    }

    // A misconfigured/compromised webhook URL is the one place this addon sends data to a fully
    // player-chosen destination - refuse to let it exfiltrate the API token or cracked-account password.
    private static boolean allowOutboundPost(Kind kind, byte[] body) {
        if (body.length == 0) return true;
        String text = new String(body, StandardCharsets.UTF_8);
        try {
            THMSystem system = THMSystem.get();
            if (system == null) return true;
            String password = system.getCrackedPassword();
            if (password != null && password.length() >= 3 && text.contains(password)) {
                THMAddon.LOG.warn("Refusing HTTP body that contains the cracked login password");
                return false;
            }
            if (kind == Kind.USER_WEBHOOK) {
                String token = system.getApiToken();
                if (token != null && token.length() >= 8 && text.contains(token)) {
                    THMAddon.LOG.warn("Refusing webhook body that contains the API token");
                    return false;
                }
            }
        } catch (Throwable ignored) {
            // Settings may not be loaded yet.
        }
        return true;
    }

    private static URI parseAllowedUri(String raw, Kind kind) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2048) return null;

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (kind == Kind.API || kind == Kind.IMAGE) {
            if (!"https".equals(scheme)) {
                THMAddon.LOG.warn("Rejected non-HTTPS {} URL", kind);
                return null;
            }
        } else if (!"https".equals(scheme) && !"http".equals(scheme)) {
            THMAddon.LOG.warn("Rejected non-HTTP(S) webhook URL");
            return null;
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) return null;
        if (uri.getUserInfo() != null) return null;
        if (!isPublicHostname(uri.getHost())) {
            THMAddon.LOG.warn("Rejected URL host that resolves to a private or local address");
            return null;
        }
        return uri.normalize();
    }

    private static boolean isPublicHostname(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.isEmpty() || h.equals("localhost") || h.endsWith(".localhost")) return false;
        if (h.equals("metadata.google.internal") || h.endsWith(".internal")) return false;

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(h);
        } catch (UnknownHostException e) {
            return false;
        }
        if (addresses.length == 0) return false;
        for (InetAddress addr : addresses) {
            if (!isPublicAddress(addr)) return false;
        }
        return true;
    }

    private static boolean isPublicAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
            || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return false;
        }
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            int a = raw[0] & 0xFF;
            int b = raw[1] & 0xFF;
            if (a == 0) return false;
            if (a == 100 && b >= 64 && b <= 127) return false; // 100.64/10 CGNAT
            if (a == 169 && b == 254) return false;
            if (a == 192 && b == 0) return false;
            if (a == 198 && (b == 18 || b == 19)) return false;
        }
        if (raw.length == 16) {
            if ((raw[0] & 0xFE) == 0xFC) return false; // unique local fc00::/7
            boolean v4mapped = true;
            for (int i = 0; i < 10; i++) if (raw[i] != 0) { v4mapped = false; break; }
            if (v4mapped && raw[10] == (byte) 0xFF && raw[11] == (byte) 0xFF) {
                try {
                    return isPublicAddress(InetAddress.getByAddress(new byte[]{raw[12], raw[13], raw[14], raw[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
        }
        return true;
    }

    private static byte[] exchange(
        String method,
        URI start,
        Kind kind,
        String contentType,
        byte[] requestBody,
        int maxResponseBytes,
        boolean discardBody,
        String bearerToken
    ) throws Exception {
        URI current = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (current.getHost() == null || !isPublicHostname(current.getHost())) {
                THMAddon.LOG.warn("Rejected URL host that resolves to a private or local address");
                return null;
            }

            HttpURLConnection cn = (HttpURLConnection) current.toURL().openConnection();
            try {
                cn.setInstanceFollowRedirects(false);
                cn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                cn.setReadTimeout(READ_TIMEOUT_MS);
                cn.setRequestMethod(method);
                cn.setUseCaches(false);
                if (contentType != null) cn.setRequestProperty("Content-Type", contentType);
                if (kind == Kind.API) {
                    // Every API request needs this, GET included - the backend requires a valid
                    // token on every route now, not just writes.
                    String token = bearerToken != null && !bearerToken.isEmpty() ? bearerToken : apiToken();
                    if (!token.isEmpty()) cn.setRequestProperty("Authorization", "Bearer " + token);
                }
                if (requestBody != null) {
                    cn.setDoOutput(true);
                    cn.setFixedLengthStreamingMode(requestBody.length);
                    try (var os = cn.getOutputStream()) {
                        os.write(requestBody);
                    }
                }

                int code = cn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                    String location = cn.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        THMAddon.LOG.warn("HTTP redirect without Location from {}", current.getHost());
                        return null;
                    }
                    URI allowed = parseAllowedUri(current.resolve(location).toString(), kind);
                    if (allowed == null) return null;
                    if (!current.getHost().equalsIgnoreCase(allowed.getHost())) {
                        THMAddon.LOG.warn("Rejected cross-host HTTP redirect from {} to {}", current.getHost(), allowed.getHost());
                        return null;
                    }
                    current = allowed;
                    continue;
                }

                InputStream raw = code >= 400 ? cn.getErrorStream() : cn.getInputStream();
                byte[] body = new byte[0];
                if (raw != null) {
                    try (InputStream stream = raw) {
                        body = readLimited(stream, maxResponseBytes);
                    }
                }
                if (discardBody) {
                    if (code != 200 && code != 204) {
                        throw new java.io.IOException("HTTP " + method + " " + current.getHost() + " returned " + code);
                    }
                    return body;
                }
                if (code != 200) {
                    THMAddon.LOG.warn("HTTP GET {} returned {}", current.getHost(), code);
                    return null;
                }
                return body;
            } finally {
                cn.disconnect();
            }
        }
        THMAddon.LOG.warn("Too many HTTP redirects");
        return null;
    }

    private static byte[] readLimited(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) throw new IllegalStateException("response exceeded " + maxBytes + " bytes");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String apiToken() {
        try {
            THMSystem system = THMSystem.get();
            return system == null ? "" : system.getApiToken();
        } catch (Throwable t) {
            return "";
        }
    }
}
