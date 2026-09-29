package com.agent1.android.productivity.logic.business.platform;

import android.content.Context;
import com.agent1.javaagent.catalog.AgentCatalogPaths;
import com.agent1.javaagent.catalog.OfficeCatalogScripts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 从 APK {@code assets/office/} 同步 docx 脚本到 agentRoot catalog（weizhi#8）。 */
public final class AndroidOfficeCatalogSync {

    private AndroidOfficeCatalogSync() {
    }

    public static void ensureFromAssets(Context context, Path agentRoot) {
        if (context == null || agentRoot == null) {
            return;
        }
        Path scripts = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        try {
            Files.createDirectories(scripts);
        } catch (IOException e) {
            throw new IllegalStateException("create scripts dir: " + scripts, e);
        }
        for (String name : OfficeCatalogScripts.OFFICE_SCRIPT_NAMES) {
            Path target = scripts.resolve(name);
            try (InputStream in = context.getAssets().open("office/" + name)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException("copy asset office/" + name, e);
            }
        }
    }
}
