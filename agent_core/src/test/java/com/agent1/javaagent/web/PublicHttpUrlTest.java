package com.agent1.javaagent.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class PublicHttpUrlTest {

    @Test
    void rejectsNonHttpAndUserInfo() {
        assertMessage("file:///etc/passwd", "只支持 http");
        assertMessage("javascript:alert(1)", "只支持 http");
        assertMessage("http://user:secret@203.0.113.5/a", "用户名或密码");
        assertMessage("   ", "不能为空");
    }

    @Test
    void rejectsPrivateAndLoopbackLiterals() {
        assertMessage("http://127.0.0.1/", "拒绝访问");
        assertMessage("http://10.1.2.3/a", "拒绝访问");
        assertMessage("http://192.168.1.8/", "拒绝访问");
        assertMessage("http://172.16.0.4/", "拒绝访问");
        assertMessage("http://169.254.169.254/latest", "拒绝访问");
        assertMessage("http://0.0.0.0/", "拒绝访问");
        assertMessage("http://100.64.1.2/", "拒绝访问");
        assertMessage("http://[::1]/", "拒绝访问");
        assertMessage("http://[fc00::1]/", "拒绝访问");
        assertMessage("http://foo.internal/path", "拒绝访问");
        assertMessage("http://localhost/x", "拒绝访问");
    }

    @Test
    void allowsPublicLiteralAndLoopbackWhenAsked() {
        assertDoesNotThrow(() -> PublicHttpUrl.checkPublic(PublicHttpUrl.parse("http://203.0.113.5/a"), false));
        assertDoesNotThrow(() -> PublicHttpUrl.checkPublic(PublicHttpUrl.parse("http://127.0.0.1:9/a"), true));
    }

    @Test
    void unwrapsIpv4MappedLoopback() throws Exception {
        InetAddress mapped = InetAddress.getByName("::ffff:127.0.0.1");
        assertTrue(PublicHttpUrl.isDisallowed(mapped, false));
    }

    private static void assertMessage(String url, String part) {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> {
                URI uri = PublicHttpUrl.parse(url);
                PublicHttpUrl.checkPublic(uri, false);
            }
        );
        assertTrue(ex.getMessage().contains(part), ex.getMessage());
    }
}
