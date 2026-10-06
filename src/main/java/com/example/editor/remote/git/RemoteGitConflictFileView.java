package com.example.editor.remote.git;

/** Side-by-side contents for visual conflict resolution. */
public record RemoteGitConflictFileView(String profileId,String relativePath,String baseContent,String localContent,String remoteContent,
        String resultContent,boolean mergeInProgress) {
    public RemoteGitConflictFileView{
        baseContent=baseContent==null?"":baseContent;
        localContent=localContent==null?"":localContent;
        remoteContent=remoteContent==null?"":remoteContent;
        resultContent=resultContent==null?"":resultContent;
    }
}
