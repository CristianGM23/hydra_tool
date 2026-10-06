package com.example.editor.definition.runtime.session;

import java.nio.file.Path;

/** Copia de trabajo no confirmada conservada en memoria hasta guardar o descartar. */
record PendingEditorSession(EditorModelSession document,String baseEtag,String etag,Path path) { }
