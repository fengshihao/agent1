package com.agent1.javaagent.tool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ImageSearchUrlPolicyTest {

    @Test
    void blocksSocialCrawlerHosts() {
        assertTrue(ImageSearchUrlPolicy.isBlockedForDownload(
            "https://lookaside.instagram.com/seo/google_widget/crawler/?media_id=1"
        ));
        assertTrue(ImageSearchUrlPolicy.isBlockedForDownload(
            "https://pbs.twimg.com/media/x?format=webp"
        ));
    }

    @Test
    void ranksDomesticHttpsAheadOfOverseas() {
        String cn = "https://p4.itc.cn/images01/a.jpeg";
        String overseas = "https://www.tibetway.com/assets/images/everest/ebc.jpg";
        assertTrue(ImageSearchUrlPolicy.scoreImageUrl(cn) > ImageSearchUrlPolicy.scoreImageUrl(overseas));

        List<ImageSearchUrlPolicy.RankedImage> sorted = ImageSearchUrlPolicy.sortCandidates(List.of(
            new ImageSearchUrlPolicy.RankedImage(overseas, "", ImageSearchUrlPolicy.scoreImageUrl(overseas)),
            new ImageSearchUrlPolicy.RankedImage(cn, "", ImageSearchUrlPolicy.scoreImageUrl(cn))
        ));
        assertEquals(cn, sorted.get(0).url());
    }

    @Test
    void deprioritizesPlainHttp() {
        String http = "http://www.peopleweekly.cn/uploadfile/2019/0619/a.jpg";
        String https = "https://pic.huitu.com/res/20151014/1.jpg";
        assertTrue(ImageSearchUrlPolicy.scoreImageUrl(https) > ImageSearchUrlPolicy.scoreImageUrl(http));
    }

    @Test
    void formatImagesOmitsBlockedUrls() throws Exception {
        String raw = """
            {"images":[
              "https://pbs.twimg.com/media/x",
              "https://p4.itc.cn/images01/a.jpeg"
            ]}
            """;
        String text = TavilyWebSearchClient.formatImages("test", raw);
        assertFalse(text.contains("twimg.com"));
        assertTrue(text.contains("itc.cn"));
        assertTrue(text.contains("1. https://p4.itc.cn"));
    }
}
