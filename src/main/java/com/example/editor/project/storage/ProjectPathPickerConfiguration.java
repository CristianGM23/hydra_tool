package com.example.editor.project.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectPathPicker;

/** Configura exactamente un selector de rutas según el perfil de ejecución. */
@Configuration(proxyBeanMethods = false)
public class ProjectPathPickerConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "dsl.projects.local", name = "native-path-picker-enabled", havingValue = "true", matchIfMissing = true)
    /** Descripción: Crea el selector Swing cuando el runtime local permite diálogos nativos. Entradas: properties: configuración local. Salida: selector nativo. */
    public ProjectPathPicker swingProjectPathPicker(LocalProjectProperties properties) {
        return new SwingProjectPathPicker(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "dsl.projects.local", name = "native-path-picker-enabled", havingValue = "false")
    /** Descripción: Crea el fallback headless cuando el selector nativo está desactivado. Entradas: no requiere parámetros. Salida: selector seguro que informa indisponibilidad. */
    public ProjectPathPicker headlessProjectPathPicker() {
        return new HeadlessProjectPathPicker();
    }
}
