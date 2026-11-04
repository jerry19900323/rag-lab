package com.example.raglab.application;

/** Indicates a missing, expired, already-used, or incorrect image captcha. */
public class InvalidCaptchaException extends RuntimeException {

    public InvalidCaptchaException() {
        super("The captcha is invalid or has expired");
    }
}
