package com.example.editor.remote.storage;

/** Importa un ZIP portable remoto en un nuevo proyecto local HYDRA. */
public record RemoteProjectImportRequest(String profileId,String remotePath,String parentDirectory,String projectName) {
    public RemoteProjectImportRequest {
        profileId=profileId==null?"":profileId.trim();
        remotePath=remotePath==null?"":remotePath.trim();
        parentDirectory=parentDirectory==null?"":parentDirectory.trim();
        projectName=projectName==null?"":projectName.trim();
    }
}
