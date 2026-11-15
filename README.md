# RAG Lab

RAG Lab is an independently deployable Java and Spring Boot backend service for preparing documents for retrieval-augmented generation. It parses text, PDF, Office, and HTML documents and produces deterministic chunks with traceable source locations through a structured HTTP API.

## Document Processing

```text
TXT, Markdown, PDF, DOCX, PPTX, XLSX, or HTML -> Validate -> Parse -> Normalize -> Chunk -> JSON response
```

### Parsing

- Accepts `.txt`, `.md`, `.markdown`, `.pdf`, `.docx`, `.pptx`, `.xlsx`, `.html`, and `.htm` files.
- Enforces valid UTF-8 input instead of silently replacing malformed characters.
- Extracts PDF text page by page and reports page numbers on matching chunks.
- Extracts Word headings, paragraphs, and tables while retaining section titles and element types.
- Extracts PowerPoint text and tables while retaining slide numbers.
- Extracts formatted Excel rows, formulas, and values while retaining worksheet names.
- Extracts HTML headings, text blocks, lists, and table rows while removing scripts and layout chrome.
- Rejects encrypted PDFs and explains when an image-only PDF requires OCR.
- Removes the optional UTF-8 byte order mark.
- Normalizes Windows and legacy Mac line endings to `LF`.
- Rejects empty documents, unsupported extensions, and uploads larger than 5 MB.
- Removes path information from uploaded filenames before returning metadata.

### Stable Chunking

- Produces the same chunks for identical content and settings.
- Supports configurable maximum chunk size and overlap.
- Prefers paragraph, line, sentence, and whitespace boundaries in that order.
- Falls back to a hard character boundary when no natural boundary is available.
- Returns a zero-based chunk index and exact half-open source offsets for every chunk.
- Keeps each chunk within the configured maximum character count.
- Attaches source references such as PDF page numbers and Word section titles to each chunk.

### Observable Chunk Results

The preview API returns document statistics and the complete ordered chunk list. Each chunk includes its content, character count, index, and exact source range, allowing API clients and development tools to inspect chunking behavior without coupling the backend to a user interface.

## API

### Index Document

```http
POST /api/documents/index
Content-Type: multipart/form-data
```

Form fields:

- `file`: a supported document to parse, chunk, and store.
- `documentId`: optional existing logical document ID. Omit it for a new document.
- `maxCharacters`: optional maximum chunk size; defaults to 800.
- `overlapCharacters`: optional overlap; defaults to 100.

Create a new logical document:

```shell
curl -X POST http://localhost:8080/api/documents/index \
  -F 'file=@notes.md'
```

Replace an existing logical document with a new ingestion generation:

```shell
curl -X POST http://localhost:8080/api/documents/index \
  -F 'file=@notes-v2.md' \
  -F 'documentId=the-id-returned-by-the-first-request'
```

The endpoint returns `201 Created` after every chunk has been committed. A failed Elasticsearch
write returns `502 Bad Gateway`, and the previous successful document generation is preserved.

### Document Metadata

```http
GET /api/documents?page=0&size=20
GET /api/documents/{documentId}
GET /api/documents/{documentId}/ingestions?page=0&size=20
```

These endpoints read document status and ingestion history from MySQL. Pages are zero-based and
may contain at most 100 records.

Administrators can remove an indexed document and its search chunks with
`DELETE /api/documents/{documentId}`. The same endpoint handles uploaded files and indexed web
pages; ingestion history is deleted with the document.

### Preview Document Chunks

```http
POST /api/documents/preview
Content-Type: multipart/form-data
```

Form fields:

- `file`: a supported text, text-layer PDF, modern Office, or HTML document.
- `maxCharacters`: maximum characters per chunk, from 100 to 10,000. The default is 800.
- `overlapCharacters`: overlap between adjacent chunks. The default is 100 and must be smaller than `maxCharacters`.

Example:

```shell
curl -X POST http://localhost:8080/api/documents/preview \
  -F 'file=@notes.md' \
  -F 'maxCharacters=800' \
  -F 'overlapCharacters=100'
```

Example response:

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
      "content": "# Example document...",
      "sourceReferences": [
        {"pageNumber": null, "sectionTitle": null, "elementType": "TEXT"}
      ]
    }
  ]
}
```

### Health Check

```http
GET /api/health
```

Elasticsearch readiness check:

```http
GET /api/health/elasticsearch
```

The readiness endpoint returns `200` with an `UP` status when Elasticsearch responds to a ping,
or `503` with a `DOWN` status and an error message when it is unavailable.

MySQL and Redis readiness check:

```http
GET /api/health/infrastructure
```

The application uses the `rag_lab` MySQL schema and Redis logical database `1` by default.
Provide credentials through `DB_PASSWORD` and, only when Redis authentication is enabled,
`REDIS_PASSWORD`. Flyway applies schema migrations during application startup.

### Authentication

Create an image captcha:

```http
GET /api/auth/captcha
```

The response contains a `captchaId`, a PNG `imageDataUri`, and its expiration time. Log in with:

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "your-password",
  "captchaId": "captcha-id",
  "captchaCode": "ABCD"
}
```

Set `ADMIN_PASSWORD` outside the repository to create the initial administrator on startup.
`ADMIN_USERNAME` defaults to `admin`, while `CAPTCHA_TTL` and `AUTH_SESSION_TTL` default to
two minutes and eight hours. Captchas are one-time values and login sessions are stored in Redis.

Use the returned access token for protected APIs:

```http
Authorization: Bearer session-token
```

The document APIs and all future non-public `/api` routes require authentication. Health checks,
captcha creation, and login remain public. The authenticated user and logout endpoints are:

```http
GET /api/auth/me
Authorization: Bearer session-token

POST /api/auth/logout
Authorization: Bearer session-token
```

Public HTML pages can be previewed or indexed through `POST /api/web-pages/preview` and
`POST /api/web-pages/index`. Static same-origin pagination is optional and disabled by default.
See the [API Reference](docs/decisions/api-reference.md) for the complete request and response schema.

Search indexed chunks by keyword, optionally within one document:

```shell
curl -G http://localhost:8080/api/search \
  -H 'Authorization: Bearer session-token' \
  --data-urlencode 'query=access policy' \
  --data-urlencode 'documentId=company-handbook'
```

Results include relevance scores, chunk text, offsets, and source references for citations.

### Model Settings

Administrators can configure provider connections and select models for answer generation,
embeddings, and routing through `/api/admin/model-*`. The initial presets are OpenAI, DeepSeek,
Alibaba Cloud Model Studio (international), Claude, Gemini, and local Ollama. Model dropdown
options come from the providers' live model-list APIs. Cloudflare Workers AI is also supported;
its connection requires an Account ID and API token. Set `MODEL_CONFIG_ENCRYPTION_KEY` to a
base64-encoded 32-byte key before saving provider API keys; `application.yml` reads this
environment variable through `rag.model-config.encryption-key`. In IntelliJ IDEA, add
`MODEL_CONFIG_ENCRYPTION_KEY=<generated-value>` to the application's Run Configuration
environment variables. Keep this key across restarts and do not commit its value.
See the [API Reference](docs/decisions/api-reference.md#administrator-model-settings-api) for
frontend page behavior and endpoint schemas. When `EMBEDDING` is configured, document and web
page indexing store chunk vectors, and `/api/search` uses vector similarity; otherwise search
continues to use keywords. Existing content must be re-indexed to gain vectors. Single-turn
Document and web-page ingestion also creates one short file-level description. A configured
`DOCUMENT_SUMMARY` model is preferred, followed by `ANSWER` or `CHAT`; without any chat model,
the backend stores a representative extract. The description is saved in MySQL and as one
file-level ES record. Document questions first search these descriptions, use the routing model
to choose relevant files, then search chunks only within those files. Existing documents must
be uploaded again with the same `documentId` to gain descriptions; the backend does not delete
them automatically. Single-turn
chat is available through `POST /api/chat/ask` when `CHAT` or `ANSWER` is configured. Omit
`mode` to let the backend route among conversation history, document search, both, and general
chat; send `mode: "CHAT"` only when document search must be skipped. The configured `ROUTING`
model handles this decision when present; otherwise the selected answer model does. Only
document routes search the index. `ANSWER` and `CHAT` still fall back to each other when one
is unavailable.
For interactive chat, `POST /api/chat/ask/stream` emits SSE progress, selected sources, and
model tokens; the original `/api/chat/ask` remains a synchronous JSON endpoint. RAG retrieval
merges keyword and vector results when an embedding model is configured.
The frontend starts a conversation with `POST /api/chat/conversations`, then sends its
`conversationId` with each `/api/chat/ask` or `/api/chat/ask/stream` request. It can list
conversations, read retained turns, and delete a conversation through the chat API. The backend
uses the latest 10 completed turns per conversation as model context by default. Configure
`CHAT_HISTORY_MAX_TURNS` and `CHAT_HISTORY_TTL` in the backend environment; the frontend does
not send previous messages. Requests without `conversationId` retain the legacy implicit
per-user history for compatibility.
The backend also keeps a paged archive of all completed turns within the conversation's TTL
and the latest 100 execution traces. `GET /api/chat/conversations/{id}/messages/page` restores
full in-lifetime history; `GET /api/chat/conversations/{id}/traces` shows routing reasons,
retrieval decisions, and step timings for troubleshooting.
The add-model form selects one usage type and one model. Each usage type has one saved model;
the runtime resolver looks up that type and skips missing or disabled connections.
Administrators can remove a saved model connection with
`DELETE /api/admin/model-connections/{id}`; its purpose assignments are cleared automatically.
The model connection list and save responses include the API key so the existing Edit form can
prefill its masked password input.

On startup, the service idempotently creates the versioned `rag_chunks_v1` index and attaches
the stable `rag_chunks` alias. Set `ES_INITIALIZE_INDEX=false` to disable this initialization.

The default Elasticsearch connection uses HTTPS and trusts the local Elasticsearch CA at
`/opt/elastic/elasticsearch-9.5.5/config/certs/http_ca.crt`. Override it with `ES_CA_CERT`
using a Spring resource location such as `file:/another/path/http_ca.crt`.

## Project Structure

```text
src/main/java/com/example/raglab/
├── api/                     # Controllers and HTTP error responses
├── application/             # Services and replaceable processing contracts
├── domain/                  # Domain records and validated policies
└── infrastructure/document  # Document parser contracts and implementations

src/main/resources/
└── application.yml          # Service configuration and upload limits
```

## Run

```shell
mvn spring-boot:run
```

## Test

```shell
mvn test
```

The test suite covers UTF-8 normalization, PDF pages, Word structure and tables, file-type rejection, natural-boundary chunking, deterministic overlap, API output, invalid settings, and application startup.

## Technology Stack

- Java 17+
- Spring Boot
- Maven

## Documentation

- [API Reference](docs/decisions/api-reference.md)
