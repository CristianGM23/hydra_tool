package com.example.editor.textual.interaction;

import java.util.List;
import java.util.Map;

/** Resultado fail-closed de compatibilidad/migración de snapshot gramatical. */
public record TextualSnapshotCompatibilityReport(boolean reusable,boolean reparseRequired,boolean migrationRequired,List<String> reasons,Map<String,String> metadata){
    public TextualSnapshotCompatibilityReport{reasons=reasons==null?List.of():List.copyOf(reasons);metadata=metadata==null?Map.of():Map.copyOf(metadata);}
}
