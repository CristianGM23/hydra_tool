package com.example.editor.remote.git;

import java.util.List;

/** Group of local or remote branches for the repository-tree presentation. */
public record RemoteGitBranchGroup(String id,String label,String description,List<RemoteGitBranchNode> branches) {
    public RemoteGitBranchGroup{
        description=description==null?"":description;
        branches=branches==null?List.of():List.copyOf(branches);
    }
}
