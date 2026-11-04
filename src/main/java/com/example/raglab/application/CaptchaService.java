package com.example.raglab.application;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Generates one-time image captchas and stores only their hashes in Redis. */
@Service
public class CaptchaService {

    private static final String CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String KEY_PREFIX = "rag:auth:captcha:";
    private static final int WIDTH = 160;
    private static final int HEIGHT = 56;

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public CaptchaService(
            StringRedisTemplate redisTemplate,
            @Value("${rag.auth.captcha-ttl:2m}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public CaptchaChallenge createChallenge() {
        String captchaId = UUID.randomUUID().toString();
        String code = randomCode(4);
        redisTemplate.opsForValue().set(KEY_PREFIX + captchaId, hash(code), ttl);
        return new CaptchaChallenge(captchaId, renderDataUri(code), ttl.toSeconds());
    }

    public void validateAndConsume(String captchaId, String submittedCode) {
        if (captchaId == null || captchaId.isBlank() || submittedCode == null || submittedCode.isBlank()) {
            throw new InvalidCaptchaException();
        }
        String expectedHash = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + captchaId);
        byte[] expected = expectedHash == null ? new byte[0] : expectedHash.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hash(submittedCode.trim().toUpperCase(java.util.Locale.ROOT))
                .getBytes(StandardCharsets.US_ASCII);
        if (expected.length == 0 || !MessageDigest.isEqual(expected, actual)) {
            throw new InvalidCaptchaException();
        }
    }

    private String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String renderDataUri(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 247, 250));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            for (int index = 0; index < 8; index++) {
                graphics.setColor(randomColor(100, 210));
                graphics.drawLine(
                        random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            for (int index = 0; index < code.length(); index++) {
                graphics.setColor(randomColor(30, 130));
                graphics.drawString(String.valueOf(code.charAt(index)), 18 + index * 34, 40 + random.nextInt(6));
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not render captcha image", exception);
        }
    }

    private Color randomColor(int minimum, int maximum) {
        int range = maximum - minimum;
        return new Color(
                minimum + random.nextInt(range),
                minimum + random.nextInt(range),
                minimum + random.nextInt(range));
    }
}
