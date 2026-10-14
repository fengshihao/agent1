package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class WeizhiPackageImportsTest {

    @Test
    void scansSingleAndDoubleQuotedImports() {
        String src =
            "import { a } from 'weizhi:docx';\n"
                + "import { b } from \"weizhi:pptx\";\n";
        Set<String> names = WeizhiPackageImports.scan(src);
        assertEquals(2, names.size());
        assertTrue(names.contains("docx"));
        assertTrue(names.contains("pptx"));
    }

    @Test
    void dedupesNames() {
        String src = "import x from 'weizhi:docx';\nimport y from 'weizhi:docx';\n";
        assertEquals(1, WeizhiPackageImports.scan(src).size());
    }
}
