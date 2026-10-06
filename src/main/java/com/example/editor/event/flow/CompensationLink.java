package com.example.editor.event.flow;

import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Relación explícita entre un paso compensable y su compensación. */
public record CompensationLink(String id, String sourceNodeId, String compensationNodeId) {
    public CompensationLink {
        id = StableIdentifierPolicy.requireElementId(id, "compensationLink.id");
        sourceNodeId = StableIdentifierPolicy.requireElementId(sourceNodeId, "compensationLink.sourceNodeId");
        compensationNodeId = StableIdentifierPolicy.requireElementId(compensationNodeId, "compensationLink.compensationNodeId");
    }
}
