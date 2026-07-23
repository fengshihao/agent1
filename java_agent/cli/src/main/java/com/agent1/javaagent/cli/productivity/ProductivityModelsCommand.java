package com.agent1.javaagent.cli.productivity;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.modelcatalog.QwenModelCatalog;
import com.agent1.javaagent.modelcatalog.QwenModelInfo;
import com.agent1.javaagent.modelcatalog.RuntimeConfigSummary;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.Locale;

/** 生产力 CLI：打印 Qwen 主模型规格与当前运行时参数。 */
public final class ProductivityModelsCommand {

    private ProductivityModelsCommand() {
    }

    public static int run(PrintWriter out, PrintWriter err) {
        AgentRuntimeConfig config = AgentRuntimeConfig.fromEnvironment();
        RuntimeConfigSummary summary = RuntimeConfigSummary.from(config);
        if (summary.getConfigurationError() != null) {
            err.println(summary.getConfigurationError());
        }
        NumberFormat fmt = NumberFormat.getIntegerInstance(Locale.US);
        out.println("=== 当前运行时 ===");
        out.println("model=" + summary.getModelId());
        out.println("base_url=" + summary.getBaseUrl());
        out.println("api_key=" + (summary.isApiKeyConfigured() ? "configured" : "missing"));
        out.println(
            "limits: context_turns=" + summary.getMaxContextTurns()
                + " max_turns_per_run=" + summary.getMaxTurnsPerRun()
                + " max_tool_calls=" + summary.getMaxToolCallsPerRun()
        );
        summary.getCatalogMatch().ifPresent(match -> printModel(out, fmt, match, true));
        out.println();
        out.println("=== Qwen 主模型（OpenAI 兼容 model 名）===");
        for (QwenModelInfo model : QwenModelCatalog.primaryModels()) {
            printModel(out, fmt, model, false);
        }
        return summary.getConfigurationError() == null ? 0 : 1;
    }

    private static void printModel(
        PrintWriter out,
        NumberFormat fmt,
        QwenModelInfo model,
        boolean current
    ) {
        String prefix = current ? "* " : "- ";
        out.println(
            prefix + model.getDisplayName() + " (" + model.getModelId() + ") tier=" + model.getTier()
        );
        out.println(
            "  context=" + fmt.format(model.getContextWindowTokens())
                + " max_in=" + fmt.format(model.getMaxInputTokens())
                + " max_out=" + fmt.format(model.getMaxOutputTokens())
                + (model.getMaxThinkingChainTokens() == null
                    ? ""
                    : " max_thinking=" + fmt.format(model.getMaxThinkingChainTokens()))
        );
        out.println(
            "  in=" + String.join(",", model.getInputModalities())
                + " out=" + String.join(",", model.getOutputModalities())
                + " tools=" + model.isFunctionCalling()
                + " " + model.getThinkingMode()
        );
    }
}
