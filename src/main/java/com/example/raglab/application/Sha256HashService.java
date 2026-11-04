package com.example.raglab.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/** Produces stable lowercase SHA-256 hashes for documents and normalized chunk text. */
@Component
public class Sha256HashService {

    public String hashBytes(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public String hashText(String content) {
        return hashBytes(content.getBytes(StandardCharsets.UTF_8));
    }
}
