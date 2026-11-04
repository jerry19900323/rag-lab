package com.example.raglab.application;

/** MySQL could not remove document metadata after indexed chunks were deleted. */
public class DocumentMetadataDeleteException extends RuntimeException {
    public DocumentMetadataDeleteException(Throwable cause) {
        super("The document metadata could not be deleted", cause);
    }
}
