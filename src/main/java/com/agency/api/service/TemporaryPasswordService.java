package com.agency.api.service;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;

@Service
public class TemporaryPasswordService {
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%*-_";
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        var value = new StringBuilder();
        value.append(pick(LETTERS)).append(pick(DIGITS)).append(pick(SYMBOLS));
        String alphabet = LETTERS + DIGITS + SYMBOLS;
        while (value.length() < 20) value.append(pick(alphabet));
        char[] chars = value.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) { int j = random.nextInt(i + 1); char c = chars[i]; chars[i] = chars[j]; chars[j] = c; }
        return new String(chars);
    }

    private char pick(String alphabet) { return alphabet.charAt(random.nextInt(alphabet.length())); }
}
