/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

// Messages never print the URLs: with a real secrets.properties they are secret.
class GeneratedApiEndpointsTest {
    @Test
    void everyEndpointDecryptsToTheBuildSecret() throws IOException {
        Path secrets = Path.of("secrets.properties");
        if (!Files.exists(secrets)) secrets = Path.of("secrets.properties.example");
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(secrets, StandardCharsets.UTF_8)) {
            props.load(r);
        }

        Map<String, Supplier<String>> endpoints = Map.of(
            "api.memberHud", GeneratedApiEndpoints::memberHudUrl,
            "api.highway", GeneratedApiEndpoints::highwayUrl,
            "api.status", GeneratedApiEndpoints::statusUrl,
            "api.highwayStatus", GeneratedApiEndpoints::highwayStatusUrl,
            "api.cape", GeneratedApiEndpoints::capeListUrl,
            "api.capePost", GeneratedApiEndpoints::capePostUrl,
            "api.capeIndex", GeneratedApiEndpoints::capeIndexUrl
        );
        for (var e : endpoints.entrySet()) {
            String url = e.getValue().get();
            assertNotNull(url, e.getKey() + " failed to decrypt");
            assertTrue(url.startsWith("https://"), e.getKey() + " is not https");
            assertTrue(url.equals(props.getProperty(e.getKey())), e.getKey() + " decrypts to the wrong value");
        }
    }
}
