# API contract

Base URL: `http://<server-ip>:8765`

Private endpoint authorization: `Authorization: Bearer <token>`.

## Chat Completions

`POST /v1/chat/completions`

The JSON body is passed to the configured upstream. The server inserts the configured model only when the request does not include a model. Streaming is not available; use `stream: false`.

## Agent tools

`GET /v1/agent/tools` returns the two built-in tool definitions. `POST /v1/agent/tools/call` accepts:

```json
{"name":"device_info","arguments":{}}
```

or

```json
{"name":"echo","arguments":{"value":"texto"}}
```

These endpoints are independent of the configured LLM provider. An external agent can call them directly after receiving the API token.
