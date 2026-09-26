package com.agent1.javaagent.weizhi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** 在加载 {@link com.weizhi.WeizhiEngine} 之前显式加载 libweizhijni。 */
public final class WeizhiJniBootstrap {

    private static volatile boolean loaded;

    private WeizhiJniBootstrap() {
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * @return true 若 JNI 已可用（本进程内幂等）
     */
    public static synchronized boolean tryLoad(Path searchRoot) {
        if (loaded) {
            return true;
        }
        Path lib = locateNativeLibrary(searchRoot);
        if (lib == null) {
            return false;
        }
        try {
            System.load(lib.toAbsolutePath().normalize().toString());
            loaded = true;
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static Path locateNativeLibrary(Path searchRoot) {
        if (searchRoot == null) {
            return null;
        }
        Path root = searchRoot.toAbsolutePath().normalize();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
        String[] candidates;
        if (os.contains("mac")) {
            candidates = new String[] {"libweizhijni.dylib", "libweizhijni.jnilib"};
        } else if (os.contains("linux")) {
            candidates = new String[] {"libweizhijni.so"};
        } else if (os.contains("win")) {
            candidates = new String[] {"weizhijni.dll"};
        } else {
            candidates = new String[] {"libweizhijni.so", "libweizhijni.dylib"};
        }
        for (String name : candidates) {
            Path direct = root.resolve(name);
            if (Files.isRegularFile(direct)) {
                return direct;
            }
        }
        Path build = root.resolve("build");
        for (String name : candidates) {
            Path nested = build.resolve(name);
            if (Files.isRegularFile(nested)) {
                return nested;
            }
        }
        return null;
    }
}
