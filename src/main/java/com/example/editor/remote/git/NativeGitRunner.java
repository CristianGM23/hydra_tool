package com.example.editor.remote.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.example.editor.concurrent.BoundedPermitPool;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraProcessTreeTerminator;
import com.example.editor.remote.auth.RemoteSecretRedactor;
import com.example.editor.remote.storage.RemoteStorageCredentials;
import com.example.editor.remote.storage.RemoteStorageException;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/** Ejecuta Git sin shell, con argumentos acotados y credenciales solo en el entorno efímero del proceso. */
@Component
public class NativeGitRunner {
    private final NativeGitProperties properties;
    private final HydraExternalProcessPolicy securityPolicy;
    private final HydraProcessTreeTerminator processTreeTerminator;
    private final BoundedPermitPool processSlots;
    @Autowired
    /** Descripción: Inicializa una instancia de NativeGitRunner con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; securityPolicy: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public NativeGitRunner(NativeGitProperties properties, HydraExternalProcessPolicy securityPolicy){this(properties,securityPolicy,new HydraProcessTreeTerminator());}
    /** Descripción: Inicializa una instancia de NativeGitRunner con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; securityPolicy: dato de entrada necesario para la operación; processTreeTerminator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ NativeGitRunner(NativeGitProperties properties, HydraExternalProcessPolicy securityPolicy, HydraProcessTreeTerminator processTreeTerminator){this.properties=properties;this.securityPolicy=securityPolicy;this.processTreeTerminator=processTreeTerminator;this.processSlots=new BoundedPermitPool(properties.getMaximumConcurrentProcesses());}
    /** Descripción: Inicializa una instancia de NativeGitRunner con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public NativeGitRunner(NativeGitProperties properties){this(properties,new HydraExternalProcessPolicy(new HydraExternalProcessSecurityProperties()));}

    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de NativeGitRunner. Entradas: workingDirectory: dato de entrada necesario para la operación; arguments: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Result run(Path workingDirectory,List<String> arguments){return run(workingDirectory,arguments,null,null);}

    /** Descripción: Procesa la operación «runAuthenticated» coordinando las responsabilidades de NativeGitRunner. Entradas: workingDirectory: dato de entrada necesario para la operación; arguments: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Result runAuthenticated(Path workingDirectory,List<String> arguments,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials){
        if(credentials==null||!credentials.configured())throw new RemoteStorageException(RemoteStorageException.Reason.CREDENTIALS_MISSING,"Remote Git credentials are not configured");
        return run(workingDirectory,arguments,profile,credentials);
    }

    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de NativeGitRunner. Entradas: workingDirectory: dato de entrada necesario para la operación; arguments: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Result run(Path workingDirectory,List<String> arguments,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials){
        if(!properties.isEnabled())throw new RemoteStorageException(RemoteStorageException.Reason.DISABLED,"Native Git integration is disabled");
        if(workingDirectory==null||!Files.isDirectory(workingDirectory))throw invalid("Git working directory does not exist");
        List<String> command=new ArrayList<>();command.add(properties.getExecutable());command.add("-c");command.add("credential.helper=");
        if(profile!=null&&credentials!=null){command.add("-c");command.add("credential.username="+username(profile));}
        command.addAll(arguments);
        Path askpass=null;
        Process process=null;
        Thread reader=null;
        BoundedPermitPool.Permit permit=null;
        HydraProcessTreeTerminator.ProcessTreeTracker tracker=null;
        try{
            permit=processSlots.tryAcquire(properties.getProcessAcquireTimeoutSeconds(),TimeUnit.SECONDS);
            if(permit==null)throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Native Git capacity is busy; retry later");
            securityPolicy.validateCommand(command);
            securityPolicy.validateWorkingDirectory(workingDirectory);
            ProcessBuilder builder=new ProcessBuilder(command).directory(workingDirectory.toFile()).redirectErrorStream(true);
            securityPolicy.configureMinimalEnvironment(builder,Map.of());
            Map<String,String> env=builder.environment();env.put("GIT_TERMINAL_PROMPT","0");env.put("GCM_INTERACTIVE","Never");env.put("LC_ALL","C");env.put("LANG","C");
            if(profile!=null&&credentials!=null){askpass=createAskPass();env.put("GIT_ASKPASS",askpass.toAbsolutePath().toString());env.put("HYDRA_GIT_PASSWORD",credentials.token());}
            process=builder.start();
            tracker=processTreeTerminator.track(process,Duration.ofMillis(properties.getProcessTreeObservationIntervalMillis()));
            Process runningProcess=process;
            AtomicReference<byte[]> captured=new AtomicReference<>(new byte[0]);
            reader=new Thread(()->captured.set(readBounded(runningProcess.getInputStream(),properties.getMaximumOutputBytes())),"hydra-git-output");reader.setDaemon(true);reader.start();
            Duration timeout=properties.getTimeout();boolean finished=process.waitFor(Math.max(1L,timeout.toMillis()),java.util.concurrent.TimeUnit.MILLISECONDS);
            if(!finished){terminate(tracker,process);tracker=null;awaitReader(reader,runningProcess);throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Git operation timed out after "+timeout.toSeconds()+" seconds");}
            long lingering=tracker==null?0L:tracker.aliveObservedProcesses();
            HydraProcessTreeTerminator.TerminationReport completion=terminate(tracker,process);tracker=null;
            boolean drained=awaitReader(reader,runningProcess);String output=redactOutput(new String(captured.get(),StandardCharsets.UTF_8).trim(),credentials);
            if(captured.get().length>=properties.getMaximumOutputBytes()) output=output+"\n[HYDRA: Git output reached the configured capture limit and may be truncated]";
            if(!drained) output=output+"\n[HYDRA: Git output reader exceeded the configured drain wait and was cancelled]";
            if(lingering>0) output=output+"\n[HYDRA: Git left "+lingering+" descendant process(es); the tracked tree was terminated]";
            if(!completion.terminated()) output=output+"\n[HYDRA: Git process completion left "+completion.survivingProcesses()+" surviving process(es)]";
            return new Result(process.exitValue(),output);
        }catch(IllegalArgumentException exception){throw invalid(exception.getMessage());}catch(InterruptedException exception){terminate(tracker,process);tracker=null;if(reader!=null)cancelReader(reader,process);Thread.currentThread().interrupt();throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Git operation was interrupted",exception);}catch(IOException exception){throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Git executable could not be started: "+properties.getExecutable(),exception);}finally{if(tracker!=null)terminate(tracker,process);else if(process!=null&&process.isAlive())terminate(null,process);if(reader!=null&&reader.isAlive())cancelReader(reader,process);if(permit!=null)permit.close();if(askpass!=null)try{Files.deleteIfExists(askpass);}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/remote/git/NativeGitRunner.java",ignored);}}
    }

    /** Descripción: Ejecuta la operación «terminate» correspondiente a la responsabilidad de Result. Entradas: tracker: dato de entrada necesario para la operación; process: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraProcessTreeTerminator.TerminationReport terminate(HydraProcessTreeTerminator.ProcessTreeTracker tracker,Process process){if(tracker!=null)return processTreeTerminator.terminateTracked(tracker,Duration.ofMillis(properties.getProcessTerminationGraceMillis()),Duration.ofMillis(properties.getProcessTerminationForceWaitMillis()),properties.getProcessTerminationMaximumPasses());return processTreeTerminator.terminate(process,Duration.ofMillis(properties.getProcessTerminationGraceMillis()),Duration.ofMillis(properties.getProcessTerminationForceWaitMillis()),properties.getProcessTerminationMaximumPasses());}
    /** Descripción: Ejecuta la operación «awaitReader» correspondiente a la responsabilidad de Result. Entradas: reader: dato de entrada necesario para la operación; process: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean awaitReader(Thread reader,Process process) throws InterruptedException {
        if(reader==null)return true;
        reader.join(properties.getOutputDrainWaitMillis());
        if(!reader.isAlive())return true;
        cancelReader(reader,process);
        return !reader.isAlive();
    }
    /** Descripción: Evalúa la condición «cancelReader» sin modificar el estado externo. Entradas: reader: dato de entrada necesario para la operación; process: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void cancelReader(Thread reader,Process process){
        if(reader==null||!reader.isAlive())return;
        reader.interrupt();
        if(process!=null)try{process.getInputStream().close();}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/remote/git/NativeGitRunner.java",ignored);}
        try{reader.join(Math.min(500L,properties.getOutputDrainWaitMillis()));}catch(InterruptedException exception){Thread.currentThread().interrupt();}
    }
    /** Capacidad restante del pool acotado de procesos Git nativos. */ /** Descripción: Ejecuta la operación «availableProcessSlots» correspondiente a la responsabilidad de Result. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int availableProcessSlots(){return processSlots.availablePermits();}
    /** Estado observable de la capacidad Git nativa. */ /** Descripción: Ejecuta la operación «capacityStatus» correspondiente a la responsabilidad de Result. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public BoundedPermitPool.Snapshot capacityStatus(){return processSlots.snapshot();}
    /** Descripción: Ejecuta la operación «redactOutput» correspondiente a la responsabilidad de Result. Entradas: output: dato de entrada necesario para la operación; credentials: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String redactOutput(String output,RemoteStorageCredentials credentials){String redacted=RemoteSecretRedactor.redact(output);if(credentials!=null&&!credentials.token().isBlank())redacted=redacted.replace(credentials.token(),"[REDACTED]");return redacted;}

    /** Descripción: Valida las condiciones necesarias para «requireSuccess» y rechaza entradas no válidas. Entradas: result: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void requireSuccess(Result result,String operation){if(result.exitCode()!=0)throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,operation+" failed"+(result.output().isBlank()?"":": "+result.output()));}

    /** Descripción: Obtiene la información correspondiente a «readBounded» dentro de NativeGitRunner. Entradas: input: valor de entrada que debe procesarse; maximum: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] readBounded(InputStream input,int maximum){try(input;ByteArrayOutputStream output=new ByteArrayOutputStream(Math.min(maximum,8192))){byte[] buffer=new byte[4096];int retained=0;int read;while((read=input.read(buffer))>=0){if(read==0)continue;int keep=Math.min(read,Math.max(0,maximum-retained));if(keep>0){output.write(buffer,0,keep);retained+=keep;}}return output.toByteArray();}catch(IOException exception){return ("Could not read Git process output: "+exception.getMessage()).getBytes(StandardCharsets.UTF_8);}}
    /** Descripción: Crea o prepara el resultado de «createAskPass» dentro de NativeGitRunner. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path createAskPass() throws IOException {boolean windows=System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win");Path script=Files.createTempFile("hydra-git-askpass-",windows?".cmd":".sh");String content=windows?"@echo off\r\npowershell.exe -NoProfile -NonInteractive -Command \"[Console]::Out.WriteLine($env:HYDRA_GIT_PASSWORD)\"\r\n":"#!/bin/sh\nprintf '%s\\n' \"$HYDRA_GIT_PASSWORD\"\n";Files.writeString(script,content,StandardCharsets.UTF_8);if(!windows)try{Files.setPosixFilePermissions(script,EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE));}catch(UnsupportedOperationException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/remote/git/NativeGitRunner.java",ignored);}return script;}
    /** Descripción: Ejecuta la operación «username» correspondiente a la responsabilidad de NativeGitRunner. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String username(RemoteStorageProperties.Profile profile){if(profile.getProvider()==RemoteStorageProviderType.GITHUB)return "x-access-token";if(profile.getProvider()==RemoteStorageProviderType.GITLAB)return "oauth2";return profile.getGitUsername().isBlank()?"oauth2":profile.getGitUsername();}
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de NativeGitRunner. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException invalid(String message){return new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,message);}
    public record Result(int exitCode,String output){public boolean successful(){return exitCode==0;}}
}
