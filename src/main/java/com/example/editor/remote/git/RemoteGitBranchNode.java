package com.example.editor.remote.git;

import java.util.List;

/** Branch plus a bounded commit slice so HYDRA can render a repository tree. */
public record RemoteGitBranchNode(String name,String fullName,boolean local,boolean remote,boolean current,String upstream,int ahead,int behind,
        String lastCommitShortId,String lastCommitAt,String lastCommitSubject,List<RemoteGitCommitNode> commits) {
    public RemoteGitBranchNode{
        upstream=upstream==null?"":upstream;
        lastCommitShortId=lastCommitShortId==null?"":lastCommitShortId;
        lastCommitAt=lastCommitAt==null?"":lastCommitAt;
        lastCommitSubject=lastCommitSubject==null?"":lastCommitSubject;
        commits=commits==null?List.of():List.copyOf(commits);
    }
}
