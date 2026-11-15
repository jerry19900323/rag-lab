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
| `403 Forbidden` | The authenticated user is not an administrator for a protected management action. |
| `404 Not Found` | The requested document or model connection does not exist. |
| `409 Conflict` | The same logical document is already being indexed or deleted. |
| `502 Bad Gateway` | Elasticsearch failed while storing, searching, or deleting document chunks. |
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
| `DELETE` | `/api/documents/{documentId}` | Admin bearer | Delete an indexed document, its chunks, and ingestion history. |
| `POST` | `/api/web-pages/preview` | Bearer | Fetch public HTML pages and preview chunks. |
| `POST` | `/api/web-pages/index` | Bearer | Fetch public HTML pages and persist their chunks. |
| `GET` | `/api/search` | Bearer | Search indexed chunks by configured embeddings, or by keyword when no embedding model is configured. |
| `POST` | `/api/chat/ask` | Bearer | Generate a single chat reply or a document-grounded answer. |
| `POST` | `/api/chat/ask/stream` | Bearer | Stream retrieval progress, sources, and model tokens as SSE. |
| `POST` | `/api/chat/conversations` | Bearer | Start a new conversation and return its ID. |
| `GET` | `/api/chat/conversations` | Bearer | List the current user's active conversations. |
| `GET` | `/api/chat/conversations/{conversationId}/messages` | Bearer | Read the stored turns of one conversation. |
| `GET` | `/api/chat/conversations/{conversationId}/messages/page` | Bearer | Page through all completed turns within the conversation's lifetime. |
| `GET` | `/api/chat/conversations/{conversationId}/traces` | Bearer | Review recent question execution traces. |
| `DELETE` | `/api/chat/conversations/{conversationId}` | Bearer | Delete one conversation and its stored turns. |
| `GET` | `/api/admin/model-providers` | Admin bearer | List supported provider presets and purposes. |
| `GET` | `/api/admin/model-usage-types` | Admin bearer | List selectable model usage types. |
| `GET` | `/api/admin/model-connections` | Admin bearer | List saved connections, including API keys for editing. |
| `POST` | `/api/admin/model-connections` | Admin bearer | Save a provider connection; Cloudflare also saves its selected model ID and usage type. |
| `PUT` | `/api/admin/model-connections/{id}` | Admin bearer | Edit or disable a connection. |
| `DELETE` | `/api/admin/model-connections/{id}` | Admin bearer | Delete a connection and clear its purpose assignments. |
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

Parses and chunks a document, generates a short file-level description, records its lifecycle
in MySQL, and replaces the current document generation in Elasticsearch. A Redis lock prevents
concurrent indexing of the same logical document. The file-level description is generated from
representative beginning, middle, and ending excerpts: a configured `DOCUMENT_SUMMARY` model is
used first, otherwise `ANSWER`, then `CHAT`. If none is configured, the backend stores a short
extractive sample so uploads still work. A configured summary model failure fails this upload;
it is not silently ignored. The description is a search locator, not evidence for the final answer.

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
  "summary": "Project notes describing access approval and staff responsibilities.",
  "indexedAt": "2026-11-01T07:30:00Z"
}
```

| Status | Condition | Example `error` |
| --- | --- | --- |
| `409` | Another request is indexing the same `documentId`. | `Document ... is already being indexed` |
| `502` | The configured summary or embedding model failed, or Elasticsearch could not store the chunks and file description. | `Document summary request failed`, `Embedding provider request failed`, or an Elasticsearch error. |

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
      "summary": "Employee handbook covering access approval and staff responsibilities.",
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

Document status values are `PROCESSING`, `COMPLETED`, or `FAILED`. `summary` is available in
the list and detail responses after a successful new upload; it is `null` for older documents
until they are uploaded again. The frontend can display it under the filename.

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

### Delete an Indexed Document

Deletes one logical document, all of its indexed chunks, and its MySQL ingestion history. This
works for both uploaded files and indexed web pages. The service does not retain original upload
bytes, so there is no separate stored source file to remove. The request requires an active
administrator session (`user.role === "ADMIN"`).

```http
DELETE /api/documents/{documentId}
Authorization: Bearer <adminAccessToken>
```

`documentId` is the exact ID returned by `POST /api/documents/index`,
`POST /api/web-pages/index`, or `GET /api/documents`. It must contain 1–200 characters. Do not
send an ingestion ID, filename, or web URL in its place. The request has no body.

Status: `204 No Content` after the search chunks and metadata are removed. The response body is
empty. Subsequent `GET /api/documents/{documentId}` returns `404`, and its chunks no longer
appear in `/api/search`.

| Status | Condition | Frontend behavior |
| --- | --- | --- |
| `401` | No valid login session. | Return to login. |
| `403` | The user is not an administrator. | Hide the delete action; show access denied if attempted directly. |
| `404` | The document no longer exists. | Refresh the list and remove the stale row. |
| `409` | The document is being indexed or another delete holds its lock. | Keep the row and offer retry after the operation ends. |
| `502` | Elasticsearch did not delete all chunks. | Keep the row and show the error; metadata is retained so the request can be retried. |
| `503` | MySQL could not delete metadata after search chunks were removed. | Keep the row and retry later. Repeating the delete is safe: chunk deletion is idempotent. |

Error bodies use `{"error":"..."}`. For example, a search-index deletion failure returns
`{"error":"The document could not be deleted from the search index"}`. A successful delete
is permanent from this service; uploading the same file again creates a new document unless
the client explicitly supplies the same `documentId`.

**Frontend placement and interaction:**

| Page | When to show Delete | On confirm | On success |
| --- | --- | --- | --- |
| Document upload page | After indexing returns `201` and a `documentId`, show Delete on the indexed result card or its document list row. | Show the filename and a permanent-deletion warning; disable the button while the request runs. | Clear the indexed result and refresh the document list/count. |
| Document management list or detail | For an indexed document row/detail viewed by an administrator. | Use that row's `documentId`; preserve current filter/page while loading. | Refresh the list; if the current page becomes empty, move to the previous page. Close a deleted detail view. |
| Search results | Only if that page intentionally offers document management actions. | Explain that deleting one document removes all its search hits. | Refresh the results for the same query. |
| Web page import result/list | After web indexing returns `201` with `document.documentId`. | Explain that this removes the local index and metadata, not the remote website. | Remove the imported item and refresh document/search lists. |

For a file merely selected in the browser, or a `POST /api/documents/preview` result, a
“Remove file” action only clears local form state; it must **not** call this API because no
indexed document exists. The same applies to `POST /api/web-pages/preview`.

Example frontend call after confirmation:

```javascript
const response = await fetch(`/api/documents/${encodeURIComponent(documentId)}`, {
  method: "DELETE",
  headers: { Authorization: `Bearer ${accessToken}` },
});
if (response.status === 204) {
  // Clear the indexed result, then refetch the current document list/search results.
} else {
  const { error } = await response.json();
  // Keep the item visible and display error; handle 401/403/404 as described above.
}
```

```shell
curl -X DELETE http://localhost:8080/api/documents/company-handbook \
  -H 'Authorization: Bearer session-token'
```

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
    "summary": "Articles from example.com about the imported topics.",
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
   or delete it, load its available models, and test catalog access. The connection list
   returns `apiKey` so the existing Edit action can prefill its password input. Show a Delete action
   on every saved connection row, with a confirmation dialog.
2. **Add or change a model:** choose one usage type, an enabled connection, and one model ID.
   For a new Cloudflare connection, submit both fields in `POST /api/admin/model-connections`.
   For an existing connection, save with `PUT /api/admin/model-assignments/{purpose}`. Each usage type has one selected
   model; saving another model for the same type replaces it. Show the current selections from
   `GET /api/admin/model-assignments`. To remove one selection, send both IDs as `null` through
   that same `PUT` endpoint. Allow manual model ID entry when the catalog is incomplete or
   unavailable, and label it as unverified.

The add flow uses these calls in order:

1. If the provider connection does not exist yet, save its name, provider, and API key with
   `POST /api/admin/model-connections`. For Cloudflare, the form must also ask for **usage type**
   and **model ID** and send both in this request. Keep the returned `connectionId`. If the
   connection already exists, select it from `GET /api/admin/model-connections`.
2. For an existing connection, call `GET /api/admin/model-connections/{id}/models` and choose
   a `modelId`. The form also lets the administrator enter a model ID manually. For a new
   Cloudflare connection, enter the model ID directly in the creation form; the model list
   becomes available after the connection has been saved.
3. For an existing connection, choose **one** `usageType` from
   `GET /api/admin/model-usage-types` and send the connection ID and model ID to
   `PUT /api/admin/model-assignments/{usageType}`. For a new Cloudflare connection, the first
   request already saves the model assignment; do not send this `PUT` again.
4. Refresh `GET /api/admin/model-assignments` and display the saved model next to its usage type.
   If that type already has a model, explain that saving will replace the existing selection.

On page load, request `/api/admin/model-providers`, `/api/admin/model-usage-types`,
`/api/admin/model-connections`, and `/api/admin/model-assignments`. Populate provider and usage
dropdowns from the responses. The model dropdown is loaded **after** a connection is saved,
using its ID and `GET /api/admin/model-connections/{id}/models`; “Refresh models” calls that endpoint again. Model lists are
queried live and may change. The response is not a promise that a particular model supports
every purpose; `supportedPurposes` describes the provider protocol, not each individual model.
The “Test connection” button tests access to the model catalog and displays `UP` plus the
number of discovered models. It does **not** invoke the selected chat or embedding model.

Each selected usage is stored as one row in `rag_model_assignments`, with `usage_type` as the
primary key. The backend resolves a model by that exact usage and ignores an unassigned or
disabled connection. When `EMBEDDING` is configured, new indexing and `/api/search` invoke that
model. `POST /api/chat/ask` uses `CHAT` for ordinary chat and `ANSWER` for document questions;
either purpose falls back to the other when its preferred assignment is absent or disabled.

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

| `provider` | Page label | Catalog source | Known model protocols | API key |
| --- | --- | --- | --- | --- |
| `OPENAI` | OpenAI | OpenAI models API | Chat, text embedding | Required |
| `DEEPSEEK` | DeepSeek | DeepSeek models API | Chat | Required |
| `DASHSCOPE` | Alibaba Cloud Model Studio (international) | International Model Studio models API | Chat, text embedding | Required |
| `ANTHROPIC` | Claude | Anthropic models API | Chat | Required |
| `GEMINI` | Gemini | Gemini models API | Chat, text embedding | Required |
| `CLOUDFLARE` | Cloudflare Workers AI | Cloudflare account's Workers AI model search API | Chat, text embedding | Required |
| `OLLAMA` | Ollama (local) | `127.0.0.1:11434/api/tags` on the backend host | Chat, text embedding | Not used |

The Model Studio preset uses the **international** catalog. Its API key must be valid for that
region. The Ollama preset connects to the **backend server's** localhost, not the browser's
computer. A connection may override its provider's default API base URL. A model missing from
a provider's catalog may be entered manually in the assignment
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
  "supportedPurposes": ["ANSWER", "CHAT", "ROUTING", "QUERY_REWRITE", "DOCUMENT_SUMMARY", "INFORMATION_EXTRACTION", "TRANSLATION", "EMBEDDING"]
}
```

Use `provider` as the form value and `displayName` as its visible label. `supportedPurposes`
reports known chat and text embedding protocol support. Do not hide other values returned by
`GET /api/admin/model-usage-types` just because they are absent from `supportedPurposes`; their
provider and individual model support is unverified.

### List usage types for the add-model form

```http
GET /api/admin/model-usage-types
Authorization: Bearer <adminAccessToken>
```

Status: `200 OK`. Returns `[{"usageType":"ANSWER","displayName":"Document answers"}, ...]`.
Use `usageType` as the selection value and `displayName` as its label. The available values are
`ANSWER`, `CHAT`, `ROUTING`, `QUERY_REWRITE`, `DOCUMENT_SUMMARY`, `INFORMATION_EXTRACTION`,
`TRANSLATION`, `EMBEDDING`, `MULTIMODAL_EMBEDDING`, `RERANK`, `VISION`, `OCR`,
`SPEECH_TO_TEXT`, `TEXT_TO_SPEECH`, `IMAGE_GENERATION`, and `VIDEO_GENERATION`.
Only one usage type is selected when saving a model. A type in this list is a configuration
option; it does not mean its runtime workflow is implemented or that the selected model supports
it.

```shell
curl http://localhost:8080/api/admin/model-usage-types \
  -H 'Authorization: Bearer session-token'
```

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
    "accountId": "organization-or-account-id",
    "apiBaseUrl": "https://api.openai.com/v1",
    "enabled": true,
    "hasApiKey": true,
    "apiKey": "provider-secret-key",
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
  "accountId": "organization-or-account-id",
  "apiBaseUrl": "https://api.openai.com/v1",
  "apiKey": "provider-secret-key",
  "enabled": true
}
```

Status: `201 Created`; response is one connection object in the same shape as the list.
The request fields are:

| Field | Required on create | Frontend meaning |
| --- | --- | --- |
| `name` | Yes | Connection name; 1–100 characters after trimming. |
| `provider` | Yes | One of the codes from `GET /api/admin/model-providers`; use `CLOUDFLARE` for Workers AI. |
| `apiKey` | Yes, except `OLLAMA` | Provider API key or Cloudflare Workers AI API token. The list, create, and update responses return this value for the edit form. |
| `accountId` | For `CLOUDFLARE` | Cloudflare Account ID. For other providers, optional metadata; maximum 200 characters. |
| `apiBaseUrl` | No | Optional HTTP(S) base URL; maximum 500 characters. Cloudflare's official URL is derived from `accountId` if omitted. |
| `usageType` | For `CLOUDFLARE` | One code from `GET /api/admin/model-usage-types`, such as `ANSWER` or `EMBEDDING`. |
| `modelId` | For `CLOUDFLARE` | Exact active model identifier, such as `@cf/meta/llama-3.1-8b-instruct-fast`; 1–200 characters after trimming. |
| `enabled` | No | Defaults to `true`; must be enabled when saving a model ID in the same request. |

For other providers, `usageType` and `modelId` may also be sent together when creating a
connection. Either send both or omit both. A selected usage must be supported by that provider.
`accountId` is optional provider account metadata outside Cloudflare;
it is stored and returned but is not sent automatically as a provider authentication header.
`apiBaseUrl` is an optional HTTP(S) base URL (maximum 500 characters), without credentials,
query, or fragment. Omit it to use the provider preset. The URL is stored and returned; the
backend also uses it for model-list requests. For OpenAI-compatible APIs, enter the base URL
ending at `/v1`, not the full `/models` URL. Other providers should use the base path preceding
their models endpoint; for Ollama, enter the server root such as `http://127.0.0.1:11434`.
For `CLOUDFLARE`, `accountId`, `usageType`, and `modelId` are required when creating a connection.
Enter a Cloudflare Workers AI API token as `apiKey`;
the model list comes from the account's Workers AI model search API. If `apiBaseUrl` is omitted,
the backend resolves the model for future calls with
`https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/v1`. A custom `apiBaseUrl` is
stored for future inference calls, while catalog discovery still uses Cloudflare's official
account endpoint. Use an active chat-capable model ID such as
`@cf/meta/llama-3.1-8b-instruct-fast` for chat or an appropriate `@cf/...` embedding model.
The older `@cf/meta/llama-3.1-8b-instruct` example was deprecated; do not copy it into a new
connection. Model availability can change, so verify the selected ID in Cloudflare's current
model catalog. The account ID can contain letters, digits, `_`, and `-`.
See [Cloudflare's OpenAI-compatible API](https://developers.cloudflare.com/workers-ai/configuration/open-ai-compatibility/),
[model search API](https://developers.cloudflare.com/api/resources/ai/subresources/models/methods/list/),
and [model deprecation changelog](https://developers.cloudflare.com/changelog/product/workers-ai/).
Do not persist the form's `apiKey` in browser storage.

For the **Add Cloudflare model** form, show input fields for Account ID, API token, usage type,
and model ID. Make all four required before enabling Save. The model ID can be typed directly;
it need not appear in the live model list. If the selected usage already has a model, show the
existing model ID and explain that saving will replace it.
For example, to add a document-answering model in one request:

```http
POST /api/admin/model-connections
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{
  "name": "Cloudflare Workers AI",
  "provider": "CLOUDFLARE",
  "accountId": "your-cloudflare-account-id",
  "apiKey": "your-workers-ai-api-token",
  "usageType": "ANSWER",
  "modelId": "@cf/meta/llama-3.1-8b-instruct-fast"
}
```

Status: `201 Created`. The response is the connection object above, including `apiKey`.
The selected model ID is stored in `rag_model_assignments.model_id` under `usage_type=ANSWER`;
the connection response does not contain `modelId` or `usageType`. Call
`GET /api/admin/model-assignments` to display it after creation. If the type already has
another model, this request replaces that type's assignment. Creation and assignment are one
database transaction. Missing Account ID, usage type, model ID, or API token returns `400`.
For an existing Cloudflare connection, use `PUT /api/admin/model-assignments/{usageType}` to
change its model ID; the connection update endpoint edits connection settings only.

```shell
curl -X POST http://localhost:8080/api/admin/model-connections \
  -H 'Authorization: Bearer session-token' -H 'Content-Type: application/json' \
  -d '{"name":"Cloudflare Workers AI","provider":"CLOUDFLARE","accountId":"your-cloudflare-account-id","apiKey":"your-workers-ai-api-token","usageType":"ANSWER","modelId":"@cf/meta/llama-3.1-8b-instruct-fast"}'
```

When Edit is opened, use the selected row from `GET /api/admin/model-connections` to prefill
the form. `apiKey` is returned as the saved plaintext value; render it in a password input
(`type="password"`) so it is masked on the page. For a connection without a key, `apiKey` is
`null`. If a stored key cannot be decrypted, the list request returns `503` with an `error`.
Do not put the key in browser storage, URLs, analytics, or console logs.

Edit an existing connection using `PUT /api/admin/model-connections/{id}` with the connection
fields from the JSON example; `usageType` and `modelId` are only used on creation. Status:
`200 OK`; the response is the updated connection object including `apiKey`. Send `apiKey: null`
or omit it to retain the stored key. Send a new key to replace it. If `enabled` is omitted on
update, its previous value is retained. The `provider` must remain the original provider;
create a new connection to switch providers. Omitting `accountId` or `apiBaseUrl` on update
retains the stored value; send an empty string to clear either field. Set `enabled: false` to
disable a connection.

```shell
curl -X PUT http://localhost:8080/api/admin/model-connections/3e246d55-2b69-4e13-88bb-8e31dd479e7d \
  -H 'Authorization: Bearer session-token' -H 'Content-Type: application/json' \
  -d '{"name":"Main OpenAI account","provider":"OPENAI","enabled":false}'
```

### Delete a model connection

```http
DELETE /api/admin/model-connections/{id}
Authorization: Bearer <adminAccessToken>
```

`id` is the `connectionId` from `GET /api/admin/model-connections`. The request has no body.
Status: `204 No Content`, with an empty body. The connection and its encrypted API key are
removed from MySQL. Any usage assignment referencing this
connection is cleared in the same database transaction; those assignments remain in the list
with `connectionId: null` and `modelId: null`. Other connections and assignments are unchanged.
This deletes **our saved connection only**; it does not revoke the provider API key at the
provider or delete any remote model.

On the AI configuration page, display Delete beside each saved connection. Before calling it,
find matching entries in `GET /api/admin/model-assignments` and show their purpose names in the
confirmation dialog, for example: “Deleting Main OpenAI account will also clear ANSWER and
ROUTING.” Disable the row's actions during the request. After `204`, remove the row, clear the
selected connection and model dropdown, and refetch assignments so each affected purpose shows
“Not configured.” If the request fails, keep the row and selections unchanged.

| Status | Condition | Frontend behavior |
| --- | --- | --- |
| `401` | No valid login session. | Return to login. |
| `403` | Logged-in user is not an administrator. | Hide Model Settings or show access denied. |
| `404` | The connection was already removed. | Refresh connections and assignments. |
| `503` | MySQL could not complete deletion. | Keep the row and offer retry; assignment clearing is rolled back. |

```javascript
const response = await fetch(`/api/admin/model-connections/${encodeURIComponent(connectionId)}`, {
  method: "DELETE",
  headers: { Authorization: `Bearer ${accessToken}` },
});
if (response.status === 204) {
  // Refetch connections and purpose assignments; clear the selected model dropdown.
} else {
  const { error } = await response.json();
  // Keep the row visible and display error.
}
```

```shell
curl -X DELETE http://localhost:8080/api/admin/model-connections/3e246d55-2b69-4e13-88bb-8e31dd479e7d \
  -H 'Authorization: Bearer session-token'
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
For most providers the request uses the connection's `apiBaseUrl` when configured, otherwise
the preset URL. Cloudflare model discovery always uses its Account ID and official account
model search endpoint.

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

Status: `200 OK`. Returns one item for every code in `GET /api/admin/model-usage-types`.
With no configuration, all items have null selection fields. A sample of the response is:

```json
[
  {"purpose":"ANSWER","connectionId":null,"modelId":null,"updatedAt":null},
  {"purpose":"EMBEDDING","connectionId":null,"modelId":null,"updatedAt":null},
  {"purpose":"ROUTING","connectionId":null,"modelId":null,"updatedAt":null}
]
```

The real response includes the remaining usage types as well. `purpose` here is the same code
named `usageType` in the dropdown response. Each code appears once, so the page can display one
model per type. A row with `connectionId: null` and `modelId: null` means “Not configured.”

In the add-model form, bind the selected model to the selected usage type. Replace `ANSWER` in
the path with the chosen `usageType`:

```http
PUT /api/admin/model-assignments/ANSWER
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{"connectionId":"3e246d55-2b69-4e13-88bb-8e31dd479e7d","modelId":"provider-model-id"}
```

Status: `200 OK`; returns the saved assignment, for example
`{"purpose":"ANSWER","connectionId":"3e246d55-2b69-4e13-88bb-8e31dd479e7d","modelId":"provider-model-id","updatedAt":"2026-11-04T08:00:00Z"}`.
The database stores the selected code in `rag_model_assignments.usage_type`. That column is the
primary key, so saving the same type again replaces its previous model. The frontend should show
the current assignment before replacing it, then refresh `GET /api/admin/model-assignments`.
For a conversation model, select `ANSWER` (document answers) or `CHAT` (general chat) and put
the provider's **conversation model ID** in `modelId`. This value is already persisted in
`rag_model_assignments.model_id`; it is not duplicated on the connection record.

To remove the selected model for one type, send `{"connectionId":null,"modelId":null}` to the
same path and refresh the list. This clears the selection for that usage type; it does not delete
the provider connection or remote model. Both fields must be set or both null. `modelId` is trimmed and
limited to 200 characters. A connection must exist and be enabled. Known chat and text embedding
provider restrictions are checked; other type and individual model support is not verified with
an inference request. Show manual model IDs as unverified.

For example, the frontend can send the selected usage and model like this:

```javascript
const response = await fetch(
  `/api/admin/model-assignments/${encodeURIComponent(usageType)}`,
  {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ connectionId, modelId }),
  },
);
if (!response.ok) {
  const { error } = await response.json();
  throw new Error(error);
}
// Refetch GET /api/admin/model-assignments to update the page.
```

For a Remove action, send the same request with both `connectionId` and `modelId` set to `null`.

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
| `503` | The server encryption key is absent, cannot decrypt a stored credential, or MySQL could not delete a connection. | Show the returned error; keep the row on delete failure. |

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

## Chat and Document Answer API

### Frontend conversation flow

The chat page supports multiple independent conversations. The frontend owns the **currently selected
conversation ID**; the backend owns its message history. Send the ID on every question, including
follow-up questions. Do not send prior messages in the request.

| User action | Frontend request | What to do with the response |
| --- | --- | --- |
| Open chat page or refresh | `GET /api/chat/conversations` | Render the returned conversations, newest `updatedAt` first. Keep the selected ID if it still exists; otherwise select one or show an empty chat. |
| Click **New chat** | `POST /api/chat/conversations` with bearer token and no body | Save the returned `conversationId` as the active ID and clear the displayed messages. This creates an empty conversation; it does not send a question. |
| Send a question | `POST /api/chat/ask/stream` (or `/api/chat/ask`) with `question` and the active `conversationId` | Append the answer and optional sources to the active conversation. Refresh the conversation list if you show recent activity. |
| Select an existing conversation | `GET /api/chat/conversations/{conversationId}/messages/page?page=0&size=20` | Render the first page of saved turns and load more pages as needed. Pass this ID with subsequent questions. |
| Inspect a wrong answer | `GET /api/chat/conversations/{conversationId}/traces?page=0&size=20` | Show the route, retrieval query, candidate judgments, timings, and error for each recent question. Match live status events using `requestId`. |
| Delete a conversation | `DELETE /api/chat/conversations/{conversationId}` | Remove it from the list. If it was active, clear its messages and select another conversation or show the empty state. |

All chat endpoints require `Authorization: Bearer <accessToken>`. A conversation belongs to the
authenticated user: IDs belonging to another user or no longer present cannot be read, used for
questions, or deleted. On `400`, show the returned `error` and refresh the list because the
conversation may have expired or been deleted. On `401`, return to login. A client should not
invent an ID; always use one returned by `POST /api/chat/conversations` or the list endpoint.

```http
POST /api/chat/conversations
Authorization: Bearer <accessToken>

HTTP/1.1 201 Created
Content-Type: application/json

{"conversationId":"df6a1786-54c1-4dd5-849e-5c362997a26e","updatedAt":"2026-11-14T08:30:00Z"}
```

`GET /api/chat/conversations` returns `200 OK` and an array of the same objects, or `[]`.
`updatedAt` is an ISO 8601 UTC timestamp, set when the conversation is created and updated
after each completed answer. Use the ID as the stable list key; the API does not currently
provide a title. `GET /api/chat/conversations/{conversationId}/messages` returns `200 OK` and
an array in oldest-to-newest order, for example:

```json
[{"question":"李二龙是谁？","answer":"李二龙是一名 Java 开发工程师。[1]"}]
```

`GET /api/chat/conversations/{conversationId}/messages` remains the recent-turn response
for existing clients. For full history within the conversation's lifetime, use
`GET /api/chat/conversations/{conversationId}/messages/page?page=0&size=20`. It returns
`{"items":[{"question":"...","answer":"..."}],"page":0,"size":20,"totalElements":12,"totalPages":1}`
in oldest-to-newest order. `page` starts at `0`; `size` must be `1`–`100`. Use `totalElements`
to show the exact number of completed turns and request further pages. Older turns saved before
the archive feature was added cannot be reconstructed if they were already trimmed.

History endpoints return completed question/answer pairs, not source cards or partial stream
tokens. Keep sources from the live response if they need to remain visible while the page stays
open. `DELETE /api/chat/conversations/{conversationId}` returns `204 No Content` and removes
the conversation, its turns, and its execution traces. A newly created conversation has no
messages. The backend retains all completed turns in a paged archive for the conversation's
24-hour inactivity lifetime; it sends only the latest 10 to the model by default. A completed
turn refreshes the archive TTL. `CHAT_HISTORY_MAX_TURNS` controls the model's recent context
window, and `CHAT_HISTORY_TTL` controls expiration. The frontend can rebuild the message list
after a refresh by paging through the archive; it need not persist conversation messages.

`GET /api/chat/conversations/{conversationId}/traces?page=0&size=20` uses the same page format
and returns up to the latest 100 execution traces for that conversation, newest first. Each item has
`requestId`, `question`, `status` (`COMPLETED` or `FAILED`), `startedAt`, `finishedAt`, `steps`,
and `error` (null on success). `steps[]` contains the same status details as the live stream,
including `elapsedMs` since request start and `durationMs` since the preceding step. This
lets the page show a compact progress list and an expandable troubleshooting view. Traces
expire with the active conversation. This endpoint never returns model credentials or the
full model prompts.

For compatibility, an ask request without `conversationId` still uses the old implicit
per-user history, which does not appear in `GET /api/chat/conversations`. The chat page should
always create or select an explicit conversation before sending a question.

### Where the frontend uses it

Use `POST /api/chat/ask` for two conversation flows:

| Page or action | Request | Backend behavior | Frontend display |
| --- | --- | --- | --- |
| Ordinary chat, including greetings such as “你好” | Send `question`; explicit `mode: "CHAT"` can be used to rule out document search. | The backend routes to general chat or relevant conversation history. | Show `answer`; `sources` is `[]`. |
| Question over indexed documents | Send `question`; `mode: "DOCUMENT"` is optional. Do not send `documentId`. | The backend decides whether this question needs documents, history, both, or neither. For a document route, it finds candidate files from their descriptions, selects relevant files, then searches their chunks. | Show `answer` and source cards when document evidence was selected. |
| Question about one selected document | Send `question` and that document's `documentId`. | If routed to document search, retrieval is restricted to that document. | Show the answer and selected source cards. |

For model-generated answers, the administrator must have at least one enabled `CHAT` or `ANSWER`
model assignment. When routing selects document evidence, retrieval uses keyword matching and
adds semantic retrieval when `EMBEDDING` is configured. Both chat endpoints store completed turns in Redis under the
authenticated user and supplied conversation ID. The frontend sends the current `question`,
active `conversationId`, and bearer token; it does not send prior messages. Before answering,
the backend reads the selected conversation's retained turns and asks the configured `ROUTING`
model to choose `GENERAL`, `HISTORY`, `DOCUMENT`, or `BOTH`. If `ROUTING` is not assigned, the
selected `ANSWER`/`CHAT` model performs this routing step. A configured `ROUTING` assignment is
used for every question. `mode: "CHAT"` forbids document search but still permits history.
The frontend does not choose the route.
The router returns a structured decision with `route`, `searchQuery`, `reason`, and optional
`documentHint`. For document routes, `searchQuery` drives file-level and chunk-level search. When the question
explicitly names a filename including its extension, `documentHint` checks saved filenames
using an indexed exact match; a single matching
document narrows ES to that document ID. An explicit `documentId` supplied by the frontend
takes precedence. Otherwise, ES searches one summary profile per uploaded file and returns at
most five candidate files. The routing model reads those file descriptions and chooses up to
three relevant document IDs with a reason for each decision. Keyword and, when configured,
vector chunk retrieval run only inside those selected files. File descriptions are never
treated as answer evidence; the final answer uses accepted chunks.

Older documents without summary profiles can still be found through a fallback chunk search
when no suitable profile is selected or the selected files yield no accepted passage. Once
existing files are uploaded again, they gain profiles and use the narrower path. Reupload with
the same `documentId` replaces the old chunk generation and its profile; deletion removes both.

For `HISTORY`, the latest context turns and the **exact archived total turn count** are passed
to the answer model as conversation evidence; ES is not called. A question such as
“我上边问了几个问题？” can therefore use the total, even when it exceeds the context window.
For `DOCUMENT`, the backend selects files, searches their chunks, and sends only passages
accepted by the relevance check.
For `BOTH`, it selects related turns and searches ES using the router's standalone
`searchQuery`; the final answer model receives the **original user question**, selected
history, and accepted document passages. `GENERAL` sends neither history nor document text.
The answer model is instructed to use prior turns only for conversation facts or references,
not as proof that a document contains a claim. A failed routing or relevance check
returns `502` rather than guessing a route or accepting uncertain evidence.

`rag.chat.history-max-turns` in `application.yml` defaults to `10` and can be set through
`CHAT_HISTORY_MAX_TURNS`. `rag.chat.history-ttl` defaults to `24h` and can be set through
`CHAT_HISTORY_TTL`. The backend sends at most that many completed question/answer pairs to the
model; the paged archive retains all completed turns until expiry. A successful turn refreshes
the TTL; failed or interrupted turns are not saved as conversation messages.
Different conversation IDs have independent histories, even for the same user. The frontend
should provide New chat and conversation switching controls.

Only questions routed to `DOCUMENT` or `BOTH` search ES. If no passages are accepted, the
answer model receives no document context and the API returns `sources: []`. The model is instructed not to guess
private facts such as a person's resume when no document evidence exists. There are no
hard-coded responses for greetings and assistant-identity questions.
When ES returns candidates, the backend asks the selected chat model to judge each individual
passages that directly help answer the current question. Passages about a different subject are
excluded. Each judgment records an accepted/rejected decision and a short reason. Only selected
passages appear in `sources` and in the answer prompt. If none are
selected, the answer model receives no document text. This selection is model-based and can
still make mistakes; the frontend should render `sources` so users can inspect the evidence.
This extra model request can add latency and cost. A failed relevance check returns `502` rather
than treating the candidates as relevant.

For the frontend: the main question box sends `question` and the active `conversationId`;
the backend decides whether to search documents. Use `documentId` only for an explicit "ask this document" feature.
If the UI offers a separate general-chat action, send `mode: "CHAT"` explicitly. In document
mode, optionally provide a `topK` control. `temperature` and `maxTokens` apply to both modes. Disable Send while
the request is running. On `200`, display `answer`; in document mode render each `sources[]` item
with its file name, available page/section, and passage text.

```http
POST /api/chat/ask
Authorization: Bearer <accessToken>
Content-Type: application/json

{"question":"What is the access policy?","conversationId":"df6a1786-54c1-4dd5-849e-5c362997a26e","mode":"DOCUMENT","documentId":"company-handbook","topK":5,"temperature":0.2,"maxTokens":1024}
```

| Field | Required | Default | Constraint |
| --- | --- | --- | --- |
| `question` | Yes | — | Non-blank, at most 2,000 characters after trimming. |
| `conversationId` | For the conversation UI | Legacy implicit per-user history if omitted | UUID returned by the new-conversation or list endpoint. Must belong to the authenticated user. Pass the same ID for every turn of that conversation. |
| `mode` | No | `DOCUMENT` | `DOCUMENT` allows automatic routing to document search, history, both, or general chat. `CHAT` prevents document search but still allows history. |
| `documentId` | No | All documents | Exact document ID; at most 200 characters. Used only in `DOCUMENT` mode. |
| `topK` | No | `5` | Number of passages in `DOCUMENT` mode, `1`–`10`. |
| `temperature` | No | `0.2` | Sampling value, `0`–`2`. |
| `maxTokens` | No | `1024` | Maximum answer tokens, `1`–`4096`. |

Status: `200 OK`. In `CHAT` mode, `sources` is `[]`. In `DOCUMENT` mode, `sources` contains the
accepted document passages in citation order. The model is instructed to use relevant document
facts first, cite their source numbers, and distinguish any general-knowledge answer from document
content. The model may decide the retrieved candidates do not answer the question; do not present
every returned candidate as an actually cited source. The model receives
at most the first 1,500 characters of each passage; the response contains each full passage.
For retrieval, only the first 500 characters of `question` are used, while the model receives
the full question. Use `fileName`, `documentId`, `chunkIndex`, and `sourceReferences` to render
source cards. Display `answer` as text; citation markers such as `[1]` refer to the corresponding
one-based position in `sources`. The backend asks the model to cite sources, but does not verify
that every claim has a citation. The API does not provide source-file download or page-opening
links, so show source locations as labels unless the frontend has its own viewer.

```json
{
  "answer": "The policy requires staff approval before access is granted. [1]",
  "sources": [
    {
      "documentId": "company-handbook", "ingestionId": "ing-1",
      "fileName": "handbook.pdf", "documentType": "PDF", "chunkIndex": 4,
      "content": "Staff need approval before access is granted.",
      "startOffset": 1200, "endOffset": 1244,
      "sourceReferences": [{"pageNumber":3,"slideNumber":null,"sheetName":null,
        "sectionTitle":null,"elementType":"PAGE","sourceUrl":null,
        "sourceTitle":null,"webPageNumber":null}],
      "score": 0.87
    }
  ]
}
```

When a document route finds no passages, the backend still calls the selected
answer model for a general response and returns `sources: []`. The prompt tells the model to
avoid claiming document support and to say when it does not know. Prompt instructions reduce
unsupported claims but cannot guarantee factual accuracy. Retrieval or provider errors return
an error rather than silently falling back. Handle failures as follows:

| Status | Meaning | Frontend action |
| --- | --- | --- |
| `400` | Missing or invalid question or parameter, or unknown/expired conversation ID. | Preserve the entered question and show `error`; refresh the conversation list if its ID is invalid. |
| `401` | Login session missing or expired. | Return to login. |
| `502` | Embedding, Elasticsearch retrieval, or chat provider failed. For chat provider HTTP errors, `error` includes the upstream status and a short reason: `400` invalid request or model capability, `401`/`403` token or permission, `404` account URL or model, `410` retired model, `429` rate limit or quota. | Keep the question and show `error`; offer Retry for transient failures. For `410`, direct the administrator to select an active model ID. |
| `503` | Neither `CHAT` nor `ANSWER` resolves to an enabled model, or the selected model credential cannot be decrypted. | Preserve the backend `error` message; tell the administrator to check the assignments, enabled connection, and server encryption key. |

Only Cloudflare
Workers AI, OpenAI, DeepSeek, and Alibaba Cloud Model Studio compatible chat endpoints are
currently supported by the answer client.

```javascript
const response = await fetch("/api/chat/ask", {
  method: "POST",
  headers: {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
  },
  body: JSON.stringify({ question, conversationId: activeConversationId, mode: "DOCUMENT", documentId: selectedDocumentId || null }),
});
const data = await response.json();
if (!response.ok) throw new Error(data.error ?? "Question could not be answered");
// Render data.answer as text and data.sources as source cards.
```

```shell
curl -X POST http://localhost:8080/api/chat/ask \
  -H 'Authorization: Bearer session-token' -H 'Content-Type: application/json' \
  -d '{"question":"What is the access policy?","conversationId":"df6a1786-54c1-4dd5-849e-5c362997a26e","mode":"DOCUMENT","topK":5}'
```

For ordinary chat, send `{"question":"你好","mode":"CHAT","conversationId":"<active-id>"}`. For a RAG question across
all documents, send `{"question":"请总结李二龙的简历","conversationId":"<active-id>"}`; no `documentId` is needed. Display
`answer` and any `sources` returned. A follow-up can still send only its latest question:
`{"question":"他做过什么？","conversationId":"<same-active-id>"}`. The backend resolves the reference using that conversation's recent turns.

### Stream the answer and show progress

`POST /api/chat/ask/stream` accepts the **same JSON request** and bearer token as
`POST /api/chat/ask`. It responds with `Content-Type: text/event-stream`. Use this endpoint
for the main conversation UI. The existing synchronous endpoint remains available for callers
that need one JSON response.

The stream sends named SSE events in execution order. `status` events describe completed
decisions or the step now running; document-only steps are omitted for other routes:

| Event | Example `data` JSON | Frontend behavior |
| --- | --- | --- |
| `status` | `{"step":"started","message":"Preparing question"}` | Show immediate progress and keep this event's `requestId` for diagnostics. |
| `status` | `{"step":"history_loaded","message":"Loaded conversation history","turnCount":2,"totalTurns":12}` | Show context-window count and total completed turns. |
| `status` | `{"step":"routing","message":"Choosing evidence sources"}` | Show that source selection is running. |
| `status` | `{"step":"route_selected","message":"Selected evidence sources","route":"BOTH","routingModelPurpose":"ROUTING","reason":"Needs prior subject and document facts","searchQuery":"李二龙有哪些项目？"}` | Show the route, router's reason, and actual retrieval query. |
| `status` | `{"step":"history_selected","message":"Selected relevant history","turnCount":1,"decisions":[{"question":"李二龙是谁？","answer":"李二龙是一名 Java 工程师。","accepted":true,"reason":"Resolves the subject"}]}` | Present only for `HISTORY` or `BOTH`; shows prior turns considered, a bounded answer excerpt, whether each turn was selected, and why. |
| `status` | `{"step":"document_filter","message":"Checked document titles","hint":"resume.pdf","matchCount":1,"applied":true}` | Present when the router identifies an explicit filename; shows whether ES was narrowed. `matchCount` is capped at 2 because only uniqueness matters. |
| `status` | `{"step":"document_profiles","message":"Found candidate documents","candidateCount":2,"candidates":[{"documentId":"resume","fileName":"resume.pdf","summary":"Java resume...","score":2.1}]}` | Show file-level matches from the summary index. Skipped when an exact document ID is already known. |
| `status` | `{"step":"document_selection","message":"Selected documents from summaries","selectedCount":1,"fallback":false,"decisions":[{"documentId":"resume","fileName":"resume.pdf","accepted":true,"reason":"Matches the requested person"}]}` | Show which files the routing model chose and why. `fallback: true` means the backend will search chunks across the index for older or unmatched files. |
| `status` | `{"step":"searching","message":"Searching documents","query":"李二龙做过什么？","documentScope":["resume"]}` | Show the actual chunk query and selected document IDs. An empty scope means the legacy fallback search. |
| `status` | `{"step":"retrieved","message":"Found document candidates","candidateCount":5,"candidates":[{"documentId":"resume","fileName":"resume.pdf","chunkIndex":2,"score":0.87,"contentPreview":"Worked on Java payment services..."}]}` | Show chunk candidates before relevance filtering. `contentPreview` is limited to 500 characters; `evidence_selected` later gives each candidate's decision and reason. |
| `status` | `{"step":"document_fallback","message":"No selected document passage answered the question; checking remaining chunks","reason":"Selected document summaries did not yield accepted evidence"}` | Present only when a scoped search found no accepted passage; the backend then checks the broader chunk index. |
| `status` | `{"step":"evidence_selected","message":"Selected document evidence","sourceCount":1,"decisions":[{"documentId":"resume","fileName":"resume.pdf","chunkIndex":2,"accepted":true,"reason":"Contains the requested experience"}]}` | Show each candidate's accepted/rejected decision and reason. |
| `sources` | `[{"documentId":"...","fileName":"resume.pdf", ...}]` | Render only accepted document passages; an empty array means no document evidence was used. |
| `status` | `{"step":"generating","message":"Generating answer","sourceCount":2}` | Switch the indicator to answer generation. |
| `token` | `{"text":"李二龙"}` | Append `text` to the visible answer as it arrives. Do not replace prior text. |
| `status` | `{"step":"answer_generated","message":"Answer generated","answerLength":128}` | Model generation finished; `durationMs` on this event measures generation time. |
| `done` | `{}` | Mark the turn complete and stop the loading indicator. |
| `error` | `{"message":"..."}` | Stop loading, preserve any partial answer, and offer Retry. This may occur after HTTP 200 because the stream has already started. |

Every `status` event also includes a `requestId`, `elapsedMs`, and `durationMs`. Use `requestId`
to find the corresponding saved trace. `durationMs` measures time since the preceding status
event, so it is a diagnostic approximation for the intervening operation; it is not a provider
billing metric. `done` means the answer and trace have both been saved.

After file selection, chunk retrieval runs ES keyword matching and, when `EMBEDDING` is configured,
also runs vector similarity search. It merges both ranked lists, removes duplicate chunks, and
returns a bounded set of candidates for relevance checking; the final `sources` list contains
at most `topK` accepted passages. Vector-only hits with ES score below `0.8` are discarded
for answer generation; this is a conservative heuristic on the index's default cosine score,
not a guarantee that higher-scoring passages answer the question. Keyword matching can find exact names even when semantic
similarity ranks them poorly. The keyword query is ES `match`, so it is tokenized full-text
matching, not a byte-for-byte exact filter. Without `EMBEDDING`, the keyword results are used
alone. Retrieval failures produce `error`; they are not treated as zero matches.
The `sources` event contains only passages accepted by the relevance check. A `GENERAL` or
`HISTORY` route skips all file-level and chunk-level retrieval statuses.

Because the endpoint is a **POST with bearer authorization**, use `fetch()` and parse its
response body as SSE. Browser `EventSource` cannot directly send this JSON POST request. Decode
chunks with a streaming `TextDecoder`, buffer incomplete lines/events across chunks, parse each
`data:` JSON value, and dispatch by the `event:` name. Cancel the reader when the user cancels
the turn. A new question starts a new request; completed turns are saved by the backend.

```javascript
const controller = new AbortController(); // Keep this for the Cancel button.
const response = await fetch("/api/chat/ask/stream", {
  method: "POST",
  headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
  body: JSON.stringify({ question, conversationId: activeConversationId }),
  signal: controller.signal,
});
if (!response.ok) throw new Error((await response.json()).error ?? "Chat failed");
const reader = response.body.getReader();
const decoder = new TextDecoder();
let pending = "";
while (true) {
  const { value, done } = await reader.read();
  if (done) break;
  pending += decoder.decode(value, { stream: true });
  const frames = pending.split(/\r?\n\r?\n/);
  pending = frames.pop();
  for (const frame of frames) {
    const event = frame.match(/^event: (.+)$/m)?.[1];
    const raw = frame.match(/^data: (.+)$/m)?.[1];
    if (!event || !raw) continue;
    const data = JSON.parse(raw);
    if (event === "status") showProgress(data.step, data.message);
    if (event === "sources") showCandidateSources(data);
    if (event === "token") appendAnswer(data.text);
    if (event === "error") throw new Error(data.message);
    if (event === "done") finishAnswer();
  }
}
// To cancel: controller.abort(). Handle AbortError without showing a failure toast.
```

```shell
curl -N -X POST http://localhost:8080/api/chat/ask/stream \
  -H 'Authorization: Bearer session-token' -H 'Content-Type: application/json' \
  -d '{"question":"请总结李二龙的简历","conversationId":"df6a1786-54c1-4dd5-849e-5c362997a26e"}'
```

## Document Passage Search API

### Where the frontend uses it

This endpoint searches the **content of indexed documents**. With an enabled `EMBEDDING` model,
it embeds the query and runs vector similarity search; otherwise it uses keyword matching.
It is useful in these frontend flows:

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

This endpoint returns evidence only. It does not generate an answer. A question-answering UI
should call `POST /api/chat/ask`; this endpoint can support a separate "find source passages"
view. There is currently no endpoint
that downloads an indexed source file or opens it at a page number, so the frontend should display
source locations as labels unless it has its own document viewer. A `sourceUrl` from a web import
can be offered as an external link.

### Request

When `EMBEDDING` is configured, this endpoint calls the configured model once for the query
and searches indexed chunk vectors with the same model identity. New document and web-page
ingestions call that model once per chunk and store each vector in Elasticsearch. A disabled or
unassigned `EMBEDDING` connection leaves the existing keyword behavior in place. Existing
documents indexed before this feature have no vectors: re-index them to include them in semantic
results. Changing the embedding model also requires re-indexing documents; vectors from a
different model are filtered out. The vector dimension is inferred from the first indexed
vector, so changing to a model with another dimension requires a new Elasticsearch index.

Results include complete chunk text and source references. The optional `documentId` filter is an exact
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

Results are ordered by Elasticsearch relevance score (vector similarity when embeddings are
configured, keyword relevance otherwise). Render each `items[]` entry as one result
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
| `502 Bad Gateway` | The embedding provider or Elasticsearch could not complete the search. | Show a temporary search failure and allow retry. Do not present it as zero matches. |

Examples of the common error response are `{"error":"query must not be blank"}` for `400`,
`{"error":"Authentication is required"}` for `401`, and
`{"error":"The search index could not be queried"}` for `502`.

When embedding generation or Elasticsearch vector search fails, the endpoint returns `502`
with `{"error":"..."}`. The frontend should offer retry and should not show the failure as an
empty result. Cloudflare Workers AI, OpenAI, and Alibaba Cloud Model Studio compatible
embedding endpoints are currently supported for inference.

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
