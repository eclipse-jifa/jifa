# MCP

本页介绍 Eclipse Jifa 中实验性的 MCP 端点。

Jifa 提供了一个实验性的 [Model Context Protocol](https://modelcontextprotocol.io/) 端点，用于在对应认证与授权模式下，以只读方式访问文件及分析结果。

## 概览

- 端点：`/jifa-api/mcp`
- 部署方式：同一个 server、同一个端口、同一个应用
- 支持的节点角色：`STANDALONE_WORKER` 和 `MASTER`
- 当前范围：文件、GC 日志、线程快照、堆快照的高层分析工具

## 启用 MCP

在服务端配置中启用实验性 MCP 端点：

```yaml
jifa:
  mcp-enabled: true
```

端点地址为：

```text
http://<host>:<port>/jifa-api/mcp
```

## 认证与授权

MCP 端点复用了当前 Jifa 的认证和授权体系。

- 如果开启了匿名访问，则可以不带 Bearer Token 调用 MCP。
- 如果启用了登录，则需要在每次请求中传递 `Authorization: Bearer <jwt>`。
- 文件访问权限仍然会被校验，因此 MCP 只能分析当前用户有权限访问的文件。

## 客户端配置

不同 MCP 客户端的配置格式会略有不同，但底层传输方式相同，都是指向 Jifa MCP 端点的 streamable HTTP。

无认证示例：

```json
{
  "mcpServers": {
    "jifa": {
      "type": "streamable-http",
      "url": "http://127.0.0.1:8102/jifa-api/mcp"
    }
  }
}
```

带 Bearer 认证示例：

```json
{
  "mcpServers": {
    "jifa": {
      "type": "streamable-http",
      "url": "http://127.0.0.1:8102/jifa-api/mcp",
      "headers": {
        "Authorization": "Bearer <your-jwt>"
      }
    }
  }
}
```

## 当前工具

### 文件工具

- `list_my_files(type?, page?, pageSize?)`
- `get_file_info`
- `analyze_file_summary`

### GC 日志工具

- `analyze_gc_log_summary`
- `analyze_gc_log_metrics`

### 线程快照工具

- `analyze_thread_dump_summary`
- `analyze_thread_dump_details`
- `analyze_thread_dump_blocking_chains`
- `get_thread_dump_thread_content`

### 堆快照工具

- `analyze_heap_dump_summary`
- `analyze_heap_dump_hotspots`
- `analyze_heap_dump_thread_details`

## 典型工作流

推荐的 MCP 使用方式如下：

1. 先调用 `list_my_files` 发现当前可分析文件。文件较多时可使用 `page` 和 `pageSize` 分页，`pageSize` 最大为 `100`。
2. 再调用 `get_file_info` 确认文件类型和支持的工具。
3. 根据文件类型选择高层分析工具，例如 `analyze_gc_log_summary` 或 `analyze_heap_dump_summary`。
4. 使用结构化结果生成总结；如果需要更细粒度的钻取分析，再回到 Jifa Web UI。

## 返回结果结构

大多数高层分析工具都会返回适合 AI 处理的结构化字段，例如：

- `summary`
- `findings`
- `evidence`
- `recommendations`

其中 `list_my_files` 还会返回分页字段，例如：

- `page`
- `pageSize`
- `totalSize`
- `returnedSize`

如果工具调用失败，结构化结果中还可能包含：

- `error`
- `errorCode`

例如权限不足时可能返回 `ACCESS_DENIED`，文件不存在时返回 `FILE_NOT_FOUND`。

## 直接调用 MCP

如果直接通过 HTTP 调用该端点，需要使用 JSON-RPC，并且在 `Accept` 头中同时带上 `application/json` 和 `text/event-stream`。

由于当前端点使用的是 streamable HTTP，`initialize` 响应会返回 `mcp-session-id` 头。后续请求需要复用这个 header。根据 MCP 协议，初始化完成后的后续请求还应带上 `MCP-Protocol-Version` 头。实际使用时更推荐直接使用 MCP 客户端 SDK，让它自动处理初始化、会话和协议头。

下面是一个 `initialize` 请求示例：

```bash
curl -i -X POST http://127.0.0.1:8102/jifa-api/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize",
    "params": {
      "protocolVersion": "2025-11-25",
      "capabilities": {},
      "clientInfo": {
        "name": "example-client",
        "version": "1.0.0"
      }
    }
  }'
```

下面是一个初始化完成之后、带 Bearer 认证的 `tools/call` 请求示例：

```bash
curl -X POST http://127.0.0.1:8102/jifa-api/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'mcp-session-id: <initialize 响应中的 session-id>' \
  -H 'Authorization: Bearer <your-jwt>' \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/call",
    "params": {
      "name": "analyze_gc_log_summary",
      "arguments": {
        "file": "<unique-file-name>"
      }
    }
  }'
```

## 当前限制

- 当前端点只会在 `STANDALONE_WORKER` 和 `MASTER` 节点上启用。
- 文件上传和下载仍然通过现有的 Web / HTTP API 处理。
- 文件列表中可能会出现 JFR 文件，但当前实验版本还未支持 JFR 的 MCP 功能。
- `list_my_files` 会刻意分页返回，而不是一次性暴露完整文件列表。
- 当前实验版本未包含 API Key / PAT 功能。
