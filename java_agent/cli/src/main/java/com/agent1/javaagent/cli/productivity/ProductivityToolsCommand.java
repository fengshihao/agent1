package com.agent1.javaagent.cli.productivity;

import java.io.PrintWriter;
import java.util.List;

/** 生产力 CLI：打印当前装配的工具能力摘要。 */
public final class ProductivityToolsCommand {

    private ProductivityToolsCommand() {
    }

    public static int run(PrintWriter out, boolean weizhiScriptEnabled) {
        out.println(ProductivityToolCapabilities.summaryForCli(weizhiScriptEnabled));
        out.println();
        for (String line : ProductivityToolCapabilities.detailBullets(weizhiScriptEnabled)) {
            out.println("  · " + line);
        }
        return 0;
    }
}
