package com.example.editor.remote.git;

import java.util.List;

/** Safe structural view of the active repository and its branches. */
public record RemoteGitRepositoryTree(String profileId,String repositoryRoot,boolean initialized,String currentBranch,boolean mergeInProgress,
        List<RemoteGitBranchGroup> groups) {
    public RemoteGitRepositoryTree{
        repositoryRoot=repositoryRoot==null?"":repositoryRoot;
        currentBranch=currentBranch==null?"":currentBranch;
        groups=groups==null?List.of():List.copyOf(groups);
    }
}
