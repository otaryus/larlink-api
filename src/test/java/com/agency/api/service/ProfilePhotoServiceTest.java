package com.agency.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfilePhotoServiceTest {

    private final ProfilePhotoService service = new ProfilePhotoService();

    @Test
    void acceptsAValidPngSignature() {
        byte[] bytes = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1};

        var photo = service.readValidated(new MockMultipartFile("photo", "profile.png", "image/png", bytes));

        assertEquals("image/png", photo.contentType());
        assertArrayEquals(bytes, photo.bytes());
    }

    @Test
    void rejectsContentThatDoesNotMatchTheDeclaredMimeType() {
        var file = new MockMultipartFile("photo", "profile.png", "image/png", "not-an-image".getBytes());

        var error = assertThrows(IllegalArgumentException.class, () -> service.readValidated(file));

        assertEquals("O conteúdo do ficheiro não corresponde a uma imagem válida.", error.getMessage());
    }

    @Test
    void rejectsFilesLargerThanThreeMegabytes() {
        var file = new MockMultipartFile("photo", "large.jpg", "image/jpeg", new byte[3 * 1024 * 1024 + 1]);

        var error = assertThrows(IllegalArgumentException.class, () -> service.readValidated(file));

        assertEquals("A fotografia deve ter no máximo 3 MB.", error.getMessage());
    }
}
