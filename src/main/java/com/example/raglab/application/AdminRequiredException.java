package com.example.raglab.application;

/** Raised when an authenticated non-administrator accesses model settings. */
public class AdminRequiredException extends RuntimeException {
    public AdminRequiredException() {
        super("Administrator access is required");
    }
}
