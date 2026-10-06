package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Candidate root inside a Git repository that looks like an Eclipse update site. */
public record UpdateSiteDetectedRoot(String path,int score,List<String> markers,boolean recommended) {
    public UpdateSiteDetectedRoot {
        path = path == null ? "" : path.replace('\\','/');
        score = Math.max(0, score);
        markers = markers == null ? List.of() : List.copyOf(markers);
    }
}
