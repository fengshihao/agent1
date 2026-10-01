package com.agent1.javaagent.web;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/** 只允许公网 http(s)。本机、链路本地、私网和云元数据地址一律拒绝。 */
public final class PublicHttpUrl {

    private PublicHttpUrl() {
    }

    public static URI parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("url 不能为空");
        }
        final URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("url 无法解析");
        }
        String scheme = uri.getScheme();
        if (scheme == null
            || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("只支持 http 或 https");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
            throw new IllegalArgumentException("url 不能包含用户名或密码");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("url 缺少主机名");
        }
        return uri;
    }

    public static void checkPublic(URI uri, boolean allowLoopback) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (isBlockedName(host, allowLoopback)) {
            throw new IllegalArgumentException("拒绝访问本机或内网地址");
        }
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析主机");
        }
        if (addresses.length == 0) {
            throw new IllegalArgumentException("无法解析主机");
        }
        for (InetAddress address : addresses) {
            if (isDisallowed(address, allowLoopback)) {
                throw new IllegalArgumentException("拒绝访问本机或内网地址");
            }
        }
    }

    private static boolean isBlockedName(String host, boolean allowLoopback) {
        if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost")) {
            return true;
        }
        if ("localhost".equals(host) || "localhost.localdomain".equals(host)) {
            return !allowLoopback;
        }
        return false;
    }

    public static boolean isDisallowed(InetAddress address, boolean allowLoopback) {
        byte[] raw = address.getAddress();
        if (raw.length == 16 && isIpv4Mapped(raw)) {
            try {
                return isDisallowed(InetAddress.getByAddress(new byte[] {
                    raw[12], raw[13], raw[14], raw[15]
                }), allowLoopback);
            } catch (UnknownHostException e) {
                return true;
            }
        }
        if (allowLoopback && address.isLoopbackAddress()) {
            return false;
        }
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        if (raw.length == 4) {
            int b0 = raw[0] & 0xff;
            int b1 = raw[1] & 0xff;
            if (b0 == 0 || b0 >= 240) {
                return true;
            }
            return b0 == 100 && (b1 & 0xc0) == 64;
        }
        if (raw.length == 16) {
            int b0 = raw[0] & 0xff;
            return (b0 & 0xfe) == 0xfc;
        }
        return true;
    }

    private static boolean isIpv4Mapped(byte[] raw) {
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return (raw[10] & 0xff) == 0xff && (raw[11] & 0xff) == 0xff;
    }
}
