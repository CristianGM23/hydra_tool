package com.example.editor.event.flow;

/** Semántica neutral de una transición entre nodos. */
public enum FlowTransitionKind { NEXT, ON_SUCCESS, ON_ERROR, COMPENSATES, TIMEOUT, TRUE, FALSE }
