package com.example.paygenix.support;

import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.Map;

/** Uploads whose first bytes match their declared type (the storage checks them), padded to the size asked for. */
public final class TestFiles {

    private static final Map<String, byte[]> HEADS = Map.of(
            "image/jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE0),
            "image/png", bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A),
            "image/gif", bytes('G', 'I', 'F', '8', '9', 'a'),
            "image/webp", bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'),
            "video/mp4", bytes(0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'),
            "video/quicktime", bytes(0, 0, 0, 0x14, 'f', 't', 'y', 'p', 'q', 't', ' ', ' '),
            "video/webm", bytes(0x1A, 0x45, 0xDF, 0xA3));

    private TestFiles() {
    }

    public static MockMultipartFile file(String part, String contentType, int size) {
        return new MockMultipartFile(part, "upload", contentType, content(contentType, size));
    }

    /** Valid first bytes for known types; zeros otherwise (or when size is 0). */
    public static byte[] content(String contentType, int size) {
        byte[] head = contentType == null ? null : HEADS.get(contentType.split(";")[0].trim().toLowerCase());
        byte[] data = new byte[size];
        if (head != null && size > 0) System.arraycopy(head, 0, data, 0, Math.min(head.length, size));
        return data;
    }

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) b[i] = (byte) values[i];
        return Arrays.copyOf(b, b.length);
    }
}
