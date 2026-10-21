package com.agent1.javaagent.cli.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** 关于页内容（对齐 Android {@code AboutScreen}）。 */
public final class DesktopAboutInfo {

    private record DependencyNotice(String name, String license) {
    }

    private DesktopAboutInfo() {
    }

    public static ObjectNode toJson(ObjectMapper mapper) {
        String versionName = DesktopAboutInfo.class.getPackage().getImplementationVersion();
        if (versionName == null || versionName.isBlank()) {
            versionName = "0.1.0-SNAPSHOT";
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("title", "关于");
        root.put("appName", "Agent1");
        root.put("versionName", versionName);
        root.put("versionCode", 0);
        root.put("authorLabel", "作者");
        root.put("authorValue", "冯世浩 fengshihao");
        root.put("websiteLabel", "官网");
        root.put("websiteUrl", "https://fengshihao.github.io/agent1/");
        root.put("sourceLabel", "开源地址");
        root.put("sourceUrl", "https://github.com/fengshihao/agent1");
        root.put("licenseLabel", "本软件");
        root.put("licenseValue", "MIT");
        root.put("depsTitle", "开源依赖");
        root.put(
            "depsNotice",
            "这些库随安装包分发。Apache-2.0 与 MIT 都要求保留版权声明和许可证名称。"
        );
        ArrayNode deps = root.putArray("dependencies");
        for (DependencyNotice notice : dependencyNotices()) {
            ObjectNode item = deps.addObject();
            item.put("name", notice.name());
            item.put("license", notice.license());
        }
        return root;
    }

    private static List<DependencyNotice> dependencyNotices() {
        return List.of(
            new DependencyNotice("OkHttp", "Apache-2.0"),
            new DependencyNotice("Jackson", "Apache-2.0"),
            new DependencyNotice("RxJava", "Apache-2.0"),
            new DependencyNotice("JLine", "BSD-3-Clause"),
            new DependencyNotice("微智 Weizhi", "Apache-2.0")
        );
    }
}
