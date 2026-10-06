package com.example.editor.security.process;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Security policy for every native process launched by HYDRA. */
@Component
@ConfigurationProperties(prefix = "hydra.security.external-process")
public class HydraExternalProcessSecurityProperties {
    private boolean enabled = true;
    private Set<String> allowedExecutables = new LinkedHashSet<>(List.of(
            "git", "python", "python3", "java", "javac", "mvn", "mvnw", "node", "npm", "npx", "go"));
    private Set<String> inheritedEnvironment = new LinkedHashSet<>(List.of(
            "PATH", "Path", "PATHEXT", "SystemRoot", "WINDIR", "COMSPEC", "TEMP", "TMP", "TMPDIR",
            "HOME", "USERPROFILE", "LANG", "LC_ALL", "JAVA_HOME", "MAVEN_HOME", "M2_HOME",
            "HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "no_proxy", "SSL_CERT_FILE", "SSL_CERT_DIR"));
    private Set<String> requestEnvironment = new LinkedHashSet<>(List.of(
            "CI", "NO_COLOR", "FORCE_COLOR", "LANG", "LC_ALL", "JAVA_HOME", "MAVEN_OPTS", "NODE_OPTIONS",
            "PYTHONUTF8", "PYTHONIOENCODING"));
    private int maximumArguments = 128;
    private int maximumArgumentCharacters = 16_384;
    private long maximumTemporaryWorkspaceBytes = 536_870_912L;
    private int maximumTemporaryWorkspaceFiles = 16_384;
    private Path temporaryRoot = Path.of("./data/external-process-workspaces");
    private int maximumConcurrentProcesses = 8;
    private int maximumProcessTimeoutSeconds = 900;
    private int maximumCapturedOutputBytes = 8_388_608;

    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled(){return enabled;}
    /** Descripción: Actualiza el estado gestionado por HydraExternalProcessSecurityProperties mediante la operación «setEnabled». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean value){enabled=value;}
    /** Descripción: Obtiene la información correspondiente a «getAllowedExecutables» dentro de HydraExternalProcessSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Set<String> getAllowedExecutables(){return Set.copyOf(allowedExecutables);}
    /** Descripción: Actualiza el estado gestionado por HydraExternalProcessSecurityProperties mediante la operación «setAllowedExecutables». Entradas: values: colección de elementos que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAllowedExecutables(Set<String> values){allowedExecutables=normalize(values);}
    /** Descripción: Obtiene la información correspondiente a «getInheritedEnvironment» dentro de HydraExternalProcessSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Set<String> getInheritedEnvironment(){return Set.copyOf(inheritedEnvironment);}
    /** Descripción: Actualiza el estado gestionado por HydraExternalProcessSecurityProperties mediante la operación «setInheritedEnvironment». Entradas: values: colección de elementos que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setInheritedEnvironment(Set<String> values){inheritedEnvironment=clean(values);}
    /** Descripción: Obtiene la información correspondiente a «getRequestEnvironment» dentro de HydraExternalProcessSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Set<String> getRequestEnvironment(){return Set.copyOf(requestEnvironment);}
    /** Descripción: Actualiza el estado gestionado por HydraExternalProcessSecurityProperties mediante la operación «setRequestEnvironment». Entradas: values: colección de elementos que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRequestEnvironment(Set<String> values){requestEnvironment=clean(values);}
    /** Descripción: Obtiene la información correspondiente a «getMaximumArguments» dentro de HydraExternalProcessSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumArguments(){return maximumArguments;}
    /** Descripción: Actualiza el estado gestionado por HydraExternalProcessSecurityProperties mediante la operación «setMaximumArguments». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumArguments(int value){maximumArguments=Math.max(1,value);}
    /** Descripción: Obtiene la información correspondiente a «getMaximumArgumentCharacters» dentro de HydraExternalProcessSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumArgumentCharacters(){return maximumArgumentCharacters;}
    /** Descripción: Actualiza el estado gestionado por HydraExternalProcessSecurityProperties mediante la operación «setMaximumArgumentCharacters». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumArgumentCharacters(int value){maximumArgumentCharacters=Math.max(256,value);}
    /** Descripción: Obtiene la información correspondiente a «getMaximumTemporaryWorkspaceBytes» dentro de HydraExternalProcessSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumTemporaryWorkspaceBytes(){return maximumTemporaryWorkspaceBytes;}
    /** Descripción: Actualiza el estado gestionado por HydraExternalProcessSecurityProperties mediante la operación «setMaximumTemporaryWorkspaceBytes». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumTemporaryWorkspaceBytes(long value){maximumTemporaryWorkspaceBytes=Math.max(1_048_576L,value);}
    /** Descripción: Obtiene la información correspondiente a «getMaximumTemporaryWorkspaceFiles» dentro de HydraExternalProcessSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumTemporaryWorkspaceFiles(){return maximumTemporaryWorkspaceFiles;}
    /** Descripción: Actualiza el estado gestionado por HydraExternalProcessSecurityProperties mediante la operación «setMaximumTemporaryWorkspaceFiles». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumTemporaryWorkspaceFiles(int value){maximumTemporaryWorkspaceFiles=Math.max(1,value);}
    /** Descripción: Obtiene el máximo global de procesos externos simultáneos. Entradas: no requiere parámetros. Salida: límite de concurrencia. */ public int getMaximumConcurrentProcesses(){return maximumConcurrentProcesses;}
    /** Descripción: Configura el máximo global de procesos externos simultáneos. Entradas: value: límite solicitado. Salida: actualiza el límite con un rango seguro. */ public void setMaximumConcurrentProcesses(int value){maximumConcurrentProcesses=Math.max(1,Math.min(64,value));}
    /** Descripción: Obtiene el timeout máximo permitido para cualquier proceso externo. Entradas: no requiere parámetros. Salida: segundos máximos. */ public int getMaximumProcessTimeoutSeconds(){return maximumProcessTimeoutSeconds;}
    /** Descripción: Configura el timeout máximo para procesos externos. Entradas: value: segundos solicitados. Salida: actualiza un límite acotado. */ public void setMaximumProcessTimeoutSeconds(int value){maximumProcessTimeoutSeconds=Math.max(1,Math.min(86_400,value));}
    /** Descripción: Obtiene el máximo de bytes capturables por cada stream de proceso. Entradas: no requiere parámetros. Salida: bytes máximos. */ public int getMaximumCapturedOutputBytes(){return maximumCapturedOutputBytes;}
    /** Descripción: Configura el máximo de bytes capturables por stream. Entradas: value: bytes solicitados. Salida: actualiza el límite defensivo. */ public void setMaximumCapturedOutputBytes(int value){maximumCapturedOutputBytes=Math.max(1_024,Math.min(67_108_864,value));}
    /** Descripción: Obtiene la información correspondiente a «getTemporaryRoot» dentro de HydraExternalProcessSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getTemporaryRoot(){return temporaryRoot;}
    /** Descripción: Actualiza el estado gestionado por HydraExternalProcessSecurityProperties mediante la operación «setTemporaryRoot». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setTemporaryRoot(Path value){temporaryRoot=value==null?Path.of("./data/external-process-workspaces"):value;}

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de HydraExternalProcessSecurityProperties. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> normalize(Set<String> values){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        if(values!=null) for(String value:values) if(value!=null&&!value.isBlank()) result.add(normalizedExecutable(value));
        return result;
    }
    /** Descripción: Ejecuta la operación «clean» correspondiente a la responsabilidad de HydraExternalProcessSecurityProperties. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> clean(Set<String> values){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        if(values!=null) for(String value:values) if(value!=null&&!value.isBlank()) result.add(value.trim());
        return result;
    }
    /** Descripción: Ejecuta la operación «normalizedExecutable» correspondiente a la responsabilidad de HydraExternalProcessSecurityProperties. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String normalizedExecutable(String value){
        String name=Path.of(value.trim()).getFileName().toString().toLowerCase(Locale.ROOT);
        if(name.endsWith(".exe")) name=name.substring(0,name.length()-4);
        if(name.endsWith(".cmd")) name=name.substring(0,name.length()-4);
        if(name.endsWith(".bat")) name=name.substring(0,name.length()-4);
        return name;
    }
}
