# RAG Lab API Reference

## Overview

RAG Lab provides authentication, health, document preview, durable indexing, and document metadata APIs.

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
| `409 Conflict` | The same logical document is already being indexed. |
| `502 Bad Gateway` | Elasticsearch failed while storing document chunks. |
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
    "displayName": "Administrator"
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
  "displayName": "Administrator"
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
