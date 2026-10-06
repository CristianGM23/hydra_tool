package com.example.editor;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EditorApplication {
    /** Descripción: Inicia HYDRA respetando el modo de escritorio o servidor antes de que Spring inicialice AWT. Entradas: args: argumentos de arranque, incluidos perfiles y una posible preferencia explícita de modo headless. Salida: inicia el contexto de Spring y mantiene disponible el selector nativo de directorios en ejecuciones locales de escritorio. */
    /** Descripción: Ejecuta la operación «main» correspondiente a la responsabilidad de EditorApplication. Entradas: args: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void main(String[] args) {
        SpringApplication application = new SpringApplication(EditorApplication.class);
        application.setHeadless(resolveHeadlessMode(args));
        application.run(args);
    }

    /** Descripción: Decide si HYDRA debe arrancar sin interfaz nativa. Entradas: args: argumentos de línea de comandos; también se respetan las propiedades de sistema y variables de entorno equivalentes. Salida: true para perfiles de servidor/test o cuando se solicita explícitamente; false para el modo local/escritorio, permitiendo Examinar incluso al abrir HYDRA mediante localhost. */
    /** Descripción: Obtiene la información correspondiente a «resolveHeadlessMode» dentro de EditorApplication. Entradas: args: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean resolveHeadlessMode(String[] args) {
        String explicitHeadless = commandLineValue(args, "spring.main.headless");
        if (explicitHeadless.isBlank()) explicitHeadless = firstNonBlank(System.getProperty("spring.main.headless"), System.getenv("SPRING_MAIN_HEADLESS"));
        if (!explicitHeadless.isBlank()) return Boolean.parseBoolean(explicitHeadless);

        Set<String> profiles = activeProfiles(args);
        return profiles.contains("production") || profiles.contains("headless-server") || profiles.contains("test");
    }

    /** Descripción: Reúne los perfiles activos que pueden conocerse antes de crear el contexto de Spring. Entradas: args: argumentos de arranque. Salida: conjunto normalizado de perfiles indicado por argumentos, propiedad de sistema o variable de entorno. */
    /** Descripción: Ejecuta la operación «activeProfiles» correspondiente a la responsabilidad de EditorApplication. Entradas: args: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Set<String> activeProfiles(String[] args) {
        String configured = commandLineValue(args, "spring.profiles.active");
        if (configured.isBlank()) configured = firstNonBlank(System.getProperty("spring.profiles.active"), System.getenv("SPRING_PROFILES_ACTIVE"));
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(configured.split(",")).map(String::trim).map(value -> value.toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank()).forEach(result::add);
        return result;
    }

    /** Descripción: Obtiene una opción Spring escrita como --clave=valor sin interpretar otras opciones. Entradas: args: argumentos de arranque; key: nombre de la propiedad buscada. Salida: valor encontrado o cadena vacía cuando no se ha indicado. */
    /** Descripción: Ejecuta la operación «commandLineValue» correspondiente a la responsabilidad de EditorApplication. Entradas: args: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static String commandLineValue(String[] args, String key) {
        if (args == null || key == null || key.isBlank()) return "";
        String prefix = "--" + key + "=";
        for (String argument : args) if (argument != null && argument.startsWith(prefix)) return argument.substring(prefix.length()).trim();
        return "";
    }

    /** Descripción: Selecciona el primer texto no vacío entre dos fuentes de configuración. Entradas: first: valor prioritario; second: valor alternativo. Salida: primer valor no vacío o cadena vacía. */
    /** Descripción: Ejecuta la operación «firstNonBlank» correspondiente a la responsabilidad de EditorApplication. Entradas: first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        return second == null ? "" : second.trim();
    }
}
