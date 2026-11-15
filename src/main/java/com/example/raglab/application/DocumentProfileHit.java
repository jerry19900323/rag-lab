package com.example.raglab.application;

/** One file-level search candidate before chunk retrieval. */
public record DocumentProfileHit(String documentId, String fileName, String summary, double score) {}
