package com.example.editor.event;
import com.example.editor.validation.contract.StableIdentifierPolicy;
/** Actor opcional de un evento. */
public record EventActor(String id, EventActorKind kind, String displayName) {
    public EventActor {
        id = StableIdentifierPolicy.requireElementId(id, "actor.id");
        if(kind == null) kind = EventActorKind.SYSTEM;
        displayName = displayName == null ? "" : displayName.trim();
    }
}
