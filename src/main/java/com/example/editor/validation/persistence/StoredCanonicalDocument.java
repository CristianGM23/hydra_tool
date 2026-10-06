package com.example.editor.validation.persistence;
import java.nio.file.Path;
public record StoredCanonicalDocument<T>(T document, String etag, Path path) { }
