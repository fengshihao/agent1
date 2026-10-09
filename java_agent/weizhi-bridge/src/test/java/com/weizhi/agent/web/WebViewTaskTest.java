package com.weizhi.agent.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebViewTaskTest {

    @Test
    void prepareCodeLeavesAsyncReturnUntouched() {
        String code = "return (async () => { await Promise.resolve(1); return 2; })();";
        WebViewTask.PreparedCode prep = WebViewTask.prepareCode(code);
        assertEquals(code, prep.code);
        assertFalse(prep.autoAsyncWrapped);
    }

    @Test
    void prepareCodeWrapsTopLevelAwait() {
        String code = "const x = await Promise.resolve(1);\nreturn x;";
        WebViewTask.PreparedCode prep = WebViewTask.prepareCode(code);
        assertTrue(prep.autoAsyncWrapped);
        assertTrue(prep.code.startsWith("return (async () => {"));
        assertTrue(prep.code.contains("await Promise.resolve(1)"));
        assertTrue(prep.code.endsWith("})();"));
    }

    @Test
    void prepareCodeSkipsWhenNoAwait() {
        String code = "return 42;";
        WebViewTask.PreparedCode prep = WebViewTask.prepareCode(code);
        assertEquals(code, prep.code);
        assertFalse(prep.autoAsyncWrapped);
    }
}
