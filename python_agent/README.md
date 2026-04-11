# Python Agent（`agent1` CLI）

本目录为 **Pydantic AI** 命令行智能体：工具调用、Rich 终端、JSONL 可观测性。仓库级说明与架构见根目录 [README.md](../README.md)。

## 一键安装（`uv tool install`）

实现位于本目录 **`scripts/install.sh`**、**`scripts/install.ps1`**。从仓库根请用 **`install-python-agent.sh`** / **`install-python-agent.ps1`**（薄转调）；远程 raw 安装 URL 见根目录 [README.md](../README.md)。

## 开发

```bash
cd python_agent
uv sync
uv run agent1 --help
```

## 测试

```bash
cd python_agent
PYTHONPATH=src python -m unittest discover -s tests -v
```

在仓库根目录也可使用（不切换目录时）：

```bash
uv sync --project python_agent
uv run --project python_agent agent1 "你好"
```

在仓库根目录跑单测时，需指定工作目录：

```bash
uv run --project python_agent --directory python_agent python -m unittest discover -s tests -v
```
