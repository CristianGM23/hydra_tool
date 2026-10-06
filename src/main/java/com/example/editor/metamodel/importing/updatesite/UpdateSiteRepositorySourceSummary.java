package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Stable summary of a captured Git repository source. */
public record UpdateSiteRepositorySourceSummary(String snapshotId,String repositoryUrl,String provider,String reference,
        String selectedSubdirectory,int fileCount,long totalBytes,List<String> skippedPaths,List<String> diagnostics,
        List<UpdateSiteDetectedRoot> detectedRoots) {
    public UpdateSiteRepositorySourceSummary {
        snapshotId=text(snapshotId);repositoryUrl=text(repositoryUrl);provider=text(provider);reference=text(reference);
        selectedSubdirectory=text(selectedSubdirectory);fileCount=Math.max(0,fileCount);totalBytes=Math.max(0,totalBytes);
        skippedPaths=skippedPaths==null?List.of():List.copyOf(skippedPaths);
        diagnostics=diagnostics==null?List.of():List.copyOf(diagnostics);
        detectedRoots=detectedRoots==null?List.of():List.copyOf(detectedRoots);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteRepositorySourceSummary. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
