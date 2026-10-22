package com.example.editor.project.recovery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Archivos del fixture Sirius y utilidades de comparación del contenido de los almacenes canónicos, compartidos por las pruebas de importación. */
final class ImportFixtureArchives {
    static final String FIXTURE = "/fixtures/sirius-complete/";
    static final String ECORE = "com.hydra.sirius.fixture.metamodel/model/hydratest.ecore";
    static final List<String> FULL = List.of(
            "com.hydra.sirius.fixture.design/.project", "com.hydra.sirius.fixture.design/build.properties",
            "com.hydra.sirius.fixture.design/description/hydratest.odesign", "com.hydra.sirius.fixture.design/icons/datastore.svg",
            "com.hydra.sirius.fixture.design/META-INF/MANIFEST.MF",
            "com.hydra.sirius.fixture.design/src/com/hydra/sirius/fixture/design/Services.java",
            "com.hydra.sirius.fixture.metamodel/.project", "com.hydra.sirius.fixture.metamodel/build.properties",
            "com.hydra.sirius.fixture.metamodel/META-INF/MANIFEST.MF", ECORE,
            "com.hydra.sirius.fixture.metamodel/model/hydratest.genmodel",
            "com.hydra.sirius.fixture.sample/.project", "com.hydra.sirius.fixture.sample/representations.aird",
            "com.hydra.sirius.fixture.sample/model/invalid.hydraflow", "com.hydra.sirius.fixture.sample/model/valid.hydraflow",
            "com.hydra.sirius.fixture.validation/.project", "com.hydra.sirius.fixture.validation/build.properties",
            "com.hydra.sirius.fixture.validation/plugin.xml", "com.hydra.sirius.fixture.validation/META-INF/MANIFEST.MF",
            "com.hydra.sirius.fixture.validation/validation/hydratest.evl");
    static final List<String> METAMODEL_ONLY = List.of(ECORE);

    private ImportFixtureArchives() { }

    static byte[] zip(List<String> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String relative : files) {
                try (InputStream input = ImportFixtureArchives.class.getResourceAsStream(FIXTURE + relative)) {
                    if (input == null) throw new IOException("Missing fixture resource: " + relative);
                    zip.putNextEntry(new ZipEntry(relative));
                    zip.write(input.readAllBytes());
                    zip.closeEntry();
                }
            }
        }
        return bytes.toByteArray();
    }

    /** SHA-256 de cada fichero de los almacenes canónicos, sin la auditoría (solo anexado), el registro de proyectos ni el propio diario. */
    static Map<String, String> canonicalState(Path root) throws IOException {
        Map<String, String> state = new TreeMap<>();
        if (!Files.isDirectory(root)) return state;
        try (var walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.contains("audit") || relative.startsWith("projects/") || relative.startsWith("external-import-journal/")) continue;
                try { state.put(relative, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))); }
                catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
            }
        }
        return state;
    }

    static String describe(Map<String, String> before, Map<String, String> after) {
        java.util.List<String> added = new java.util.ArrayList<>(after.keySet()); added.removeAll(before.keySet());
        java.util.List<String> removed = new java.util.ArrayList<>(before.keySet()); removed.removeAll(after.keySet());
        java.util.List<String> changed = before.keySet().stream().filter(after::containsKey).filter(key -> !before.get(key).equals(after.get(key))).toList();
        return "added=" + added + " removed=" + removed + " changed=" + changed;
    }

    /** Diarios de importación que quedan en disco. */
    static List<String> journals(Path root) throws IOException {
        Path directory = root.resolve("external-import-journal");
        if (!Files.isDirectory(directory)) return List.of();
        try (var list = Files.list(directory)) {
            return list.map(path -> path.getFileName().toString()).filter(name -> name.startsWith("imp-") && name.endsWith(".json")).sorted().toList();
        }
    }

    static String text(Path file) throws IOException { return Files.readString(file, StandardCharsets.UTF_8); }
}
