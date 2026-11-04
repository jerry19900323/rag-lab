package com.example.raglab.application;

/** Application scenarios that can select a configured model. */
public enum ModelPurpose {
    ANSWER("Document answers"),
    CHAT("General chat"),
    ROUTING("Request routing"),
    QUERY_REWRITE("Query rewriting"),
    DOCUMENT_SUMMARY("Document summarization"),
    INFORMATION_EXTRACTION("Information extraction"),
    TRANSLATION("Translation"),
    EMBEDDING("Text embeddings"),
    MULTIMODAL_EMBEDDING("Multimodal embeddings"),
    RERANK("Search reranking"),
    VISION("Image understanding"),
    OCR("Scanned document recognition"),
    SPEECH_TO_TEXT("Speech transcription"),
    TEXT_TO_SPEECH("Speech synthesis"),
    IMAGE_GENERATION("Image generation"),
    VIDEO_GENERATION("Video generation");

    private final String displayName;
    ModelPurpose(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

}
