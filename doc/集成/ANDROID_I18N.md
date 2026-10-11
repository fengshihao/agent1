# Android 界面国际化（M0）

## 支持语言

跟随 **系统语言**（标准 `values-*` 资源，无 App 内语言切换）：

| 资源目录 | 语言 |
|----------|------|
| `values/` | 简体中文（默认回退） |
| `values-en/` | English |
| `values-zh-rTW/` | 繁体中文 |
| `values-ja/` | 日本語 |
| `values-ko/` | 한국어 |
| `values-es/` | Español |

未单独翻译的 locale（如 `fr`）回退到 `values/` 简体。

## 范围（当前）

- 主对话壳层、抽屉、模型设置入口、错误提示（`UserFacingError` → `strings.xml`）
- **未** 翻译：`java-agent-core` 系统提示词、工具返回的中文业务句、会话标题（模型生成）

## 国内 / 海外默认模型

首次安装且 **未在 App 内保存过模型配置** 时：

| 市场 | 判定 | 默认服务商 | 默认模型 |
|------|------|------------|----------|
| 国内 | SIM 或 locale 为 CN | DashScope | BuildConfig / 通义默认 |
| 海外 | 其他 | DeepSeek | `deepseek-flash` |

判定逻辑：`DeviceMarketRegion`（`logic.data.config`）。用户保存配置后以偏好为准；「恢复默认」清空偏好后会重新按市场默认。

## 开发约定

- Compose：`stringResource(R.string.xxx)`
- ViewModel / 非 Composable：`AppLocalization.getString`（在 `Agent1Application` 中 `attach`）
- 新增用户可见中文：同时补全上述 6 个 `strings.xml`
