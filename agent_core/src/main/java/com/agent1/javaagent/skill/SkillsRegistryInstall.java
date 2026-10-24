package com.agent1.javaagent.skill;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** {@code load_skill} 前确保 skill 在 skills-home；本地无则优先从 registry 下载。 */
public final class SkillsRegistryInstall {

    private SkillsRegistryInstall() {
    }

    /** 已安装则直接返回；否则先 registry 拉 zip，失败再 classpath bundled 兜底。 */
    public static Path ensure(Path agentRoot, String name) throws IOException {
        if (agentRoot == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("agentRoot and name required");
        }
        String trimmed = name.trim();
        Path existing = SkillsHomeInstaller.installedDir(agentRoot, trimmed);
        if (existing != null) {
            return existing;
        }
        if (!SkillRegistryClient.disabled()) {
            try {
                SkillRegistryClient.ensureInstalled(agentRoot, List.of(trimmed));
            } catch (IOException e) {
                if (!SkillsBundledInstall.isBundled(trimmed)) {
                    throw e;
                }
            }
            Path dir = SkillsHomeInstaller.installedDir(agentRoot, trimmed);
            if (dir != null) {
                return dir;
            }
        }
        if (SkillsBundledInstall.isBundled(trimmed)) {
            SkillsBundledInstall.ensureBundled(agentRoot, trimmed);
            Path dir = SkillsHomeInstaller.installedDir(agentRoot, trimmed);
            if (dir != null) {
                return dir;
            }
        }
        throw new IOException("skill not installed: " + trimmed);
    }
}
