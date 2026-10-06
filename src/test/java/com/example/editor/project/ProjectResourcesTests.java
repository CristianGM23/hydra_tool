package com.example.editor.project;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
class ProjectResourcesTests {
    @Test void publishesProjectSchemaAndExample() {
        assertThat(getClass().getResource("/schemas/project/modeling-project-1.0.schema.json")).isNotNull();
        assertThat(getClass().getResource("/examples/project/railway-modeling-project.json")).isNotNull();
    }

    @Test void enablesTheDesktopPathPickerInSpringBoot() throws IOException {
        Properties properties=new Properties();
        try(var input=getClass().getResourceAsStream("/application.properties")) {
            assertThat(input).isNotNull();
            properties.load(input);
        }
        assertThat(properties.getProperty("spring.main.headless")).isEqualTo("false");
        assertThat(properties.getProperty("server.port")).isEqualTo("8081");
    }
}
