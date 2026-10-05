package com.agent1.javaagent.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 能力检索字段权重：title &gt; tags &gt; summary/entry（FTS bm25 + LIKE 回退）。 */
class CapabilityIndexRankingTest {

    private static final String TOKEN = "ranktoken42";

    @TempDir
    Path temp;

    Path agentRoot;
    Path dbPath;

    @BeforeEach
    void setUp() {
        agentRoot = temp.resolve("agentRoot");
        dbPath = CapabilityDatabasePaths.databaseFile(agentRoot);
        CapabilityIndexStore.rebuild(
            agentRoot,
            List.of(
                rec("rank.title", "Capability " + TOKEN, "generic summary", ""),
                rec("rank.tags", "Generic title", "generic summary", TOKEN + ",misc"),
                rec("rank.summary", "Generic title", "paragraph with " + TOKEN + " inside", ""),
                rec("rank.entry", "Generic title", "generic", "", "path/" + TOKEN + ".js", 1.0)
            )
        );
    }

    @Test
    void ftsPrefersTitleOverTagsSummaryAndEntry() {
        List<CapabilityIndexStore.CapabilityHit> hits =
            CapabilityIndexStore.search(agentRoot, TOKEN, List.of("test"), "any", 10);
        assertFalse(hits.isEmpty());
        assertEquals("rank.title", hits.get(0).id());
        assertTrue(indexOf(hits, "rank.title") < indexOf(hits, "rank.tags"));
        assertTrue(indexOf(hits, "rank.tags") < indexOf(hits, "rank.summary"));
        assertTrue(indexOf(hits, "rank.summary") < indexOf(hits, "rank.entry"));
    }

    @Test
    void ftsTitleBeatsTagsWhenOnlyThoseFieldsMatch() {
        CapabilityIndexStore.rebuild(
            agentRoot,
            List.of(
                rec("only.title", "prefix " + TOKEN, "no hit here", "other,words"),
                rec("only.tags", "unrelated name", "no hit here", TOKEN + ",other")
            )
        );
        var hits = CapabilityIndexStore.search(agentRoot, TOKEN, List.of(), "any", 5);
        assertEquals("only.title", hits.get(0).id());
    }

    @Test
    void ftsTagsBeatSummaryWhenOnlyThoseFieldsMatch() {
        CapabilityIndexStore.rebuild(
            agentRoot,
            List.of(
                rec("only.tags", "unrelated name", "no hit here", TOKEN + ",other"),
                rec("only.summary", "unrelated name", "body " + TOKEN, "")
            )
        );
        var hits = CapabilityIndexStore.search(agentRoot, TOKEN, List.of(), "any", 5);
        assertEquals("only.tags", hits.get(0).id());
    }

    @Test
    void likeFallbackUsesSameFieldPriority() {
        List<CapabilityIndexStore.CapabilityHit> hits =
            CapabilityIndexStore.searchLike(dbPath, TOKEN, List.of("test"), "any", 10);
        assertFalse(hits.isEmpty());
        assertEquals("rank.title", hits.get(0).id());
        assertTrue(indexOf(hits, "rank.title") < indexOf(hits, "rank.tags"));
        assertTrue(indexOf(hits, "rank.tags") < indexOf(hits, "rank.summary"));
    }

    @Test
    void fieldMatchTierOrdersTitleBeforeTagsBeforeSummary() {
        CapabilityIndexStore.CapabilityHit titleHit =
            new CapabilityIndexStore.CapabilityHit("a", "test", TOKEN, "", "", "", "", "any", "", 0);
        CapabilityIndexStore.CapabilityHit tagHit =
            new CapabilityIndexStore.CapabilityHit("b", "test", "x", "", TOKEN, "", "", "any", "", 0);
        CapabilityIndexStore.CapabilityHit summaryHit =
            new CapabilityIndexStore.CapabilityHit("c", "test", "x", TOKEN, "", "", "", "any", "", 0);
        var terms = CapabilityIndexStore.queryTerms(TOKEN);
        assertTrue(CapabilityIndexStore.fieldMatchTier(titleHit, terms)
            < CapabilityIndexStore.fieldMatchTier(tagHit, terms));
        assertTrue(CapabilityIndexStore.fieldMatchTier(tagHit, terms)
            < CapabilityIndexStore.fieldMatchTier(summaryHit, terms));
    }

    @Test
    void recordWeightTieBreaksWithinSameFieldTier() {
        CapabilityIndexStore.rebuild(
            agentRoot,
            List.of(
                rec("w.low", TOKEN + " shared title", "", "", "", 1.0),
                rec("w.high", TOKEN + " shared title", "", "", "", 5.0)
            )
        );
        var hits = CapabilityIndexStore.search(agentRoot, TOKEN, List.of(), "any", 5);
        assertEquals("w.high", hits.get(0).id());
    }

    @Test
    void longMixedQueryStillFindsPartialTerms() {
        CapabilityIndexStore.rebuild(
            agentRoot,
            List.of(
                rec(
                    "mcp:gaode/maps_geo",
                    "gaode / maps_geo",
                    "将详细的结构化地址转换为经纬度坐标",
                    "mcp",
                    "$mcp.gaode.maps_geo",
                    1.0
                ),
                rec(
                    "mcp:gaode/maps_weather",
                    "gaode / maps_weather",
                    "根据城市名称查询天气",
                    "mcp",
                    "$mcp.gaode.maps_weather",
                    1.0
                )
            )
        );
        var hits = CapabilityIndexStore.search(
            agentRoot,
            "地图 地理编码 坐标 地址查询 geocode amap baidu map MCP",
            List.of(),
            "any",
            5
        );
        assertFalse(hits.isEmpty());
        assertEquals("mcp:gaode/maps_geo", hits.get(0).id());
    }

    @Test
    void moreTermMatchesOutrankASingleTitleHit() {
        CapabilityIndexStore.rebuild(
            agentRoot,
            List.of(
                rec("only.map", "map catalog", "unrelated", ""),
                rec(
                    "mcp:gaode/maps_geo",
                    "gaode / maps_geo",
                    "将详细的结构化地址转换为经纬度坐标",
                    "mcp",
                    "$mcp.gaode.maps_geo",
                    1.0
                )
            )
        );
        var hits = CapabilityIndexStore.search(agentRoot, "坐标 map", List.of(), "any", 5);
        assertEquals("mcp:gaode/maps_geo", hits.get(0).id());
    }

    private static int indexOf(List<CapabilityIndexStore.CapabilityHit> hits, String id) {
        for (int i = 0; i < hits.size(); i++) {
            if (id.equals(hits.get(i).id())) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static CapabilityRecord rec(
        String id,
        String title,
        String summary,
        String tags
    ) {
        return rec(id, title, summary, tags, "", 1.0);
    }

    private static CapabilityRecord rec(
        String id,
        String title,
        String summary,
        String tags,
        String entry,
        double weight
    ) {
        return new CapabilityRecord(
            id,
            "test",
            title,
            summary,
            tags,
            "any",
            entry,
            "",
            "",
            "",
            "test",
            weight
        );
    }
}
