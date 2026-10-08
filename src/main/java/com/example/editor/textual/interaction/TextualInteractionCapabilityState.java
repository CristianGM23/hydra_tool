package com.example.editor.textual.interaction;

import java.util.Map;

/** Estado negociado de una capacidad textual. */
public record TextualInteractionCapabilityState(TextualInteractionCapability capability,TextualInteractionExecutionMode mode,boolean available,boolean deterministic,String reason,Map<String,String> metadata){
    public TextualInteractionCapabilityState{reason=reason==null?"":reason;metadata=metadata==null?Map.of():Map.copyOf(metadata);}
}
