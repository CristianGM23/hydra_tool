package com.example.editor.project.portability;
import java.time.Instant;
/** Manifiesto estable del contenedor portable de proyecto HYDRA. */
public record ProjectPortableManifest(String schemaVersion,String hydraVersion,String archiveSha256,long archiveBytes,boolean secretsIncluded,Instant createdAt){
    public static final String CURRENT_SCHEMA_VERSION="1.0";
    public ProjectPortableManifest{schemaVersion=schemaVersion==null||schemaVersion.isBlank()?CURRENT_SCHEMA_VERSION:schemaVersion;if(!CURRENT_SCHEMA_VERSION.equals(schemaVersion))throw new IllegalArgumentException("Unsupported portable project schemaVersion: "+schemaVersion);hydraVersion=hydraVersion==null?"":hydraVersion.trim();archiveSha256=archiveSha256==null?"":archiveSha256.trim();createdAt=createdAt==null?Instant.now():createdAt;if(secretsIncluded)throw new IllegalArgumentException("Portable HYDRA packages cannot include secrets");}
}
