# Android Dynamic UI 架构图

## 分层架构（Llm UI + Dynamic UI）

```mermaid
flowchart LR
    subgraph presentation [PresentationLayer]
        main[MainActivity]
        llmScreen[LlmUiTestScreen]
        dynamicScreen[DynamicScreenFromDocument]
        renderer[RenderComponent]
        crashCard[CrashReportCard]
    end

    subgraph domain [DomainOrchestrationLayer]
        uiAgent[LlmUiAgent]
        genFlow[GenerateUIFlow]
        repairFlow[RepairOnParseFailFlow]
        summaryFlow[SummarizeSelectionFlow]
    end

    subgraph data [DataAndParsingLayer]
        parser[UiParser]
        cleaner[cleanModelJson]
        models[UiModel_UiDocument_UiComponent]
        serializer[UiComponentSerializer]
        formState[formState_MutableMap]
    end

    subgraph infra [InfrastructureLayer]
        promptLoader[PromptAssetsLoader]
        qwenClient[QwenClient]
        dashscope[DashScope_Qwen3_5_Flash_API]
        crashReporter[CrashReporter]
        crashStore[SharedPrefs_and_Files]
    end

    main --> llmScreen
    llmScreen --> genFlow
    genFlow --> uiAgent
    uiAgent --> promptLoader
    uiAgent --> qwenClient
    qwenClient --> dashscope
    dashscope --> cleaner
    cleaner --> parser
    parser --> serializer
    serializer --> models
    parser -->|ok| dynamicScreen
    dynamicScreen --> renderer
    renderer --> formState

    parser -->|fail| repairFlow
    repairFlow --> uiAgent
    repairFlow --> qwenClient
    repairFlow --> cleaner
    repairFlow --> parser

    llmScreen --> summaryFlow
    summaryFlow --> formState
    summaryFlow --> uiAgent
    summaryFlow --> qwenClient
    summaryFlow --> dashscope

    llmScreen --> crashReporter
    crashReporter --> crashStore
    crashStore --> crashCard
```

## 说明

- Presentation：页面入口、交互、动态组件渲染。
- Domain：生成 UI、解析失败修复、提交用户选择总结等流程编排。
- Data：JSON 清洗、解析、组件模型与用户输入状态聚合。
- Infrastructure：提示词加载、Qwen API 访问、崩溃捕获与持久化。
