package com.agent1.javaagent.skill;

import java.nio.file.Path;

/** {@code <agentRoot>/skills-home} 布局（与 weizhi-home 分离）。 */
public final class SkillsHomePaths {

    public static final String HOME_DIR = "skills-home";
    public static final String SKILLS_DIR = "skills";
    public static final String INDEX_FILE = "skills-index.json";

    private SkillsHomePaths() {
    }

    public static Path home(Path agentRoot) {
        if (agentRoot == null) {
            throw new IllegalArgumentException("agentRoot");
        }
        return agentRoot.toAbsolutePath().normalize().resolve(HOME_DIR);
    }

    public static Path skillsDir(Path agentRoot) {
        return home(agentRoot).resolve(SKILLS_DIR);
    }

    public static Path indexFile(Path agentRoot) {
        return home(agentRoot).resolve(INDEX_FILE);
    }
}
