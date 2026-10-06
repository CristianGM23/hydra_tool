package com.example.editor.architecture;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Congela explícitamente los contratos JSON canónicos revisados para detectar cambios no aprobados. */
class CanonicalSchemaBaselineTests {

    @Test
    void canonicalJsonSchemasMatchTheReviewedBaseline() throws Exception {
        Properties baseline = new Properties();
        try(InputStream input = Files.newInputStream(Path.of("config/hydra-contract-baseline.properties"))) {
            baseline.load(input);
        }
        List<Path> schemas;
        try(var stream = Files.walk(Path.of("src/main/resources/schemas"))) {
            schemas = stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".schema.json"))
                    .sorted().toList();
        }
        assertThat(schemas).hasSize(Integer.parseInt(baseline.getProperty("schema.count")));
        for(Path schema : schemas) {
            String key = "schema." + schema.toString().replace('\\', '/');
            assertThat(baseline.getProperty(key)).as("reviewed hash for " + schema).isNotBlank();
            assertThat(sha256(schema)).as("canonical schema changed without baseline review: " + schema)
                    .isEqualTo(baseline.getProperty(key));
        }
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try(InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while((read = input.read(buffer)) >= 0) if(read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
