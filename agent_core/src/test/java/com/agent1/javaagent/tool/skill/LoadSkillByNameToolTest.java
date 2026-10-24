package com.agent1.javaagent.tool.skill;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.skill.SkillsBundledInstall;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoadSkillByNameToolTest {

    @TempDir
    Path agentRoot;

    @Test
    void loadsTravelItineraryAfterBundledInstall() throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        SkillsBundledInstall.syncFromClasspath(agentRoot);
        LoadSkillByNameTool tool = new LoadSkillByNameTool(agentRoot, agentRoot);
        String text = tool.load("travel-itinerary");
        assertTrue(text.contains("[skill] travel-itinerary loaded"), text);
        assertTrue(text.contains("skills_home"), text);
        assertTrue(text.contains("load_skill"), text);
    }
}
