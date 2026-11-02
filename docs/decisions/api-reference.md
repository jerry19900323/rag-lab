# RAG Lab API Reference

## Overview

RAG Lab provides authentication, health, document preview, durable indexing, document metadata,
keyword search, and administrator model-configuration APIs.

Local base URL:

```text
http://localhost:8080
```

All request and response bodies use UTF-8. JSON timestamps use ISO 8601 UTC format, for example
`2026-11-01T07:30:00Z`.

## Authentication

The captcha and login endpoints and all `/api/health/**` endpoints are public. Every other `/api/**`
endpoint requires an opaque Redis-backed bearer token:

```http
Authorization: Bearer <accessToken>
```

A missing, expired, invalid, or disabled-user session returns `401 Unauthorized`:

```json
{
  "error": "Authentication is required"
}
```

## Common Conventions

### Content Types

- JSON requests and responses use `application/json`.
- File uploads use `multipart/form-data`.
- Successful logout has no response body.

### Error Response

Expected failures use this JSON shape:

```json
{
  "error": "A human-readable description of the failure"
}
```

| Status | Meaning |
| --- | --- |
| `400 Bad Request` | Invalid request, captcha, file, chunk settings, or pagination. |
| `401 Unauthorized` | Missing/invalid session or incorrect username/password. |
| `404 Not Found` | The requested document does not exist. |
| `403 Forbidden` | The authenticated user is not an administrator for a model-settings request. |
| `409 Conflict` | The same logical document is already being indexed. |
| `502 Bad Gateway` | Elasticsearch failed while storing or searching document chunks. |
| `503 Service Unavailable` | A health-checked dependency is unavailable. |

### Pagination

Paginated endpoints accept zero-based `page` and `size` query parameters. `page` must not be
negative and `size` must be between `1` and `100`.

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### Character Offsets

Chunk offsets use zero-based, half-open ranges: `[startOffset, endOffset)`. The start character is
included and the end character is excluded, so `characterCount = endOffset - startOffset`.
Offsets refer to normalized content after optional UTF-8 BOM removal and line-ending conversion to LF.

## Endpoint Summary

| Method | Path | Authentication | Description |
| --- | --- | --- | --- |
| `GET` | `/api/health` | Public | Application liveness. |
| `GET` | `/api/health/elasticsearch` | Public | Elasticsearch readiness. |
| `GET` | `/api/health/infrastructure` | Public | MySQL and Redis readiness. |
| `GET` | `/api/auth/captcha` | Public | Create a one-time image captcha. |
| `POST` | `/api/auth/login` | Public | Validate captcha and credentials and create a session. |
| `GET` | `/api/auth/me` | Bearer | Return the current user. |
| `POST` | `/api/auth/logout` | Bearer | Revoke the current session. |
| `POST` | `/api/documents/preview` | Bearer | Parse and chunk without persistence. |
| `POST` | `/api/documents/index` | Bearer | Parse, chunk, and persist a document generation. |
| `GET` | `/api/documents` | Bearer | List indexed documents. |
| `GET` | `/api/documents/{documentId}` | Bearer | Get document metadata. |
| `GET` | `/api/documents/{documentId}/ingestions` | Bearer | List ingestion attempts. |
| `POST` | `/api/web-pages/preview` | Bearer | Fetch public HTML pages and preview chunks. |
| `POST` | `/api/web-pages/index` | Bearer | Fetch public HTML pages and persist their chunks. |
| `GET` | `/api/search` | Bearer | Search indexed chunks by keyword. |
| `GET` | `/api/admin/model-providers` | Admin bearer | List supported provider presets and purposes. |
| `GET` | `/api/admin/model-connections` | Admin bearer | List saved connections without credentials. |
| `POST` | `/api/admin/model-connections` | Admin bearer | Save a provider connection. |
| `PUT` | `/api/admin/model-connections/{id}` | Admin bearer | Edit or disable a connection. |
| `GET` | `/api/admin/model-connections/{id}/models` | Admin bearer | Load a live provider model list. |
| `POST` | `/api/admin/model-connections/{id}/test` | Admin bearer | Test provider catalog access. |
| `GET` | `/api/admin/model-assignments` | Admin bearer | Read model selection for each purpose. |
| `PUT` | `/api/admin/model-assignments/{purpose}` | Admin bearer | Bind or clear a model for one purpose. |

## Authentication API

### Create Captcha

Creates a one-time four-character captcha. The answer is stored as a hash in Redis and expires after
the configured captcha TTL, two minutes by default.

```http
GET /api/auth/captcha
```

Status: `200 OK`

```json
{
  "captchaId": "92fe9db6-8912-4b77-99ad-b61df1115e01",
  "imageDataUri": "data:image/png;base64,iVBORw0KGgoAAA...",
  "expiresInSeconds": 120
}
```

| Field | Type | Description |
| --- | --- | --- |
| `captchaId` | string | Identifier submitted with the login request. |
| `imageDataUri` | string | Browser-ready PNG data URI. Use it directly as an image `src`. |
| `expiresInSeconds` | integer | Remaining validity when the captcha is created. |

```shell
curl http://localhost:8080/api/auth/captcha
```

### Login

Consumes the captcha, validates an active MySQL user with BCrypt, and creates an opaque Redis session.
A captcha is consumed even when the username or password is incorrect.

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "your-password",
  "captchaId": "92fe9db6-8912-4b77-99ad-b61df1115e01",
  "captchaCode": "ABCD"
}
```

All four fields are required and must not be blank.

Status: `200 OK`

```json
{
  "accessToken": "BafDq4f0yY7sV3dQzXv8YlZp1q6WmA9N2kR5tU8xC0E",
  "tokenType": "Bearer",
  "expiresInSeconds": 28800,
  "user": {
    "userId": 1,
    "username": "admin",
    "displayName": "Administrator",
    "role": "ADMIN"
  }
}
```

| Status | Condition | Example `error` |
| --- | --- | --- |
| `400` | Captcha missing, incorrect, expired, or already used. | `The captcha is invalid or has expired` |
| `401` | User does not exist, is disabled, or password is incorrect. | `The username or password is incorrect` |

```shell
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"your-password","captchaId":"captcha-id","captchaCode":"ABCD"}'
```

### Get Current User

```http
GET /api/auth/me
Authorization: Bearer <accessToken>
```

Status: `200 OK`

```json
{
  "userId": 1,
  "username": "admin",
  "displayName": "Administrator",
  "role": "ADMIN"
}
```

### Logout

Deletes the current session from Redis. The token cannot be used afterward.

```http
POST /api/auth/logout
Authorization: Bearer <accessToken>
```

Status: `204 No Content`

```shell
curl -X POST http://localhost:8080/api/auth/logout \
  -H 'Authorization: Bearer session-token'
```

## Health API

### Application Health

```http
GET /api/health
```

Status: `200 OK`

```json
{
  "status": "UP"
}
```

### Elasticsearch Health

```http
GET /api/health/elasticsearch
```

Status is `200 OK` when Elasticsearch responds, otherwise `503 Service Unavailable`.

```json
{
  "status": "UP",
  "message": null
}
```

### Infrastructure Health

Checks MySQL and Redis. Status is `200 OK` when both dependencies are available, otherwise
`503 Service Unavailable`.

```http
GET /api/health/infrastructure
```

```json
{
  "status": "UP",
  "components": {
    "mysql": {"status": "UP", "message": null},
    "redis": {"status": "UP", "message": null}
  }
}
```

## Document API

Supported formats are TXT, Markdown, PDF, DOCX, PPTX, XLSX, HTML, and HTM. Text formats must use
UTF-8. PDFs must contain a text layer; OCR is not currently provided. Legacy `.doc`, `.ppt`, and
`.xls` files are not supported. The default maximum multipart request size is 5 MB.

Document types include `TEXT`, `MARKDOWN`, `PDF`, `DOCX`, `PPTX`, `XLSX`, and `HTML`.

### Preview Document Chunks

Parses, normalizes, and chunks a document without writing to MySQL or Elasticsearch.

```http
POST /api/documents/preview
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data
```

| Form field | Type | Required | Default | Constraints |
| --- | --- | --- | --- | --- |
| `file` | file | Yes | None | Supported, non-empty file; maximum request size 5 MB. |
| `maxCharacters` | integer | No | `800` | Between `100` and `10000`. |
| `overlapCharacters` | integer | No | `100` | Non-negative and smaller than `maxCharacters`. |

Status: `200 OK`

```json
{
  "fileName": "notes.md",
  "documentType": "MARKDOWN",
  "characterCount": 1240,
  "chunkCount": 2,
  "maxCharacters": 800,
  "overlapCharacters": 100,
  "chunks": [
    {
      "index": 0,
      "startOffset": 0,
      "endOffset": 756,
      "characterCount": 756,
      "content": "# Example document\n\nDocument content...",
      "sourceReferences": [
        {
          "pageNumber": null,
          "slideNumber": null,
          "sheetName": null,
          "sectionTitle": "Example document",
          "elementType": "HEADING"
        }
      ]
    }
  ]
}
```

| Source reference field | Type | Description |
| --- | --- | --- |
| `pageNumber` | integer or null | One-based PDF page number. |
| `slideNumber` | integer or null | One-based PowerPoint slide number. |
| `sheetName` | string or null | Excel worksheet name. |
| `sectionTitle` | string or null | Nearest available section or heading. |
| `elementType` | string | Source kind such as `TEXT`, `PAGE`, `SLIDE`, `ROW`, `HEADING`, `PARAGRAPH`, `TABLE`, `HTML_BLOCK`, or `TABLE_ROW`. |
| `sourceUrl` | string or null | Public source URL for a fetched web page. |
| `sourceTitle` | string or null | HTML title of a fetched web page. |
| `webPageNumber` | integer or null | One-based position in the followed pagination sequence. |

```shell
curl -X POST http://localhost:8080/api/documents/preview \
  -H 'Authorization: Bearer session-token' \
  -F 'file=@notes.md' \
  -F 'maxCharacters=800' \
  -F 'overlapCharacters=100'
```

### Index Document

Parses and chunks a document, records its lifecycle in MySQL, and atomically replaces the current
document generation in Elasticsearch. A Redis lock prevents concurrent indexing of the same logical
document.

```http
POST /api/documents/index
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data
```

| Form field | Type | Required | Default | Constraints |
| --- | --- | --- | --- | --- |
| `file` | file | Yes | None | Same constraints as preview. |
| `documentId` | string | No | Generated ID | Trimmed; maximum 200 characters. Reusing an ID replaces its current ES generation. |
| `maxCharacters` | integer | No | `800` | Between `100` and `10000`. |
| `overlapCharacters` | integer | No | `100` | Non-negative and smaller than `maxCharacters`. |

Status: `201 Created`

```json
{
  "documentId": "doc-8b0a9db7",
  "ingestionId": "ing-5ee39f20",
  "fileName": "notes.md",
  "documentType": "MARKDOWN",
  "documentHash": "43c5f6a7b8c9d0e1...",
  "characterCount": 1240,
  "chunkCount": 2,
  "indexedAt": "2026-11-01T07:30:00Z"
}
```

| Status | Condition | Example `error` |
| --- | --- | --- |
| `409` | Another request is indexing the same `documentId`. | `Document ... is already being indexed` |
| `502` | Elasticsearch could not store the chunks. | `The document could not be stored in the search index` |

```shell
curl -X POST http://localhost:8080/api/documents/index \
  -H 'Authorization: Bearer session-token' \
  -F 'file=@notes.md' \
  -F 'documentId=company-handbook'
```

### List Documents

```http
GET /api/documents?page=0&size=20&document=handbook
Authorization: Bearer <accessToken>
```

| Query parameter | Required | Default | Description |
| --- | --- | --- | --- |
| `page` | No | `0` | Zero-based page number. |
| `size` | No | `20` | Page size between `1` and `100`. |
| `document` | No | None | Case-insensitive substring match against `fileName`. Leading and trailing whitespace is ignored. |

When `document` is omitted or blank, all documents are returned. Pagination totals always reflect
the active filter.

Status: `200 OK`

```json
{
  "items": [
    {
      "documentId": "company-handbook",
      "fileName": "handbook.pdf",
      "documentType": "PDF",
      "documentHash": "43c5f6a7b8c9d0e1...",
      "characterCount": 9200,
      "chunkCount": 14,
      "currentIngestionId": "ing-5ee39f20",
      "status": "COMPLETED",
      "createdAt": "2026-11-01T07:20:00Z",
      "updatedAt": "2026-11-01T07:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Document status values are `PROCESSING`, `COMPLETED`, or `FAILED`.

```shell
curl 'http://localhost:8080/api/documents?page=0&size=20&document=handbook' \
  -H 'Authorization: Bearer session-token'
```

### Get Document

```http
GET /api/documents/{documentId}
Authorization: Bearer <accessToken>
```

Status: `200 OK`. The response is one document object using the same fields as `items[]` in the list
response.

Status: `404 Not Found` when the ID does not exist:

```json
{
  "error": "Document missing-id was not found"
}
```

### List Document Ingestions

```http
GET /api/documents/{documentId}/ingestions?page=0&size=20
Authorization: Bearer <accessToken>
```

Status: `200 OK`

```json
{
  "items": [
    {
      "ingestionId": "ing-5ee39f20",
      "documentId": "company-handbook",
      "documentHash": "43c5f6a7b8c9d0e1...",
      "status": "COMPLETED",
      "characterCount": 9200,
      "chunkCount": 14,
      "errorMessage": null,
      "startedAt": "2026-11-01T07:29:58Z",
      "completedAt": "2026-11-01T07:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`errorMessage` and `completedAt` can be `null` while processing. `errorMessage` can contain a safe
failure summary when status is `FAILED`.

### Document Validation Errors

Preview and indexing can return `400 Bad Request` for these conditions:

| Condition | Example `error` |
| --- | --- |
| Missing filename | `The uploaded document must have a file name` |
| Unsupported extension | `Only .txt, .md, .markdown, .pdf, .docx, .pptx, .xlsx, .html, and .htm files are supported` |
| Empty file | `The uploaded document is empty` |
| No readable text | `The uploaded document does not contain readable text` |
| Invalid UTF-8 | `The document must use valid UTF-8 encoding` |
| Scanned/image-only PDF | `The PDF does not contain readable text; scanned PDFs require OCR` |
| Encrypted PDF | `Encrypted PDF documents are not supported` |
| Invalid maximum size | `maxCharacters must be between 100 and 10000` |
| Invalid overlap | `overlapCharacters must be non-negative and smaller than maxCharacters` |
| Invalid page | `page must not be negative` |
| Invalid page size | `size must be between 1 and 100` |

Uploads exceeding the multipart limit can be rejected by the web server before controller validation.

## Web Page Import API

Web imports fetch public static HTML and then reuse the normal parsing, chunking, MySQL lifecycle,
and Elasticsearch indexing pipeline. JavaScript-rendered content is not executed.

Both endpoints use the same request body:

```json
{
  "url": "https://example.com/articles",
  "followPagination": false,
  "maxPages": 10,
  "documentId": null,
  "maxCharacters": 800,
  "overlapCharacters": 100
}
```

| Field | Type | Required | Default | Constraints |
| --- | --- | --- | --- | --- |
| `url` | string | Yes | None | Absolute public HTTP or HTTPS URL returning HTML. |
| `followPagination` | boolean | No | `false` | When true, follows same-origin static next-page links. |
| `maxPages` | integer | No | `10` | Between `1` and `50`. |
| `documentId` | string or null | No | Generated ID | Used by indexing; maximum 200 characters. |
| `maxCharacters` | integer | No | `800` | Between `100` and `10000`. |
| `overlapCharacters` | integer | No | `100` | Non-negative and smaller than `maxCharacters`. |

Pagination discovery first checks `rel="next"`, then common next-link labels including `Next`,
`Next page`, and `下一页`. Only links with the same scheme, host, and effective port as the first
fetched page are followed. Repeated URLs stop the crawl and `maxPages` is always enforced.

For safety, the importer rejects local, loopback, link-local, private, multicast, and non-HTTP(S)
destinations. Every redirect is revalidated. Requests default to a 5-second connection timeout,
15-second request timeout, five redirects, and a 2 MB maximum HTML response per page. Cookies and
authorization headers are not forwarded.

### Preview Web Pages

Fetches and chunks pages without writing to MySQL or Elasticsearch.

```http
POST /api/web-pages/preview
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Status: `200 OK`

```json
{
  "requestedUrl": "https://example.com/articles",
  "paginationFollowed": true,
  "pages": [
    {
      "pageNumber": 1,
      "url": "https://example.com/articles",
      "title": "Articles"
    },
    {
      "pageNumber": 2,
      "url": "https://example.com/articles?page=2",
      "title": "Articles - Page 2"
    }
  ],
  "document": {
    "fileName": "example.com.html",
    "documentType": "HTML",
    "characterCount": 3200,
    "chunkCount": 5,
    "maxCharacters": 800,
    "overlapCharacters": 100,
    "chunks": []
  }
}
```

`document.chunks` uses the same complete chunk schema as document preview. Its source references
contain `sourceUrl`, `sourceTitle`, and `webPageNumber`.

```shell
curl -X POST http://localhost:8080/api/web-pages/preview \
  -H 'Authorization: Bearer session-token' \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/articles","followPagination":true,"maxPages":10}'
```

### Index Web Pages

Fetches pages and persists the combined logical document through the normal ingestion lifecycle.

```http
POST /api/web-pages/index
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Status: `201 Created`

```json
{
  "requestedUrl": "https://example.com/articles",
  "paginationFollowed": false,
  "pages": [
    {
      "pageNumber": 1,
      "url": "https://example.com/articles",
      "title": "Articles"
    }
  ],
  "document": {
    "documentId": "doc-8b0a9db7",
    "ingestionId": "ing-5ee39f20",
    "fileName": "example.com.html",
    "documentType": "HTML",
    "documentHash": "43c5f6a7b8c9d0e1...",
    "characterCount": 1600,
    "chunkCount": 3,
    "indexedAt": "2026-11-01T07:30:00Z"
  }
}
```

Download, URL-validation, HTML-content, response-size, and pagination validation failures return
`400 Bad Request` with the common error response. Indexing can additionally return `409 Conflict`
or `502 Bad Gateway` as described by the document indexing endpoint.

## Administrator Model Settings API

### Frontend page and expected behavior

Build a **Model Settings** page visible to users whose login or `/api/auth/me` response has
`user.role === "ADMIN"`. A non-admin request to any endpoint in this section returns `403`.
The page has two areas:

1. **Connections:** list saved providers, add a connection, edit its name or key, enable/disable
   it, load its available models, and test catalog access. Show `hasApiKey` as “configured” or
   “missing”; never expect the API to return the key itself.
2. **Purpose assignments:** three selectors, `ANSWER` (answer generation), `EMBEDDING` (vector
   generation and search), and `ROUTING` (model-assisted routing). Each selector offers “Not
   configured” and the enabled connections that support that purpose. After selecting a
   connection, load its model list and choose a model ID. Allow manual model ID entry when the
   provider catalog is incomplete or unavailable; label such a selection as unverified.

On page load, request `/api/admin/model-providers`, `/api/admin/model-connections`, and
`/api/admin/model-assignments`. The provider dropdown is populated from the first response,
not a hard-coded frontend list. The model dropdown is loaded **after** a connection is saved,
using its ID and `GET .../models`; “Refresh models” calls that endpoint again. Model lists are
queried live and may change. The response is not a promise that a particular model supports
every purpose; `supportedPurposes` describes the provider protocol, not each individual model.
The “Test connection” button tests access to the model catalog and displays `UP` plus the
number of discovered models. It does **not** invoke the selected chat or embedding model.

This stage stores settings and assignments only. It does not yet invoke a model for document
indexing, vector search, routing, or answer generation. Until those workflows are implemented,
the existing document indexing and keyword search behavior is unchanged. An unassigned purpose
is represented by `connectionId: null` and `modelId: null`; future workflows must skip that
model step. Disabling a connection preserves its assignments for display, but future workflows
must not use it while disabled.

### Server setup and provider presets

Set `MODEL_CONFIG_ENCRYPTION_KEY` to a base64-encoded 32-byte random key before saving API keys.
For example, generate a value with `openssl rand -base64 32` and place it in deployment secrets.
`application.yml` maps this environment variable to `rag.model-config.encryption-key`; in
IntelliJ IDEA, put `MODEL_CONFIG_ENCRYPTION_KEY=<value>` in the application's Run Configuration
environment variables, as with `DB_PASSWORD`. Do not paste the secret into the committed YAML.
Keep the same value across application restarts and instances; losing it makes stored provider
keys unreadable. Without it, saving a connection that requires an API key returns `503`. Stored
keys use AES-GCM encryption. Only the backend calls provider catalog endpoints. The page sends
the provider key to this backend once during creation or replacement; it never calls providers
directly or stores the key in browser storage.

The initial provider presets are:

| `provider` | Page label | Catalog source | Supported purpose types | API key |
| --- | --- | --- | --- | --- |
| `OPENAI` | OpenAI | OpenAI models API | `ANSWER`, `EMBEDDING`, `ROUTING` | Required |
| `DEEPSEEK` | DeepSeek | DeepSeek models API | `ANSWER`, `ROUTING` | Required |
| `DASHSCOPE` | Alibaba Cloud Model Studio (international) | International Model Studio models API | `ANSWER`, `EMBEDDING`, `ROUTING` | Required |
| `ANTHROPIC` | Claude | Anthropic models API | `ANSWER`, `ROUTING` | Required |
| `GEMINI` | Gemini | Gemini models API | `ANSWER`, `EMBEDDING`, `ROUTING` | Required |
| `OLLAMA` | Ollama (local) | `127.0.0.1:11434/api/tags` on the backend host | `ANSWER`, `EMBEDDING`, `ROUTING` | Not used |

The Model Studio preset uses the **international** catalog. Its API key must be valid for that
region. The Ollama preset connects to the **backend server's** localhost, not the browser's
computer. Provider URLs are fixed server-side; clients cannot supply arbitrary URLs in this
version. A model missing from a provider's catalog may be entered manually in the assignment
form, but a manual ID is not connection-tested by this API.

### List provider presets

```http
GET /api/admin/model-providers
Authorization: Bearer <adminAccessToken>
```

Status: `200 OK`. The response is an array; one entry looks like:

```json
{
  "provider": "OPENAI",
  "displayName": "OpenAI",
  "requiresApiKey": true,
  "supportedPurposes": ["ANSWER", "EMBEDDING", "ROUTING"]
}
```

Use `provider` as the form value and `displayName` as its visible label. Filter the purpose
selectors using `supportedPurposes`.

### List, create, and edit connections

```http
GET /api/admin/model-connections
Authorization: Bearer <adminAccessToken>
```

Status: `200 OK`. Returns an array; when no connections exist it returns `[]`.

```json
[
  {
    "connectionId": "3e246d55-2b69-4e13-88bb-8e31dd479e7d",
    "name": "Main OpenAI account",
    "provider": "OPENAI",
    "enabled": true,
    "hasApiKey": true,
    "createdAt": "2026-11-02T08:00:00Z",
    "updatedAt": "2026-11-02T08:00:00Z"
  }
]
```

Create a connection:

```http
POST /api/admin/model-connections
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{
  "name": "Main OpenAI account",
  "provider": "OPENAI",
  "apiKey": "provider-secret-key",
  "enabled": true
}
```

Status: `201 Created`; response is one connection object in the same safe shape as the list.
`name` must contain 1–100 characters after trimming. `provider` must be one of the preset enum
values. `apiKey` is required except for `OLLAMA`; it is never returned. `enabled` defaults to
`true` on creation. Do not persist the form's `apiKey` in browser storage.

Edit an existing connection using `PUT /api/admin/model-connections/{id}` with the same JSON
shape. Status: `200 OK`; the response is the updated safe connection object. Send `apiKey: null`
or omit it to retain the stored key. Send a new key to replace it. If `enabled` is omitted on
update, its previous value is retained. The `provider` must remain the original provider;
create a new connection to switch providers. Set `enabled: false` to disable a connection.
There is no delete endpoint in this stage.

```shell
curl -X PUT http://localhost:8080/api/admin/model-connections/3e246d55-2b69-4e13-88bb-8e31dd479e7d \
  -H 'Authorization: Bearer session-token' -H 'Content-Type: application/json' \
  -d '{"name":"Main OpenAI account","provider":"OPENAI","enabled":false}'
```

### Refresh models and test catalog access

```http
GET /api/admin/model-connections/{id}/models
Authorization: Bearer <adminAccessToken>
```

Status: `200 OK`. Returns a dynamically fetched array such as:

```json
[
  {"modelId": "provider-model-id", "displayName": "Provider Model Name"}
]
```

Use `modelId` as the value saved in a purpose assignment, and `displayName` as the dropdown
label. An empty array means the catalog returned no models. Call this when a connection is
selected and when the user clicks “Refresh models”; do not call it on every page render.

```http
POST /api/admin/model-connections/{id}/test
Authorization: Bearer <adminAccessToken>
```

Status: `200 OK` when the catalog can be read:

```json
{"status":"UP","discoveredModelCount":12}
```

This test checks API key and catalog reachability, not whether a specific model can generate
answers or embeddings. The frontend should show that distinction beside the test result.

```shell
curl http://localhost:8080/api/admin/model-connections/3e246d55-2b69-4e13-88bb-8e31dd479e7d/models \
  -H 'Authorization: Bearer session-token'

curl -X POST http://localhost:8080/api/admin/model-connections/3e246d55-2b69-4e13-88bb-8e31dd479e7d/test \
  -H 'Authorization: Bearer session-token'
```

### Read and change purpose assignments

```http
GET /api/admin/model-assignments
Authorization: Bearer <adminAccessToken>
```

Status: `200 OK`. Always returns one item for each of `ANSWER`, `EMBEDDING`, and `ROUTING`.
With no configuration, the response is:

```json
[
  {"purpose":"ANSWER","connectionId":null,"modelId":null,"updatedAt":null},
  {"purpose":"EMBEDDING","connectionId":null,"modelId":null,"updatedAt":null},
  {"purpose":"ROUTING","connectionId":null,"modelId":null,"updatedAt":null}
]
```

Bind a model after choosing a connection and model ID:

```http
PUT /api/admin/model-assignments/ANSWER
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{"connectionId":"3e246d55-2b69-4e13-88bb-8e31dd479e7d","modelId":"provider-model-id"}
```

Status: `200 OK`; returns the saved assignment with an `updatedAt` timestamp. To select “Not
configured”, send `{"connectionId":null,"modelId":null}` to the same path. Both fields must
be set or both null. `modelId` is trimmed and limited to 200 characters. A connection must exist,
be enabled, and support the requested purpose. The backend checks the provider's general purpose
support, but does not verify the individual model's capability at assignment time. For example,
selecting an OpenAI chat model under `EMBEDDING` may fail when vector workflows are added; the
page should prefer known embedding choices and display manual entries as unverified.

```shell
curl http://localhost:8080/api/admin/model-connections \
  -H 'Authorization: Bearer session-token'

curl http://localhost:8080/api/admin/model-assignments \
  -H 'Authorization: Bearer session-token'
```

### Frontend errors and examples

| Status | Situation | Page behavior |
| --- | --- | --- |
| `400` | Invalid name, missing key, incomplete assignment, wrong purpose/provider combination, or provider change. | Keep form values and show `error` beside the relevant form. |
| `401` | Login session missing or expired. | Return to login. |
| `403` | Logged-in user is not `ADMIN`. | Hide Model Settings and show an access-denied state if opened directly. |
| `404` | A connection ID no longer exists. | Refresh the connection list and clear the selection. |
| `502` | The provider model catalog could not be loaded or tested. | Show a retry action; allow manual model ID input when appropriate. |
| `503` | The server encryption key is absent or cannot decrypt a stored credential. | Show a server-configuration error; do not ask the user to repeatedly enter the key. |

All errors use `{"error":"..."}`. Example calls:

```shell
curl http://localhost:8080/api/admin/model-providers \
  -H 'Authorization: Bearer session-token'

curl -X POST http://localhost:8080/api/admin/model-connections \
  -H 'Authorization: Bearer session-token' -H 'Content-Type: application/json' \
  -d '{"name":"Main OpenAI account","provider":"OPENAI","apiKey":"provider-secret-key"}'

curl -X PUT http://localhost:8080/api/admin/model-assignments/ANSWER \
  -H 'Authorization: Bearer session-token' -H 'Content-Type: application/json' \
  -d '{"connectionId":"3e246d55-2b69-4e13-88bb-8e31dd479e7d","modelId":"provider-model-id"}'
```

## Keyword Search API

### Where the frontend uses it

This endpoint searches the **content of indexed documents**. It is useful in these frontend flows:

| Frontend location | When to call | Parameters | What to show |
| --- | --- | --- | --- |
| Knowledge-base content search | The user submits a keyword or phrase. | `query`; omit `documentId`. | Matching passages from all indexed documents, grouped or labeled by `fileName`, with source locations. |
| Search within a document | The user searches while viewing one document's metadata or details. | `query` and that document's `documentId`. | Only passages from that logical document. |
| Search result pagination | The user selects another results page. | Keep the same `query`, `documentId`, and `size`; change `page`. | Replace the result list and update the page controls. |

This is a **passage search**, not the document-list filename filter. `GET /api/documents?document=...`
filters document names; `GET /api/search?query=...` matches chunk text. Call this endpoint after
the user submits a non-blank search, and reset `page` to `0` when the query or document filter
changes. An application may debounce an interactive search box, but should avoid requesting every
intermediate keystroke. Upload, indexing, document listing, and metadata views do not need to
call this endpoint unless they offer a passage-search action.

This endpoint returns evidence only. It does not generate an answer or call a language model. A
future question-answering UI should call its dedicated answer endpoint when one exists; this
endpoint can still support a separate "find source passages" view. There is currently no endpoint
that downloads an indexed source file or opens it at a page number, so the frontend should display
source locations as labels unless it has its own document viewer. A `sourceUrl` from a web import
can be offered as an external link.

### Request

Searches the `content` field of indexed chunks using Elasticsearch relevance scoring. Results
include complete chunk text and source references. The optional `documentId` filter is an exact
logical document ID, such as one returned by `GET /api/documents` or an indexing response. The
request has no body; URL-encode query parameters and send the login token in the `Authorization`
header.

```http
GET /api/search?query=access%20policy&documentId=company-handbook&page=0&size=20
Authorization: Bearer <accessToken>
```

| Query parameter | Required | Default | Frontend use and constraints |
| --- | --- | --- | --- |
| `query` | Yes | None | The user's search text; non-blank, at most 500 characters after trimming. |
| `documentId` | No | All documents | Pass the selected document's exact ID for within-document search; at most 200 characters after trimming. An empty value behaves like omission. |
| `page` | No | `0` | Zero-based page index, non-negative. Start at `0`, not `1`. |
| `size` | No | `20` | Number of hits per page, from `1` to `100`. The requested page must fit within the first 10,000 results: `(page + 1) * size <= 10000`. |

Example frontend request using the token returned by `POST /api/auth/login`:

```javascript
const query = searchText.trim();
if (!query) return; // Keep the search action disabled for empty input.
const params = new URLSearchParams({ query, page: String(page), size: "20" });
if (selectedDocumentId) params.set("documentId", selectedDocumentId);

const response = await fetch(`/api/search?${params}`, {
  headers: { Authorization: `Bearer ${accessToken}` },
});
const data = await response.json();
if (response.status === 401) {
  // Start the application's login flow before retrying.
  return;
}
if (!response.ok) throw new Error(data.error ?? "Search failed");
// data.items contains the passages for the requested page.
```

`URLSearchParams` handles spaces, Chinese text, and other characters in `query`. Reuse the same
bearer token as the other protected APIs. The endpoint uses `GET`, so do not send a JSON body.

### Success response and rendering

Status: `200 OK`

```json
{
  "items": [
    {
      "documentId": "company-handbook",
      "ingestionId": "ing-5ee39f20",
      "fileName": "handbook.pdf",
      "documentType": "PDF",
      "chunkIndex": 4,
      "content": "The access policy applies to all staff.",
      "startOffset": 1200,
      "endOffset": 1239,
      "sourceReferences": [
        {"pageNumber": 3, "slideNumber": null, "sheetName": null,
         "sectionTitle": null, "elementType": "PAGE", "sourceUrl": null,
         "sourceTitle": null, "webPageNumber": null}
      ],
      "score": 2.5
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Results are ordered by Elasticsearch relevance score. Render each `items[]` entry as one result
card or row:

| Response field | Suggested frontend use |
| --- | --- |
| `fileName`, `documentType` | Identify the source document. Several hits may have the same document. |
| `content` | Display the matching passage. It is the full chunk text, not a highlighted snippet; render it as text rather than HTML. |
| `sourceReferences` | Show available page, slide, sheet, section, or web URL information beside the passage. Fields may be `null`, and one chunk may have multiple references. |
| `documentId`, `ingestionId`, `chunkIndex` | Stable identifiers for a result within one indexed generation. Use `documentId` to open existing document metadata through `GET /api/documents/{documentId}` if needed. |
| `startOffset`, `endOffset` | Zero-based half-open offsets in normalized source text. Useful for a viewer that can position itself in the text; they are not page numbers. |
| `score` | Elasticsearch relevance score. The API already orders results by it; do not display it as a confidence percentage or compare it across different searches. |
| `page`, `size`, `totalElements`, `totalPages` | Drive pagination controls. Show the next page only when `page + 1 < totalPages` and the next request stays within the 10,000-result limit. |

For PDF, use `sourceReferences[].pageNumber`; for PowerPoint, use `slideNumber`; for Excel, use
`sheetName`; for web imports, use `sourceUrl`, `sourceTitle`, and `webPageNumber` when present.
`sectionTitle` can provide additional context. Only show fields that have values. A hit may have
multiple source references, so avoid assuming `sourceReferences[0]` is the only location.

No matches still returns `200 OK`, for example:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Show an empty-results message for this response, not an error. A document must be successfully
indexed before its passages can appear in search.

### Error handling

| Status | When it happens | Frontend handling |
| --- | --- | --- |
| `400 Bad Request` | Blank or overlong `query`, invalid `page` or `size`, overlong `documentId`, or a page beyond the 10,000-result window. | Keep the entered text, show `error`, and let the user correct the search or return to an earlier page. |
| `401 Unauthorized` | Missing, invalid, expired, or disabled-user bearer session. | Send the user through the application's login flow. |
| `502 Bad Gateway` | Elasticsearch could not complete the search. | Show a temporary search failure and allow retry. Do not present it as zero matches. |

Examples of the common error response are `{"error":"query must not be blank"}` for `400`,
`{"error":"Authentication is required"}` for `401`, and
`{"error":"The search index could not be queried"}` for `502`.

Search currently matches indexed text by keywords. Alternate wording with no matching terms may
return no hits; this endpoint does not perform semantic or vector search.

```shell
curl -G http://localhost:8080/api/search \
  -H 'Authorization: Bearer session-token' \
  --data-urlencode 'query=access policy' \
  --data-urlencode 'documentId=company-handbook'
```

## Chunking Behavior

Chunking is deterministic for identical normalized content and settings. When content exceeds
`maxCharacters`, the chunker searches the latter half of the available range and selects the latest
suitable boundary in this order:

1. Paragraph boundary.
2. Line boundary.
3. Sentence-ending punctuation.
4. Whitespace.
5. Hard character limit.

Leading and trailing whitespace at chunk boundaries is excluded. Natural-boundary trimming may make
the effective overlap smaller than `overlapCharacters`.

## Change Policy

Every endpoint change must update this document in the same commit. Each endpoint entry must include
its purpose, method, path, authentication, request schema, constraints, success response, error
statuses, and at least one executable example where practical.
