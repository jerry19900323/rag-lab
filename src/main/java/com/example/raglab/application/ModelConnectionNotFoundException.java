package com.example.raglab.application;

public class ModelConnectionNotFoundException extends RuntimeException {
    public ModelConnectionNotFoundException(String id) {
        super("Model connection " + id + " was not found");
    }
}
