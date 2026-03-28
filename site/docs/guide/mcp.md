# MCP

This page describes the experimental MCP endpoint in Eclipse Jifa.

Jifa provides an experimental [Model Context Protocol](https://modelcontextprotocol.io/) endpoint for read-only access to files and analysis results under the corresponding authentication and authorization model.

## Overview

- Endpoint: `/jifa-api/mcp`
- Deployment model: same server, same port, same application
- Supported node roles: `STANDALONE_WORKER` and `MASTER`
- Scope: high-level tools for files, GC logs, thread dumps, and heap dumps

## Enable MCP

Enable the experimental endpoint in your server configuration:

```yaml
jifa:
  mcp-enabled: true
```

The endpoint address is:

```text
http://<host>:<port>/jifa-api/mcp
```

## Authentication and Authorization

The MCP endpoint reuses the existing Jifa authentication and authorization model.

- If anonymous access is enabled, MCP calls can be made without a Bearer token.
- If login is enabled, send `Authorization: Bearer <jwt>` with each MCP request.
- File access checks are still enforced, so MCP can only analyze files visible to the current user.

## Client Configuration

Different MCP clients use slightly different configuration formats, but the transport is the same: streamable HTTP pointing to the Jifa MCP endpoint.

Example without authentication:

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

Example with bearer authentication:

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

## Available Tools

### File tools

- `list_my_files(type?, page?, pageSize?)`
- `get_file_info`
- `analyze_file_summary`

### GC log tools

- `analyze_gc_log_summary`
- `analyze_gc_log_metrics`

### Thread dump tools

- `analyze_thread_dump_summary`
- `analyze_thread_dump_details`
- `analyze_thread_dump_blocking_chains`
- `get_thread_dump_thread_content`

### Heap dump tools

- `analyze_heap_dump_summary`
- `analyze_heap_dump_hotspots`
- `analyze_heap_dump_thread_details`

## Typical Workflow

The recommended MCP workflow is:

1. Call `list_my_files` to discover available analysis files. Use `page` and `pageSize` when the file list is large. The maximum `pageSize` is `100`.
2. Call `get_file_info` to confirm the file type and supported tools.
3. Choose a matching high-level analysis tool such as `analyze_gc_log_summary` or `analyze_heap_dump_summary`.
4. Use the structured result to summarize findings, and open the Jifa Web UI when deeper drill-down is needed.

## Result Shape

Most high-level analysis tools return AI-friendly structured fields such as:

- `summary`
- `findings`
- `evidence`
- `recommendations`

`list_my_files` also returns pagination fields such as:

- `page`
- `pageSize`
- `totalSize`
- `returnedSize`

When a tool call fails, the structured result may also include:

- `error`
- `errorCode`

For example, insufficient permissions may return `ACCESS_DENIED`, and missing files return `FILE_NOT_FOUND`.

## Calling MCP Directly

When calling the endpoint directly over HTTP, use JSON-RPC and include both `application/json` and `text/event-stream` in the `Accept` header.

Because this endpoint uses streamable HTTP, the `initialize` response returns an `mcp-session-id` header. Reuse that header on later requests. Per the MCP protocol, later requests after initialization should also include the `MCP-Protocol-Version` header. In practice, it is still recommended to use an MCP client SDK so initialization, session handling, and protocol headers are managed automatically.

Example `initialize` request:

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

Example `tools/call` request with bearer authentication after initialization:

```bash
curl -X POST http://127.0.0.1:8102/jifa-api/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'mcp-session-id: <session-id-from-initialize-response>' \
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

## Limitations

- The endpoint is only created on `STANDALONE_WORKER` and `MASTER` nodes.
- File upload and download are still handled through the existing Jifa Web / HTTP APIs.
- JFR files may appear in file listings, but JFR-specific MCP tools are not exposed in the current experimental scope.
- `list_my_files` is intentionally paginated and does not expose the entire file list in a single response.
- The current experimental version does not include API keys or PATs.
