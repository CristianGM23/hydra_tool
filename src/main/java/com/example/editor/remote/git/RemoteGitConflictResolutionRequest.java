package com.example.editor.remote.git;

/** Request to save the resolved content of one conflicted file. */
public record RemoteGitConflictResolutionRequest(String profileId,String relativePath,String resolvedContent,boolean stageResolved) { }
