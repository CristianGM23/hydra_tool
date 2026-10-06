package com.example.editor.remote.git;

import java.util.List;

/** Small safe projection of a commit for the repository tree UI. */
public record RemoteGitCommitNode(String id,String shortId,List<String> parentIds,String authoredAt,String subject) {
    public RemoteGitCommitNode{
        parentIds=parentIds==null?List.of():List.copyOf(parentIds);
        authoredAt=authoredAt==null?"":authoredAt;
        subject=subject==null?"":subject;
    }
}
