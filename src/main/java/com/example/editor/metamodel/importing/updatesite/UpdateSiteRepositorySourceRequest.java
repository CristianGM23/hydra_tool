package com.example.editor.metamodel.importing.updatesite;

/** Request for importing an update-site candidate directly from a Git repository URL. */
public record UpdateSiteRepositorySourceRequest(String repositoryUrl,String reference,String subdirectory,String profileId,
        UpdateSiteImportOptions options) {
    public UpdateSiteRepositorySourceRequest{
        repositoryUrl=repositoryUrl==null?"":repositoryUrl.trim();
        reference=reference==null?"":reference.trim();
        subdirectory=subdirectory==null?"":subdirectory.replace('\\','/').trim();
        profileId=profileId==null?"":profileId.trim();
        options=options==null?UpdateSiteImportOptions.defaults():options;
    }
}
