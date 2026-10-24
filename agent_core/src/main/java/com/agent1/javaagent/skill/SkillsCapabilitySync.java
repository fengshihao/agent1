package com.agent1.javaagent.skill;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.capability.CapabilityRecord;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 将 {@link SkillsHomeLoader} 已装 skill 写入 find_caps（{@code source=skills_home}）。 */
public final class SkillsCapabilitySync {

    public static final String SOURCE = "skills_home";

    private static final Logger LOG = Logger.getLogger(SkillsCapabilitySync.class.getName());

    private SkillsCapabilitySync() {
    }

    public static void syncCapabilityCards(Path agentRoot) {
        if (agentRoot == null) {
            return;
        }
        try {
            CapabilityIndexStore.replaceSource(
                agentRoot,
                SOURCE,
                cardsFor(SkillsHomeLoader.loadInstalled(agentRoot))
            );
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "sync skills_home cards failed", e);
        }
    }

    static List<CapabilityRecord> cardsFor(List<AgentSkill> skills) {
        return PackageSkills.cardsFor(skills).stream()
            .map(
                record ->
                    new CapabilityRecord(
                        record.id(),
                        record.kind(),
                        record.title(),
                        record.summary(),
                        record.tags(),
                        record.platforms(),
                        record.entry(),
                        record.docPath(),
                        record.docAnchor(),
                        record.requiresJson(),
                        SOURCE,
                        1.25
                    )
            )
            .toList();
    }
}
