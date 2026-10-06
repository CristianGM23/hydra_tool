package com.example.editor.project.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Contrato de renderizado seguro de imágenes del proyecto. */
class ProjectVisualAssetPolicyTests {
    private final ProjectVisualAssetPolicy policy = new ProjectVisualAssetPolicy();

    @Test void acceptsPortableImageFamiliesByContentSignature() {
        assertEquals("image/png", policy.validate("assets/notation/a.png", new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a}).mediaType());
        assertEquals("image/jpeg", policy.validate("assets/notation/a.jpg", new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,0}).mediaType());
        assertEquals("image/gif", policy.validate("assets/notation/a.gif", "GIF89a".getBytes(StandardCharsets.US_ASCII)).mediaType());
        assertEquals("image/webp", policy.validate("assets/notation/a.webp", new byte[]{'R','I','F','F',0,0,0,0,'W','E','B','P'}).mediaType());
        assertEquals("image/svg+xml", policy.validate("assets/notation/a.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"1\" height=\"1\"/></svg>".getBytes(StandardCharsets.UTF_8)).mediaType());
    }

    @Test void rejectsActiveOrExternallyReferencedSvg() {
        assertThrows(IllegalArgumentException.class, () -> policy.validate("bad.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("external.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"><image href=\"https://example.test/a.png\"/></svg>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("event.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8)));
    }
}
