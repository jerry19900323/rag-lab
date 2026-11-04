package com.example.raglab.application;

/** The model connection and its assignments could not be removed. */
public class ModelConnectionDeleteException extends RuntimeException {
    public ModelConnectionDeleteException(Throwable cause) {
        super("The model connection could not be deleted", cause);
    }
}
