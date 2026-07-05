package com.enterprise.ticketmaster.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/** Self-contained TOTP (RFC 6238) implementation — no external library, no cost. Compatible with Google Authenticator, Authy, etc. */
@Service
public class TotpService {

    private static final int SECRET_BYTES = 20;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String buildOtpAuthUrl(String secretBase32, String username) {
        String issuer = "Ticketmaster";
        return "otpauth://totp/" + issuer + ":" + username
                + "?secret=" + secretBase32 + "&issuer=" + issuer + "&algorithm=SHA1&digits=6&period=30";
    }

    /** Tolerates +/- 1 time step (30s) of clock drift between server and phone. */
    public boolean verifyCode(String secretBase32, String code) {
        if (secretBase32 == null || code == null || !code.matches("\\d{6}")) return false;
        long currentStep = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (code.equals(generateCode(secretBase32, step))) return true;
        }
        return false;
    }

    private String generateCode(String secretBase32, long timeStep) {
        try {
            byte[] key = base32Decode(secretBase32);
            byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP code", e);
        }
    }

    private String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int bits = 0, value = 0;
        for (byte b : data) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                result.append(BASE32_ALPHABET.charAt((value >>> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) result.append(BASE32_ALPHABET.charAt((value << (5 - bits)) & 0x1F));
        return result.toString();
    }

    private byte[] base32Decode(String base32) {
        String clean = base32.trim().toUpperCase().replace("=", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int bits = 0, value = 0;
        for (char c : clean.toCharArray()) {
            int idx = BASE32_ALPHABET.indexOf(c);
            if (idx < 0) continue;
            value = (value << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                out.write((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}