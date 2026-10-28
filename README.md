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
