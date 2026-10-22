# RAG Lab API Reference

## Overview

RAG Lab exposes HTTP endpoints for service health checks and document processing. All endpoints are served from the application host.

Local base URL:

```text
http://localhost:8080
```

Authentication is not currently required.

## Conventions

### Content Types

- JSON responses use `application/json`.
- File uploads use `multipart/form-data`.
- Uploaded document content must be valid UTF-8.

### Character Offsets

Chunk offsets use zero-based, half-open ranges:

```text
[startOffset, endOffset)
```

The character at `startOffset` is included, while the character at `endOffset` is excluded. Therefore:

```text
characterCount = endOffset - startOffset
```

Offsets refer to normalized document content after removal of an optional UTF-8 byte order mark and conversion of all line endings to `LF`.

### Error Response

Input validation failures return HTTP `400 Bad Request` with the following body:

```json
{
  "error": "A human-readable description of the validation failure"
}
```

## Health Check

Reports whether the application is available to serve requests.

### Request

```http
GET /api/health
```

No request parameters are required.

### Successful Response

Status: `200 OK`

```json
{
  "status": "UP"
}
```

### Response Fields

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | string | Yes | Application availability status. The current value is `UP`. |

### Example

```shell
curl http://localhost:8080/api/health
```

## Preview Document Chunks

Validates, parses, normalizes, and chunks one TXT or Markdown document. The operation is read-only and does not persist either the document or its chunks.

### Request

```http
POST /api/documents/preview
Content-Type: multipart/form-data
```

### Form Fields

| Field | Type | Required | Default | Constraints | Description |
| --- | --- | --- | --- | --- | --- |
| `file` | file | Yes | None | Non-empty UTF-8 `.txt`, `.md`, or `.markdown` file; maximum request size is 5 MB. | Document to parse and chunk. |
| `maxCharacters` | integer | No | `800` | From `100` through `10000`. | Maximum number of UTF-16 characters in one chunk. |
| `overlapCharacters` | integer | No | `100` | Must be non-negative and smaller than `maxCharacters`. | Requested overlap between adjacent chunks. Natural-boundary whitespace may reduce the effective overlap. |

### Example Request

```shell
curl -X POST http://localhost:8080/api/documents/preview \
  -F 'file=@notes.md' \
  -F 'maxCharacters=800' \
  -F 'overlapCharacters=100'
```

### Successful Response

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
      "content": "# Example document\n\nDocument content..."
    },
    {
      "index": 1,
      "startOffset": 656,
      "endOffset": 1240,
      "characterCount": 584,
      "content": "Overlapping document content..."
    }
  ]
}
```

### Response Fields

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `fileName` | string | Yes | Sanitized source filename without client-side path information. |
| `documentType` | string | Yes | Parsed document type: `TEXT` or `MARKDOWN`. |
| `characterCount` | integer | Yes | Number of characters in the normalized document. |
| `chunkCount` | integer | Yes | Number of chunks in the response. |
| `maxCharacters` | integer | Yes | Validated maximum chunk size used by the operation. |
| `overlapCharacters` | integer | Yes | Validated requested overlap used by the operation. |
| `chunks` | array | Yes | Ordered list of generated chunks. |
| `chunks[].index` | integer | Yes | Zero-based position of the chunk in the result. |
| `chunks[].startOffset` | integer | Yes | Inclusive start offset in normalized document content. |
| `chunks[].endOffset` | integer | Yes | Exclusive end offset in normalized document content. |
| `chunks[].characterCount` | integer | Yes | Number of characters in the chunk. |
| `chunks[].content` | string | Yes | Exact normalized document text within the reported offsets. |

### Chunking Behavior

The chunker is deterministic: identical normalized content and identical parameters produce identical chunks. When a document exceeds `maxCharacters`, the chunker searches the latter half of the available range and selects the latest suitable boundary using this priority:

1. Paragraph boundary.
2. Line boundary.
3. Sentence-ending punctuation.
4. Whitespace.
5. Hard character limit.

Leading and trailing whitespace at chunk boundaries is excluded. This keeps chunks readable while retaining exact offsets into normalized content.

### Error Responses

Status: `400 Bad Request`

Possible validation errors include:

| Condition | Example `error` value |
| --- | --- |
| Missing filename | `The uploaded document must have a file name` |
| Unsupported extension | `Only .txt, .md, and .markdown files are supported` |
| Empty file | `The uploaded document is empty` |
| Whitespace-only file | `The uploaded document does not contain readable text` |
| Invalid UTF-8 | `The document must use valid UTF-8 encoding` |
| Invalid maximum size | `maxCharacters must be between 100 and 10000` |
| Invalid overlap | `overlapCharacters must be non-negative and smaller than maxCharacters` |

Uploads that exceed the configured multipart limit are rejected by the web server before document processing begins.

## Change Policy

Every new or modified endpoint must update this document in the same change as the implementation. Each endpoint entry must include:

- Purpose and persistence behavior.
- HTTP method and path.
- Authentication requirements.
- Request content type and parameters.
- Parameter defaults and validation constraints.
- Success status and complete response schema.
- Error statuses and response examples.
- At least one executable request example.
