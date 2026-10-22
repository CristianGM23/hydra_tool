package com.example.editor.project.recovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.mockito.Mockito;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.editor.EditorApplication;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.metamodel.importing.MetamodelImportService;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import com.example.editor.project.ProjectApplicationService;

/**
 * Proceso hijo de las pruebas de recuperación: arranca HYDRA completo sobre directorios dados y, si se le pide, se mata
 * (<code>Runtime.halt</code>, sin hooks ni cierre ordenado) en un punto concreto del protocolo. Así la recuperación se
 * demuestra con una JVM nueva que no comparte ningún estado en memoria con la anterior.
 *
 * <p>Argumentos: {@code <modo> <raiz> <espacioDeTrabajo> <archivoZip|-> <nombreProyecto>}; modos {@code import} y {@code recover}.
 * El punto de fallo se indica con {@code -Dhydra.crash=…}: AFTER_MATERIALIZATION, BEFORE_METAMODEL_CREATED,
 * BEFORE_APPLIED_RECORDED o DURING_COMPENSATION.
 */
public final class ExternalImportCrashChild {
    private ExternalImportCrashChild() { }

    static void halt() { Runtime.getRuntime().halt(137); }

    @Configuration
    static class CrashConfiguration {
        @Bean
        static BeanPostProcessor crashInjector() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    String point = System.getProperty("hydra.crash", "");
                    if (bean instanceof ProjectApplicationService && point.equals("AFTER_MATERIALIZATION")) {
                        ProjectApplicationService spy = Mockito.spy((ProjectApplicationService) bean);
                        doAnswer(invocation -> { halt(); return null; }).when(spy)
                                .upload(eq("recovery"), eq("external-project-materialization.json"), any(byte[].class));
                        return spy;
                    }
                    if (bean instanceof MetamodelImportService && point.equals("BEFORE_METAMODEL_CREATED")) {
                        MetamodelImportService spy = Mockito.spy((MetamodelImportService) bean);
                        doAnswer(invocation -> { halt(); return null; }).when(spy).importPrepared(any(), anyBoolean());
                        return spy;
                    }
                    if (bean instanceof ExternalImportJournalStore && point.equals("BEFORE_APPLIED_RECORDED")) {
                        ExternalImportJournalStore spy = Mockito.spy((ExternalImportJournalStore) bean);
                        doAnswer(invocation -> {
                            ExternalImportJournal journal = invocation.getArgument(0);
                            boolean applied = journal.effects().stream().anyMatch(effect -> ExternalImportJournal.METAMODEL.equals(effect.kind())
                                    && ExternalImportJournal.APPLIED.equals(effect.status()));
                            if (applied) halt();
                            return invocation.callRealMethod();
                        }).when(spy).save(any());
                        return spy;
                    }
                    if (bean instanceof EditorDefinitionApplicationService && point.equals("DURING_COMPENSATION")) {
                        EditorDefinitionApplicationService spy = Mockito.spy((EditorDefinitionApplicationService) bean);
                        doAnswer(invocation -> { halt(); return null; }).when(spy).delete(any(), any(), any());
                        return spy;
                    }
                    return bean;
                }
            };
        }
    }

    /** Red de seguridad: si algún almacén no quedó dentro del directorio temporal, el proceso se detiene antes de importar nada. */
    private static void requireStorageInside(ConfigurableApplicationContext context, Path root) {
        var storage = context.getBean(com.example.editor.config.StorageProperties.class);
        Path base = root.toAbsolutePath().normalize();
        for (Path path : java.util.List.of(storage.metamodels(), storage.assets(), storage.validations(), storage.models())) {
            if (!path.toAbsolutePath().normalize().startsWith(base)) {
                System.err.println("UNSAFE_STORAGE " + path);
                Runtime.getRuntime().halt(3);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        Path root = Path.of(args[1]);
        Map<String, Object> properties = new LinkedHashMap<>();
        MetamodelHttpTestEnvironment.register(new org.springframework.test.context.DynamicPropertyRegistry() {
            @Override public void add(String name, java.util.function.Supplier<Object> valueSupplier) { properties.put(name, valueSupplier.get()); }
        }, root);
        properties.put("server.port", "0");
        properties.put("spring.main.banner-mode", "off");
        // Argumentos de línea de comandos: máxima precedencia (los «default properties» perderían frente a application.properties
        // y el proceso escribiría en ./data). El directorio de trabajo del proceso también es temporal (ver la prueba padre).
        // Antes de arrancar Spring (y por tanto antes de cualquier recuperación de arranque): nada puede apuntar fuera de la raíz temporal.
        try {
            ExternalImportStorageGuard.requirePropertiesInside(root, properties);
            ExternalImportStorageGuard.requireInside(root, java.util.List.of(Path.of(System.getProperty("user.dir"))));
            if (mode.equals("guard-probe")) {
                ExternalImportStorageGuard.requireInside(root, java.util.List.of(Path.of(args[2])));
                System.out.println("GUARD_OK");
                return;
            }
        } catch (IllegalStateException rejected) {
            System.err.println("UNSAFE_STORAGE " + rejected.getMessage());
            Runtime.getRuntime().halt(ExternalImportStorageGuard.REJECTED_EXIT_CODE);
        }
        String[] arguments = properties.entrySet().stream().map(entry -> "--" + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EditorApplication.class, CrashConfiguration.class)
                .headless(true).run(arguments)) {
            requireStorageInside(context, root);
            ExternalImportCompensationService compensation = context.getBean(ExternalImportCompensationService.class);
            if (mode.equals("import")) {
                byte[] archive = Files.readAllBytes(Path.of(args[3]));
                String fingerprint = context.getBean(ExternalProjectRecoveryService.class)
                        .analyze("external.zip", new ByteArrayInputStream(archive), archive.length).fingerprint();
                try {
                    context.getBean(ExternalProjectImportService.class).importConfirmed("external.zip", new ByteArrayInputStream(archive),
                            archive.length, args[2], args[4], fingerprint);
                    System.out.println("IMPORT_OK");
                } catch (RuntimeException failure) {
                    System.out.println("IMPORT_FAILED " + failure.getMessage());
                }
            }
            System.out.println("PENDING=" + compensation.pendingDescriptions());
        }
    }
}
