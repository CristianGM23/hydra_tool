package com.example.editor.remote.git;

import java.util.List;

/** Result of storing one conflict resolution into the working tree. */
public record RemoteGitConflictResolutionResult(String relativePath,boolean staged,List<String> remainingConflicts,List<String> changedFiles,
        String message) {
    public RemoteGitConflictResolutionResult{
        remainingConflicts=remainingConflicts==null?List.of():List.copyOf(remainingConflicts);
        changedFiles=changedFiles==null?List.of():List.copyOf(changedFiles);
        message=message==null?"":message;
    }
}
