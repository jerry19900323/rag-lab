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
