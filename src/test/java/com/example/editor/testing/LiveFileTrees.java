package com.example.editor.testing;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks a directory tree that a live Spring context may still be writing to. The asynchronous audit writer of such a context creates
 * and removes temporary files (JsonlAuditLogStore rewrites a partition through "<day>.jsonl.rewrite-*.tmp"), and Files.walk fails with
 * NoSuchFileException when an entry vanishes between being listed and being read. An entry that vanishes during the walk is skipped;
 * every other I/O error is still thrown.
 */
public final class LiveFileTrees {
    private LiveFileTrees() { }

    /** Regular files under the root, in path order; empty when the root does not exist. */
    public static List<Path> regularFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(root)) return files;
        Files.walkFileTree(root, new SkippingVanishedEntries() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (Files.isRegularFile(file)) files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(null);
        return files;
    }

    /** Deletes the root and everything under it; entries that are already gone are not an error. */
    public static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SkippingVanishedEntries() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                super.postVisitDirectory(directory, failure);
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static class SkippingVanishedEntries extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
            if (failure instanceof NoSuchFileException) return FileVisitResult.CONTINUE;
            throw failure;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
            if (failure != null && !(failure instanceof NoSuchFileException)) throw failure;
            return FileVisitResult.CONTINUE;
        }
    }
}
