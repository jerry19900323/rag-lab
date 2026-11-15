package com.example.raglab.application;

/** Evidence needed to answer the current question. */
public enum ChatRoute {
    GENERAL, HISTORY, DOCUMENT, BOTH;

    public boolean usesHistory() { return this == HISTORY || this == BOTH; }
    public boolean usesDocuments() { return this == DOCUMENT || this == BOTH; }
}
