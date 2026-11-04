package com.example.raglab.application;

/** Browser-ready image captcha challenge. */
public record CaptchaChallenge(
        String captchaId,
        String imageDataUri,
        long expiresInSeconds) {
}
