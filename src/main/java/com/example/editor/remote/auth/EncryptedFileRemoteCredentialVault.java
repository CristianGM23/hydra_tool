package com.example.editor.remote.auth;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Vault local cifrado con AES-256-GCM. La clave maestra se toma preferentemente
 * de entorno y, si se permite, se crea en un fichero restringido al usuario del SO.
 */
@Component
public class EncryptedFileRemoteCredentialVault implements RemoteCredentialVault {
    private static final byte[] MAGIC="HYDRA-RV1".getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE_BYTES=12;
    private static final long MAXIMUM_CREDENTIAL_FILE_BYTES=65536L;
    private static final Pattern CONNECTION_ID=Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");
    private final RemoteAuthProperties properties;
    private final ObjectMapper mapper;
    private final SecureRandom random=new SecureRandom();
    private final Map<String,RemoteCredentialMaterial> volatileCredentials=new ConcurrentHashMap<>();
    private final Object fileLock=new Object();
    private volatile SecretKeySpec masterKey;
    private volatile String protectionMode="MEMORY_ONLY";

    /** Descripción: Inicializa una instancia de EncryptedFileRemoteCredentialVault con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EncryptedFileRemoteCredentialVault(RemoteAuthProperties properties,ObjectMapper mapper){this.properties=properties;this.mapper=mapper;}

    /** Descripción: Obtiene la información correspondiente a «load» dentro de EncryptedFileRemoteCredentialVault. Entradas: connectionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Optional<RemoteCredentialMaterial> load(String connectionId){
        String id=validatedId(connectionId);
        if(!persistent()) return Optional.ofNullable(volatileCredentials.get(id));
        Path path=credentialPath(id);
        if(!Files.exists(path)) return Optional.empty();
        synchronized(fileLock){
            try {
                rejectSymlink(path);
                if(Files.size(path)>MAXIMUM_CREDENTIAL_FILE_BYTES) throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Remote credential vault entry exceeds the safe size limit");
                byte[] envelope=com.example.editor.io.BoundedIo.readInternal(path);
                byte[] clear=decrypt(id,envelope);
                try { return Optional.of(mapper.readValue(clear,RemoteCredentialMaterial.class)); }
                finally { java.util.Arrays.fill(clear,(byte)0); }
            } catch(IOException|GeneralSecurityException exception){
                throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Remote credential vault entry could not be read",exception);
            }
        }
    }

    /** Descripción: Ejecuta la operación «store» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: connectionId: identificador que delimita el recurso objetivo; material: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void store(String connectionId,RemoteCredentialMaterial material){
        String id=validatedId(connectionId);
        if(material==null||material.accessToken().isBlank()) throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"A non-empty access token is required");
        if(!persistent()){
            if(!volatileCredentials.containsKey(id)&&volatileCredentials.size()>=properties.getVault().getMaximumConnections()) throw capacity();
            volatileCredentials.put(id,material);
            return;
        }
        synchronized(fileLock){
            try {
                Path root=root();
                Files.createDirectories(root);
                rejectSymlink(root);
                restrictToOwner(root);
                Path target=credentialPath(id);
                if(!Files.exists(target)&&connectionIds().size()>=properties.getVault().getMaximumConnections()) throw capacity();
                byte[] clear=mapper.writeValueAsBytes(material);
                byte[] encrypted;
                try { encrypted=encrypt(id,clear); }
                finally { java.util.Arrays.fill(clear,(byte)0); }
                Path temporary=target.resolveSibling(target.getFileName()+".tmp-"+Long.toUnsignedString(random.nextLong()));
                try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                    channel.write(ByteBuffer.wrap(encrypted));
                    channel.force(true);
                }
                restrictToOwner(temporary);
                moveAtomically(temporary,target);
                restrictToOwner(target);
            } catch(IOException|GeneralSecurityException exception){
                throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Remote credential vault entry could not be stored",exception);
            }
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: connectionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void delete(String connectionId){
        String id=validatedId(connectionId);
        volatileCredentials.remove(id);
        if(!persistent()) return;
        synchronized(fileLock){
            try { Files.deleteIfExists(credentialPath(id)); }
            catch(IOException exception){throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Remote credential vault entry could not be deleted",exception);}
        }
    }

    /** Descripción: Ejecuta la operación «connectionIds» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<String> connectionIds(){
        if(!persistent()) return volatileCredentials.keySet().stream().sorted().toList();
        Path root=root();
        if(!Files.isDirectory(root)) return List.of();
        try(var files=Files.list(root)){
            List<String> result=new ArrayList<>();
            files.filter(Files::isRegularFile).map(path->path.getFileName().toString()).filter(name->name.endsWith(".cred")).forEach(name->{
                String id=name.substring(0,name.length()-5);
                if(CONNECTION_ID.matcher(id).matches()) result.add(id);
            });
            result.sort(String::compareTo);
            return List.copyOf(result);
        } catch(IOException exception){throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Remote credential vault could not be listed",exception);}
    }

    /** Descripción: Ejecuta la operación «persistent» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean persistent(){return properties.getVault().isPersistent();}
    /** Descripción: Ejecuta la operación «protectionMode» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String protectionMode(){
        if(!persistent()) return "MEMORY_ONLY";
        if(masterKey!=null) return protectionMode;
        String variable=properties.getVault().getMasterKeyEnvironmentVariable();
        String configured=variable.isBlank()?"":System.getenv(variable);
        if(configured!=null&&!configured.isBlank()) return "AES256_GCM_ENVIRONMENT_KEY";
        if(properties.getVault().isAllowProtectedLocalMasterKey()) return "AES256_GCM_LOCAL_PROTECTED_KEY";
        return "MASTER_KEY_REQUIRED";
    }
    /** Descripción: Obtiene la información correspondiente a «ready» dentro de EncryptedFileRemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean ready(){return !"MASTER_KEY_REQUIRED".equals(protectionMode());}

    /** Descripción: Ejecuta la operación «encrypt» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: id: identificador que delimita el recurso objetivo; clear: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] encrypt(String id,byte[] clear) throws GeneralSecurityException {
        byte[] nonce=new byte[NONCE_BYTES];random.nextBytes(nonce);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,nonce));
        cipher.updateAAD(aad(id));
        byte[] cipherText=cipher.doFinal(clear);
        ByteBuffer buffer=ByteBuffer.allocate(MAGIC.length+nonce.length+cipherText.length);
        return buffer.put(MAGIC).put(nonce).put(cipherText).array();
    }

    /** Descripción: Ejecuta la operación «decrypt» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: id: identificador que delimita el recurso objetivo; envelope: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] decrypt(String id,byte[] envelope) throws GeneralSecurityException {
        if(envelope.length<MAGIC.length+NONCE_BYTES+16) throw new GeneralSecurityException("Vault envelope is truncated");
        ByteBuffer buffer=ByteBuffer.wrap(envelope);
        byte[] magic=new byte[MAGIC.length];buffer.get(magic);
        if(!MessageDigest.isEqual(MAGIC,magic)) throw new GeneralSecurityException("Vault envelope version is not supported");
        byte[] nonce=new byte[NONCE_BYTES];buffer.get(nonce);
        byte[] cipherText=new byte[buffer.remaining()];buffer.get(cipherText);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,nonce));
        cipher.updateAAD(aad(id));
        return cipher.doFinal(cipherText);
    }

    /** Descripción: Ejecuta la operación «key» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private SecretKeySpec key(){ensureMasterKeyIfNeeded();return masterKey;}
    /** Descripción: Valida las condiciones necesarias para «ensureMasterKeyIfNeeded» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureMasterKeyIfNeeded(){
        if(!persistent()||masterKey!=null) return;
        synchronized(fileLock){
            if(masterKey!=null) return;
            byte[] keyBytes=environmentKey();
            if(keyBytes!=null){protectionMode="AES256_GCM_ENVIRONMENT_KEY";masterKey=new SecretKeySpec(keyBytes,"AES");java.util.Arrays.fill(keyBytes,(byte)0);return;}
            if(!properties.getVault().isAllowProtectedLocalMasterKey()) throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Persistent remote credential storage requires the configured vault master-key environment variable");
            keyBytes=localProtectedKey();
            protectionMode="AES256_GCM_LOCAL_PROTECTED_KEY";
            masterKey=new SecretKeySpec(keyBytes,"AES");
            java.util.Arrays.fill(keyBytes,(byte)0);
        }
    }

    /** Descripción: Ejecuta la operación «environmentKey» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] environmentKey(){
        String variable=properties.getVault().getMasterKeyEnvironmentVariable();
        String encoded=variable.isBlank()?"":System.getenv(variable);
        if(encoded==null||encoded.isBlank()) return null;
        return decodeKey(encoded.trim(),"environment variable "+variable);
    }

    /** Descripción: Ejecuta la operación «localProtectedKey» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] localProtectedKey(){
        try {
            Path root=root();Files.createDirectories(root);restrictToOwner(root);rejectSymlink(root);
            Path file=root.resolve(properties.getVault().getLocalMasterKeyFile()).normalize();
            if(!file.getParent().equals(root)) throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Vault key path escapes the configured root");
            if(Files.exists(file)){
                rejectSymlink(file);restrictToOwner(file);
                return decodeKey(new String(com.example.editor.io.BoundedIo.read(file,1024),StandardCharsets.US_ASCII).trim(),"local protected key");
            }
            byte[] generated=new byte[32];random.nextBytes(generated);
            String encoded=Base64.getEncoder().encodeToString(generated);
            Path temporary=file.resolveSibling(file.getFileName()+".tmp-"+Long.toUnsignedString(random.nextLong()));
            Files.writeString(temporary,encoded,StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            restrictToOwner(temporary);moveAtomically(temporary,file);restrictToOwner(file);
            return generated;
        } catch(IOException exception){throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Vault master key could not be initialized",exception);}
    }

    /** Descripción: Ejecuta la operación «decodeKey» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: encoded: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] decodeKey(String encoded,String source){
        try {
            byte[] value=Base64.getDecoder().decode(encoded);
            if(value.length!=32) throw new IllegalArgumentException("key must contain exactly 32 bytes");
            return value;
        } catch(IllegalArgumentException exception){throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Invalid AES-256 master key in "+source,exception);}
    }

    /** Descripción: Ejecuta la operación «restrictToOwner» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void restrictToOwner(Path path) throws IOException {
        try {
            EnumSet<PosixFilePermission> permissions=EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE);
            if(Files.isDirectory(path))permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(path,permissions);return;
        } catch(UnsupportedOperationException ignored){/* Windows/ACL path below. */}
        AclFileAttributeView acl=Files.getFileAttributeView(path,AclFileAttributeView.class);
        if(acl!=null){
            UserPrincipal owner=Files.getOwner(path);AclEntry.Builder entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner).setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if(Files.isDirectory(path))entry.setFlags(AclEntryFlag.DIRECTORY_INHERIT,AclEntryFlag.FILE_INHERIT);
            acl.setAcl(List.of(entry.build()));return;
        }
        java.io.File file=path.toFile();boolean ok=file.setReadable(false,false)&file.setWritable(false,false)&file.setExecutable(false,false)&file.setReadable(true,true)&file.setWritable(true,true);
        if(Files.isDirectory(path))ok&=file.setExecutable(true,true);
        if(!ok)throw new IOException("Operating system could not enforce owner-only credential-vault permissions");
    }
    /** Descripción: Ejecuta la operación «rejectSymlink» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rejectSymlink(Path path){if(Files.isSymbolicLink(path)) throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Credential vault paths cannot be symbolic links");}
    /** Descripción: Ejecuta la operación «root» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path root(){return properties.getVault().getRoot().toAbsolutePath().normalize();}
    /** Descripción: Ejecuta la operación «credentialPath» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path credentialPath(String id){Path root=root();Path target=root.resolve(id+".cred").normalize();if(!target.getParent().equals(root))throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"Invalid connection identifier");return target;}
    /** Descripción: Valida las condiciones necesarias para «validatedId» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String validatedId(String value){String id=value==null?"":value.trim();if(!CONNECTION_ID.matcher(id).matches())throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"Connection identifier must contain only letters, numbers, dot, underscore or hyphen");return id;}
    /** Descripción: Ejecuta la operación «aad» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] aad(String id){return ("HYDRA_REMOTE_VAULT_V1|"+id).getBytes(StandardCharsets.UTF_8);}
    /** Descripción: Ejecuta la operación «moveAtomically» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void moveAtomically(Path source,Path target) throws IOException {try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException exception){Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}}
    /** Descripción: Ejecuta la operación «capacity» correspondiente a la responsabilidad de EncryptedFileRemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteAuthException capacity(){return new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Maximum configured external connections reached");}
}
