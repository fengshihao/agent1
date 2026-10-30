package com.agent1.javaagent.skill;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** {@code load_skill} 前确保 skill 在 skills-home；本地无则从 registry 下载。 */
public final class SkillsRegistryInstall {

    private SkillsRegistryInstall() {
    }

    /** 已安装则直接返回；否则从 registry 拉 zip 并自动刷 skill 能力卡。残缺安装会触发重装。 */
    public static Path ensure(Path agentRoot, String name) throws IOException {
        if (agentRoot == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("agentRoot and name required");
        }
        String trimmed = name.trim();
        if (!SkillRegistryClient.disabled()) {
            SkillRegistryClient.ensureInstalled(agentRoot, List.of(trimmed));
            Path dir = SkillsHomeInstaller.installedDir(agentRoot, trimmed);
            if (dir == null) {
                throw new IOException("skill not installed after registry fetch: " + trimmed);
            }
            return dir;
        }
        Path existing = SkillsHomeInstaller.installedDir(agentRoot, trimmed);
        if (existing != null) {
            return existing;
        }
        throw new IOException("skill not installed and registry disabled: " + trimmed);
    }
}
