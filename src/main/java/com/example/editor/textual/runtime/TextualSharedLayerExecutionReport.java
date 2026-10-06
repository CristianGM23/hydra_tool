package com.example.editor.textual.runtime;
import java.util.List;import java.util.Map;
/** Evidence that a textual semantic commit reused HYDRA's shared semantic layers. */
public record TextualSharedLayerExecutionReport(boolean validationExecuted,boolean validationSuccessful,int validationDiagnostics,int eventsPublished,List<String>warnings,Map<String,String>metadata){public TextualSharedLayerExecutionReport{warnings=warnings==null?List.of():List.copyOf(warnings);metadata=metadata==null?Map.of():Map.copyOf(metadata);}}
