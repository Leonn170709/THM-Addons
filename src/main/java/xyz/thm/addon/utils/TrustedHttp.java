/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.thm.addon.system.THMSystem;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Outbound HTTP that cannot be turned into a backdoor by a compromised API host or a
 * carelessly-pasted webhook URL. Responses cannot: fetch {@code file://}/other non-http(s) URLs,
 * hit loopback/RFC1918/link-local/metadata addresses (SSRF), follow a redirect off to a different
 * host, or write an unbounded payload to memory. If the system DNS/proxy path can't connect to an
 * HTTPS host, it retries resolved via 1.1.1.1 and connected directly, with the same TLS checks.
 */
public final class TrustedHttp {
    // Own logger, not THMAddon.LOG: that class static-inits against a live FabricLoader, which
    // would make this class unloadable outside the game (see TrustedHttpSelfCheck).
    private static final Logger LOG = LoggerFactory.getLogger(TrustedHttp.class);
    public static final int MAX_JSON_BYTES = 1_048_576;
    /** Screenshot attachments are far bigger than any JSON body. */
    public static final int MAX_UPLOAD_BYTES = 8_388_608;
    public static final int CONNECT_TIMEOUT_MS = 8_000;
    public static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_HEADER_LINES = 100;
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final AtomicBoolean diagnosed = new AtomicBoolean();
    // Set once 1.1.1.1 worked after the system path failed - skips the doomed system attempt from then on.
    private static volatile boolean viaDoh;

    public enum Kind {
        API,           // THM API - HTTPS only, Bearer token attached
        USER_WEBHOOK,  // player-configured webhook URL - http(s), no token ever attached
        IMAGE,         // cape/texture download - HTTPS only, no token ever attached
    }

    /** Server answered with a non-2xx status - the connection itself worked. */
    private static final class HttpStatusException extends IOException {
        HttpStatusException(int code) {
            super("HTTP " + code);
        }
    }

    /** Failed before anything was sent (DNS, TCP, TLS handshake) - safe to retry another way. */
    private static final class ConnectFailed extends IOException {
        ConnectFailed(IOException cause) {
            super(cause);
        }
    }

    private TrustedHttp() {}

    public static String getString(String url, Kind kind, int maxBytes) {
        byte[] body = getBytes(url, kind, maxBytes);
        return body == null ? null : new String(body, StandardCharsets.UTF_8);
    }

    public static byte[] getBytes(String url, Kind kind, int maxBytes) {
        return send("GET", url, kind, null, null, maxBytes, null);
    }

    public static boolean postJson(String url, String json, Kind kind, String bearerToken) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        if (!allowOutboundPost(kind, body)) return false;
        if (body.length > MAX_JSON_BYTES) {
            LOG.warn("Refusing oversized JSON POST ({} bytes)", body.length);
            return false;
        }
        return send("POST", url, kind, "application/json", body, MAX_JSON_BYTES, bearerToken) != null;
    }

    /** Same guarantees as {@link #postJson}, for a body that isn't JSON (multipart uploads). */
    public static boolean postBytes(String url, byte[] body, String contentType, Kind kind, int maxBytes) {
        if (!allowOutboundPost(kind, body)) return false;
        if (body.length > maxBytes) {
            LOG.warn("Refusing oversized POST ({} bytes)", body.length);
            return false;
        }
        return send("POST", url, kind, contentType, body, MAX_JSON_BYTES, null) != null;
    }

    private static byte[] send(String method, String url, Kind kind, String contentType, byte[] body, int maxResponseBytes, String bearerToken) {
        URI uri = parseUri(url, kind);
        if (uri == null) return null;
        boolean canFallback = kind != Kind.USER_WEBHOOK;

        if (!(canFallback && viaDoh)) {
            try {
                return exchange(method, uri, kind, contentType, body, maxResponseBytes, bearerToken);
            } catch (HttpStatusException e) {
                LOG.warn("{} {} returned {}", kind, method, e.getMessage());
                return null;
            } catch (ConnectFailed e) {
                LOG.warn("{} {} failed: {}", kind, method, describe(e.getCause(), url));
                if (!canFallback) return null;
                logDiagnosticsOnce(uri);
            } catch (Exception e) {
                LOG.warn("{} {} failed: {}", kind, method, describe(e, url));
                return null;
            }
        }

        try {
            byte[] result = exchangeViaDoh(method, uri, kind, contentType, body, maxResponseBytes, bearerToken);
            dohWorked();
            return result;
        } catch (HttpStatusException e) {
            dohWorked();
            LOG.warn("{} {} returned {} (via 1.1.1.1)", kind, method, e.getMessage());
        } catch (Exception e) {
            viaDoh = false;
            LOG.warn("{} {} failed via 1.1.1.1: {}", kind, method, describe(e, url));
        }
        return null;
    }

    private static void dohWorked() {
        if (viaDoh) return;
        viaDoh = true;
        LOG.warn("System DNS/proxy path failed but 1.1.1.1 works - using 1.1.1.1 for the rest of the session");
    }

    // A misconfigured/compromised webhook URL is the one place this addon sends data to a fully
    // player-chosen destination - refuse to let it exfiltrate the API token or cracked-account password.
    private static boolean allowOutboundPost(Kind kind, byte[] body) {
        if (body.length == 0) return true;
        // Scan the head only: a JSON body always fits, and in a multipart upload every text part
        // precedes the binary attachment - decoding a whole 8 MB PNG here would cost 16 MB of chars.
        String text = new String(body, 0, Math.min(body.length, MAX_JSON_BYTES), StandardCharsets.UTF_8);
        try {
            THMSystem system = THMSystem.get();
            if (system == null) return true;
            String password = system.getCrackedPassword();
            if (password != null && password.length() >= 3 && text.contains(password)) {
                LOG.warn("Refusing HTTP body that contains the cracked login password");
                return false;
            }
            if (kind == Kind.USER_WEBHOOK) {
                String token = system.getApiToken();
                if (token != null && token.length() >= 8 && text.contains(token)) {
                    LOG.warn("Refusing webhook body that contains the API token");
                    return false;
                }
            }
        } catch (Throwable ignored) {
            // Settings may not be loaded yet.
        }
        return true;
    }

    static URI parseAllowedUri(String raw, Kind kind) {
        URI uri = parseUri(raw, kind);
        if (uri != null && !isPublicHostname(uri.getHost())) {
            LOG.warn("Rejected {} URL: host unresolvable (DNS) or private/local", kind);
            return null;
        }
        return uri;
    }

    /** Scheme/shape checks only - no DNS. */
    private static URI parseUri(String raw, Kind kind) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2048) {
            LOG.warn("Rejected {} URL: missing or too long", kind);
            return null;
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            LOG.warn("Rejected {} URL: malformed", kind);
            return null;
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (kind == Kind.API || kind == Kind.IMAGE) {
            if (!"https".equals(scheme)) {
                LOG.warn("Rejected non-HTTPS {} URL", kind);
                return null;
            }
        } else if (!"https".equals(scheme) && !"http".equals(scheme)) {
            LOG.warn("Rejected non-HTTP(S) webhook URL");
            return null;
        }

        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
            LOG.warn("Rejected {} URL: no host or has credentials", kind);
            return null;
        }
        return uri.normalize();
    }

    static boolean isPublicHostname(String host) {
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

    static boolean isPublicAddress(InetAddress addr) {
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
        String bearerToken
    ) throws Exception {
        URI current = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (current.getHost() == null || !isPublicHostname(current.getHost())) {
                throw new ConnectFailed(new UnknownHostException("host unresolvable (DNS) or private/local address"));
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
                }
                try {
                    cn.connect();
                } catch (IOException e) {
                    throw new ConnectFailed(e);
                }
                if (requestBody != null) {
                    try (var os = cn.getOutputStream()) {
                        os.write(requestBody);
                    }
                }

                int code = cn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                    String location = cn.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        LOG.warn("{} HTTP {} redirect without Location", kind, code);
                        return null;
                    }
                    URI allowed = parseAllowedUri(current.resolve(location).toString(), kind);
                    if (allowed == null) return null;
                    if (!current.getHost().equalsIgnoreCase(allowed.getHost())) {
                        LOG.warn("Rejected cross-host {} redirect", kind);
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
                if (code < 200 || code >= 300) throw new HttpStatusException(code);
                return body;
            } finally {
                cn.disconnect();
            }
        }
        LOG.warn("Too many HTTP redirects");
        return null;
    }

    private static byte[] exchangeViaDoh(String method, URI uri, Kind kind, String contentType, byte[] body, int maxResponseBytes, String bearerToken) throws Exception {
        InetAddress ip = resolveViaDoh(uri.getHost()).stream()
            .filter(TrustedHttp::isPublicAddress)
            .findFirst()
            .orElseThrow(() -> new UnknownHostException("1.1.1.1 returned no public address"));
        return exchangeDirect(ip, method, uri, kind, contentType, body, maxResponseBytes, bearerToken);
    }

    // ponytail: HTTP/1.1 over a raw TLS socket, no redirects - add them if the API ever redirects.
    static byte[] exchangeDirect(InetAddress ip, String method, URI uri, Kind kind, String contentType, byte[] body, int maxResponseBytes, String bearerToken) throws Exception {
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 443 : uri.getPort();
        try (Socket tcp = new Socket(Proxy.NO_PROXY)) {
            tcp.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
            tcp.setSoTimeout(READ_TIMEOUT_MS);
            SSLSocket ssl = (SSLSocket) HttpsURLConnection.getDefaultSSLSocketFactory().createSocket(tcp, host, port, true);
            SSLParameters params = ssl.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS"); // certificate must match the hostname, not the IP
            ssl.setSSLParameters(params);
            ssl.startHandshake();

            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            StringBuilder head = new StringBuilder()
                .append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(port == 443 ? host : host + ":" + port).append("\r\n")
                .append("User-Agent: Java/").append(System.getProperty("java.version")).append("\r\n")
                .append("Connection: close\r\n");
            if (kind == Kind.API) {
                String token = bearerToken != null && !bearerToken.isEmpty() ? bearerToken : apiToken();
                if (token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0) throw new IOException("API token contains a line break");
                if (!token.isEmpty()) head.append("Authorization: Bearer ").append(token).append("\r\n");
            }
            if (body != null) {
                head.append("Content-Type: ").append(contentType).append("\r\n")
                    .append("Content-Length: ").append(body.length).append("\r\n");
            }
            head.append("\r\n");

            OutputStream out = ssl.getOutputStream();
            out.write(head.toString().getBytes(StandardCharsets.UTF_8));
            if (body != null) out.write(body);
            out.flush();

            InputStream in = new BufferedInputStream(ssl.getInputStream());
            String[] status = readLine(in).split(" ", 3);
            if (status.length < 2) throw new IOException("malformed HTTP status line");
            int code = Integer.parseInt(status[1]);
            long contentLength = -1;
            boolean chunked = false;
            int lines = 0;
            for (String line; !(line = readLine(in)).isEmpty(); ) {
                if (++lines > MAX_HEADER_LINES) throw new IOException("too many response headers");
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if (name.equals("transfer-encoding")) chunked = value.toLowerCase(Locale.ROOT).contains("chunked");
                else if (name.equals("content-length")) contentLength = Long.parseLong(value);
            }
            if (code < 200 || code >= 300) throw new HttpStatusException(code);
            if (chunked) return readChunked(in, maxResponseBytes);
            if (contentLength > maxResponseBytes) throw new IllegalStateException("response exceeded " + maxResponseBytes + " bytes");
            return contentLength >= 0 ? readExactly(in, (int) contentLength) : readLimited(in, maxResponseBytes);
        }
    }

    /** A records for {@code host} from Cloudflare DNS-over-HTTPS, bypassing the system resolver and proxy. */
    static List<InetAddress> resolveViaDoh(String host) throws Exception {
        URI doh = URI.create("https://1.1.1.1/dns-query?type=A&name=" + URLEncoder.encode(host, StandardCharsets.UTF_8));
        HttpURLConnection cn = (HttpURLConnection) doh.toURL().openConnection(Proxy.NO_PROXY);
        try {
            cn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            cn.setReadTimeout(READ_TIMEOUT_MS);
            cn.setRequestProperty("Accept", "application/dns-json");
            int code = cn.getResponseCode();
            if (code != 200) throw new IOException("1.1.1.1 DNS returned HTTP " + code);
            JsonObject root;
            try (InputStream in = cn.getInputStream()) {
                root = JsonParser.parseString(new String(readLimited(in, 65_536), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            List<InetAddress> addresses = new ArrayList<>();
            JsonArray answers = root.getAsJsonArray("Answer");
            if (answers != null) {
                for (JsonElement answer : answers) {
                    JsonObject o = answer.getAsJsonObject();
                    String data = o.get("data").getAsString();
                    if (o.get("type").getAsInt() == 1 && IPV4.matcher(data).matches()) addresses.add(InetAddress.getByName(data));
                }
            }
            if (addresses.isEmpty()) throw new UnknownHostException("1.1.1.1 DNS returned no address");
            return addresses;
        } finally {
            cn.disconnect();
        }
    }

    // Once per session on the first connection failure. Never logs the host, URL, or IPs.
    private static void logDiagnosticsOnce(URI uri) {
        if (!diagnosed.compareAndSet(false, true)) return;
        StringBuilder sb = new StringBuilder("Network diagnostics: java=").append(System.getProperty("java.version"))
            .append(" (").append(System.getProperty("java.vendor")).append("), os=").append(System.getProperty("os.name"));
        try {
            sb.append(", tls=").append(Arrays.toString(SSLContext.getDefault().getDefaultSSLParameters().getProtocols()));
        } catch (Exception e) {
            sb.append(", tls=").append(e.getClass().getSimpleName());
        }
        sb.append(", ssl-factory=").append(HttpsURLConnection.getDefaultSSLSocketFactory().getClass().getName());
        try {
            ProxySelector selector = ProxySelector.getDefault();
            sb.append(", proxy=").append(selector == null ? "none" : selector.select(uri));
        } catch (Exception e) {
            sb.append(", proxy=").append(e.getClass().getSimpleName());
        }
        for (String prop : new String[]{"https.proxyHost", "socksProxyHost", "java.net.useSystemProxies", "jdk.tls.client.protocols", "https.protocols"}) {
            String value = System.getProperty(prop);
            if (value != null) sb.append(", ").append(prop).append('=').append(value);
        }
        List<InetAddress> system;
        List<InetAddress> doh;
        try {
            system = Arrays.asList(InetAddress.getAllByName(uri.getHost()));
        } catch (Exception e) {
            system = List.of();
        }
        try {
            doh = resolveViaDoh(uri.getHost());
        } catch (Exception e) {
            doh = List.of();
        }
        sb.append(", system-dns=").append(classify(system))
            .append(", 1.1.1.1-dns=").append(classify(doh))
            .append(", dns-match=").append(system.stream().anyMatch(doh::contains));
        LOG.warn(sb.toString());
    }

    private static String classify(List<InetAddress> addresses) {
        if (addresses.isEmpty()) return "unresolved";
        return addresses.stream().map(a -> isPublicAddress(a) ? "public" : "private").toList().toString();
    }

    // Exception type + message (TLS alerts, timeouts) with the URL and host redacted - the API host must never hit the log.
    static String describe(Throwable e, String url) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (root != e) msg += " (cause " + root.getClass().getSimpleName() + ": " + root.getMessage() + ")";
        if (url == null || url.isBlank()) return msg;
        msg = msg.replace(url.trim(), "<url>");
        try {
            String host = URI.create(url.trim()).getHost();
            if (host != null && !host.isEmpty()) msg = msg.replaceAll("(?i)" + Pattern.quote(host), "<host>");
        } catch (IllegalArgumentException ignored) {
        }
        return msg;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int b; (b = in.read()) != '\n'; ) {
            if (b == -1) throw new EOFException("connection closed mid-response");
            if (b != '\r') sb.append((char) b);
            if (sb.length() > 8192) throw new IOException("response line too long");
        }
        return sb.toString();
    }

    private static byte[] readChunked(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String line = readLine(in);
            int semi = line.indexOf(';');
            int size = Integer.parseInt((semi >= 0 ? line.substring(0, semi) : line).trim(), 16);
            if (size == 0) return out.toByteArray();
            if (size < 0 || out.size() + (long) size > maxBytes) throw new IllegalStateException("response exceeded " + maxBytes + " bytes");
            out.write(readExactly(in, size));
            readLine(in);
        }
    }

    private static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] bytes = in.readNBytes(n);
        if (bytes.length < n) throw new EOFException("connection closed mid-response");
        return bytes;
    }

    private static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
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
