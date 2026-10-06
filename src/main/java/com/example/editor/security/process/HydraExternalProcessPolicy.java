package com.example.editor.security.process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Validates commands and constructs the minimal inherited environment for native processes. */
@Component
public class HydraExternalProcessPolicy {
    private final HydraExternalProcessSecurityProperties properties;
    private final Semaphore globalProcessPermits;
    /** Descripción: Inicializa una instancia de HydraExternalProcessPolicy con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraExternalProcessPolicy(HydraExternalProcessSecurityProperties properties){this.properties=properties;this.globalProcessPermits=new Semaphore(properties==null?1:properties.getMaximumConcurrentProcesses(),true);}

    /** Descripción: Valida las condiciones necesarias para «validateCommand» y rechaza entradas no válidas. Entradas: command: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void validateCommand(List<String> command){
        if(!properties.isEnabled()) return;
        if(command==null||command.isEmpty()) throw new IllegalArgumentException("External process command is empty");
        if(command.size()>properties.getMaximumArguments()) throw new IllegalArgumentException("External process command has too many arguments");
        String executable=HydraExternalProcessSecurityProperties.normalizedExecutable(command.get(0));
        if(!properties.getAllowedExecutables().contains(executable)) throw new IllegalArgumentException("External executable is not allowed: "+executable);
        int total=0;
        for(String argument:command){
            if(argument==null) throw new IllegalArgumentException("External process argument is null");
            if(argument.indexOf('\0')>=0||argument.indexOf('\r')>=0||argument.indexOf('\n')>=0) throw new IllegalArgumentException("External process argument contains a forbidden control character");
            total+=argument.length();
            if(total>properties.getMaximumArgumentCharacters()) throw new IllegalArgumentException("External process command exceeds the configured character limit");
        }
    }


    /** Descripción: Valida presupuestos globales de timeout y salida antes de iniciar el proceso. Entradas: request: solicitud neutral. Salida: no devuelve valor; rechaza configuraciones que exceden la política. */ public void validateRuntimeBudget(HydraExternalProcessRunRequest request){
        if(request==null) throw new IllegalArgumentException("External process request is required");
        if(request.timeoutSeconds()>properties.getMaximumProcessTimeoutSeconds()) throw new IllegalArgumentException("External process timeout exceeds the global budget");
        if(request.maximumOutputBytes()>properties.getMaximumCapturedOutputBytes()) throw new IllegalArgumentException("External process output capture exceeds the global budget");
    }

    /** Descripción: Reserva una plaza del presupuesto global de procesos externos. Entradas: waitSeconds: espera máxima. Salida: verdadero cuando se obtuvo permiso. */ public boolean acquireGlobalProcessPermit(int waitSeconds) throws InterruptedException {
        return globalProcessPermits.tryAcquire(Math.max(1,waitSeconds),TimeUnit.SECONDS);
    }

    /** Descripción: Libera una plaza previamente reservada del presupuesto global. Entradas: no requiere parámetros. Salida: no devuelve valor. */ public void releaseGlobalProcessPermit(){globalProcessPermits.release();}

    /** Descripción: Devuelve el número de plazas globales actualmente disponibles para diagnóstico. Entradas: no requiere parámetros. Salida: plazas libres. */ public int availableGlobalProcessPermits(){return globalProcessPermits.availablePermits();}

    /** Descripción: Valida las condiciones necesarias para «validateWorkingDirectory» y rechaza entradas no válidas. Entradas: directory: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void validateWorkingDirectory(Path directory){
        if(directory==null||!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("External process working directory does not exist");
        rejectSymbolicPath(directory.toAbsolutePath().normalize());
    }

    /** Descripción: Ejecuta la operación «configureMinimalEnvironment» correspondiente a la responsabilidad de HydraExternalProcessPolicy. Entradas: builder: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void configureMinimalEnvironment(ProcessBuilder builder, Map<String,String> requested){
        if(!properties.isEnabled()) return;
        Map<String,String> host=new LinkedHashMap<>(System.getenv());
        Map<String,String> environment=builder.environment();
        environment.clear();
        for(String key:properties.getInheritedEnvironment()){
            String value=host.get(key);
            if(value!=null&&!value.isBlank()) environment.put(key,value);
        }
        if(requested!=null) for(var entry:requested.entrySet()){
            if(!properties.getRequestEnvironment().contains(entry.getKey())) throw new IllegalArgumentException("External process environment key is not allowed: "+entry.getKey());
            if(entry.getValue()!=null) environment.put(entry.getKey(),safeEnvironmentValue(entry.getValue()));
        }
    }

    /** Descripción: Ejecuta la operación «rejectSymbolicPath» correspondiente a la responsabilidad de HydraExternalProcessPolicy. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void rejectSymbolicPath(Path path){
        Path absolute=path.toAbsolutePath().normalize();
        Path current=absolute.getRoot();
        for(Path segment:absolute){
            current=current==null?segment:current.resolve(segment);
            if(Files.exists(current,LinkOption.NOFOLLOW_LINKS)&&Files.isSymbolicLink(current)) throw new IllegalArgumentException("Symbolic-link path is not allowed for external process workspaces: "+path);
        }
    }

    /** Descripción: Ejecuta la operación «enforceWorkspaceQuota» correspondiente a la responsabilidad de HydraExternalProcessPolicy. Entradas: root: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void enforceWorkspaceQuota(Path root){
        if(root==null||!Files.exists(root,LinkOption.NOFOLLOW_LINKS)) return;
        rejectSymbolicPath(root);
        final long[] bytes={0L}; final int[] files={0};
        try(var stream=Files.walk(root)){
            stream.forEach(path->{
                if(Files.isSymbolicLink(path)) throw new WorkspaceQuotaViolation("Symbolic links are not allowed in isolated external-process workspaces");
                if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)){
                    files[0]++;
                    try{bytes[0]=Math.addExact(bytes[0],Files.size(path));}catch(IOException|ArithmeticException exception){throw new WorkspaceQuotaViolation("Could not measure external-process workspace",exception);}
                    if(files[0]>properties.getMaximumTemporaryWorkspaceFiles()) throw new WorkspaceQuotaViolation("External-process workspace exceeds file quota");
                    if(bytes[0]>properties.getMaximumTemporaryWorkspaceBytes()) throw new WorkspaceQuotaViolation("External-process workspace exceeds byte quota");
                }
            });
        }catch(IOException exception){throw new WorkspaceQuotaViolation("Could not inspect external-process workspace",exception);}
    }

    /** Descripción: Ejecuta la operación «temporaryRoot» correspondiente a la responsabilidad de HydraExternalProcessPolicy. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path temporaryRoot(){return properties.getTemporaryRoot().toAbsolutePath().normalize();}

    /** Descripción: Ejecuta la operación «safeEnvironmentValue» correspondiente a la responsabilidad de HydraExternalProcessPolicy. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeEnvironmentValue(String value){
        if(value.indexOf('\0')>=0||value.indexOf('\r')>=0||value.indexOf('\n')>=0) throw new IllegalArgumentException("External process environment value contains a forbidden control character");
        return value;
    }

    public static final class WorkspaceQuotaViolation extends RuntimeException {
        /** Descripción: Inicializa una instancia de WorkspaceQuotaViolation con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public WorkspaceQuotaViolation(String message){super(message);}
        /** Descripción: Inicializa una instancia de WorkspaceQuotaViolation con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public WorkspaceQuotaViolation(String message,Throwable cause){super(message,cause);}
    }
}
