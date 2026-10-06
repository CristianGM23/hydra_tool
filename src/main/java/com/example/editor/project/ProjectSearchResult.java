package com.example.editor.project;

import java.util.List;

/** Resultado acotado de una búsqueda de archivos o referencias. */
public record ProjectSearchResult(String query,List<ProjectSearchMatch> matches,boolean truncated) {
    public ProjectSearchResult { matches=matches==null?List.of():List.copyOf(matches); }
}
