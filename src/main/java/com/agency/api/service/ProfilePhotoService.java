package com.agency.api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

@Service
public class ProfilePhotoService {
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public record Photo(byte[] bytes, String contentType) {}

    public Photo readValidated(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Carregue uma fotografia de perfil.");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("A fotografia deve ter no máximo 3 MB.");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Use uma imagem JPG, PNG ou WEBP.");
        try {
            byte[] bytes = file.getBytes();
            boolean valid = switch (type) {
                case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
                case "image/png" -> bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
                case "image/webp" -> bytes.length >= 12 && new String(bytes, 0, 4).equals("RIFF") && new String(bytes, 8, 4).equals("WEBP");
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("O conteúdo do ficheiro não corresponde a uma imagem válida.");
            return new Photo(bytes, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível ler a fotografia carregada.");
        }
    }
}
