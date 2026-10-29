package com.agent1.javaagent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OfficialCdnTest {

    @Test
    void resolveHostPrefersBaseUrlThenHostThenConfigured() {
        assertEquals(
            OfficialCdn.UNCONFIGURED_HOST,
            OfficialCdn.resolveHost("", "", "", "", "", null));
        assertEquals(
            "from.env",
            OfficialCdn.resolveHost("https://from.env/", "https://alias.example/", "ignored.host", "", "", "cfg.host"));
        assertEquals(
            "alias.example",
            OfficialCdn.resolveHost("", "https://alias.example/path", "", "", "", "cfg.host"));
        assertEquals("env.host", OfficialCdn.resolveHost("", "", "ENV.HOST", "", "", "cfg.host"));
        assertEquals("prop.base", OfficialCdn.resolveHost("", "", "", "https://prop.base/", "prop.host", "cfg.host"));
        assertEquals("prop.host", OfficialCdn.resolveHost("", "", "", "", "prop.host", "cfg.host"));
        assertEquals("cfg.host", OfficialCdn.resolveHost("", "", "", "", "", "cfg.host"));
    }

    @Test
    void parseHostAcceptsUrlOrBareHost() {
        assertEquals("cdn.example.test", OfficialCdn.parseHost("https://cdn.example.test/"));
        assertEquals("cdn.example.test", OfficialCdn.parseHost("https://cdn.example.test/packages/"));
        assertEquals("cdn.example.test", OfficialCdn.parseHost("CDN.EXAMPLE.TEST"));
        assertEquals("cdn.example.test", OfficialCdn.parseHost("cdn.example.test/foo"));
        assertThrows(IllegalArgumentException.class, () -> OfficialCdn.parseHost("ftp://cdn.example.test/"));
        assertThrows(IllegalArgumentException.class, () -> OfficialCdn.parseHost("https://user:pass@cdn.example.test/"));
        assertThrows(IllegalArgumentException.class, () -> OfficialCdn.parseHost(""));
    }

    @Test
    void httpsJoinsOfficialPaths() {
        assertTrue(OfficialCdn.baseUrl().startsWith("https://"));
        assertTrue(OfficialCdn.baseUrl().endsWith("/"));
        assertTrue(OfficialCdn.webLibsRegistryUrl().endsWith("/web-libs/registry/stable.json"));
        assertTrue(OfficialCdn.packagesRegistryUrl().endsWith("/packages/registry/stable.json"));
        assertTrue(OfficialCdn.genericRegistryUrl().endsWith("/registry/stable.json"));
        assertTrue(OfficialCdn.skillsRegistryUrl().endsWith("/skills/registry/stable.json"));
        assertTrue(OfficialCdn.hostWebRegistryUrl().endsWith("/host-web/registry/stable.json"));
        assertTrue(OfficialCdn.docxPreviewShellUrl().contains("/shell.html?v="));
        assertTrue(OfficialCdn.molanEmbedShellUrl().contains("/molan-embed/"));
        assertTrue(OfficialCdn.https("/foo/bar").endsWith("/foo/bar"));
    }

    @Test
    void loopbackAndOfficialHostAreAllowed() {
        assertTrue(OfficialCdn.isLoopback("127.0.0.1"));
        assertTrue(OfficialCdn.isLoopback("localhost"));
        assertTrue(OfficialCdn.isAllowedHost("127.0.0.1"));
        assertFalse(OfficialCdn.isAllowedHost("evil.invalid"));
        assertFalse(OfficialCdn.isAllowedHost(""));
        assertTrue(OfficialCdn.isAllowedHost(OfficialCdn.host()));
    }
}
