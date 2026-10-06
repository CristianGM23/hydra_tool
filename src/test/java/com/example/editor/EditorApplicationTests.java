package com.example.editor;

import java.nio.file.Path;
import java.util.HashSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteImportProperties;
import com.example.editor.provenance.HydraDistributionProvenanceProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el contexto principal de Spring Boot y las propiedades
 * de almacenamiento se cargan correctamente.
 */
@SpringBootTest(properties = {"dsl.storage.create-directories=false", "hydra.instance-lock.enabled=false"})
class EditorApplicationTests {

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private UpdateSiteImportProperties updateSiteImportProperties;

    @Autowired
    private HydraDistributionProvenanceProperties distributionProvenanceProperties;

    /**
     * Descripción:
     * Comprueba que Spring Boot crea correctamente el contexto y las propiedades.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando storageProperties existe.
     */
    @Test
    void springContextLoads() {
        assertThat(storageProperties).isNotNull();
    }

    /**
     * Descripción:
     * Comprueba que application.properties se vincula correctamente con
     * StorageProperties.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando todas las rutas coinciden.
     */
    @Test
    void storagePropertiesAreBound() {
        assertThat(storageProperties.metamodels().normalize()).isEqualTo(Path.of("data/metamodels"));
        assertThat(storageProperties.models().normalize()).isEqualTo(Path.of("data/models"));
        assertThat(storageProperties.notations().normalize()).isEqualTo(Path.of("data/notations"));
        assertThat(storageProperties.validations().normalize()).isEqualTo(Path.of("data/validations"));
        assertThat(storageProperties.quickFixes().normalize()).isEqualTo(Path.of("data/quickfixes"));
        assertThat(storageProperties.generators().normalize()).isEqualTo(Path.of("data/generators"));
        assertThat(storageProperties.assets().normalize()).isEqualTo(Path.of("data/assets"));
        assertThat(storageProperties.exports().normalize()).isEqualTo(Path.of("data/exports"));
        assertThat(updateSiteImportProperties.isRemoteEnabled()).isFalse();
        assertThat(updateSiteImportProperties.getRemoteAllowedHosts()).isEmpty();
        assertThat(distributionProvenanceProperties.storageRootPath().endsWith(Path.of("data/provenance/distributions"))).isTrue();
        assertThat(distributionProvenanceProperties.getMaximumReports()).isEqualTo(200);
    }

    /**
     * Descripción:
     * Comprueba que no existen dos tipos de almacenamiento configurados con
     * la misma ruta.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando todas las rutas son únicas.
     */
    @Test
    void configuredDirectoriesAreUnique() {
        HashSet<Path> uniqueDirectories = new HashSet<>(storageProperties.allDirectories());
        assertThat(uniqueDirectories).hasSameSizeAs(storageProperties.allDirectories());
    }
    /** Comprueba que el modo local/escritorio conserva AWT para poder abrir el selector nativo desde localhost. */
    @Test
    void desktopLocalStartupKeepsNativeDirectoryPickerAvailable() {
        assertThat(EditorApplication.resolveHeadlessMode(new String[0])).isFalse();
        assertThat(EditorApplication.resolveHeadlessMode(new String[] {"--spring.profiles.active=desktop-local"})).isFalse();
        assertThat(EditorApplication.resolveHeadlessMode(new String[] {"--spring.main.headless=false"})).isFalse();
    }

    /** Comprueba que los perfiles de servidor siguen siendo headless y no exponen diálogos nativos a clientes remotos. */
    @Test
    void serverStartupRemainsHeadless() {
        assertThat(EditorApplication.resolveHeadlessMode(new String[] {"--spring.profiles.active=production"})).isTrue();
        assertThat(EditorApplication.resolveHeadlessMode(new String[] {"--spring.profiles.active=headless-server"})).isTrue();
    }

}