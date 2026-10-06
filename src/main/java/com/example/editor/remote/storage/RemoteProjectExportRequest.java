package com.example.editor.remote.storage;

/** Solicitud de guardado del proyecto activo como ZIP portable en un proveedor remoto. */
public record RemoteProjectExportRequest(String profileId,String remotePath,RemoteWriteMode mode,String expectedVersion,String commitMessage) {
    public RemoteProjectExportRequest {
        profileId=profileId==null?"":profileId.trim();
        remotePath=remotePath==null?"":remotePath.trim();
        mode=mode==null?RemoteWriteMode.UPSERT:mode;
        expectedVersion=expectedVersion==null?"":expectedVersion.trim();
        commitMessage=commitMessage==null?"":commitMessage.trim();
    }
}
