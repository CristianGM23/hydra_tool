package com.example.editor.remote.git;

/** Petición acotada para operaciones Git del proyecto activo. */
public record RemoteGitOperationRequest(String profileId,String branch,String sourceBranch,String commitMessage,RemoteGitPullStrategy pullStrategy) { }
