package com.example.editor.definition.runtime.session;

import com.example.editor.definition.runtime.ExecutableEditorPlan;
import com.example.editor.definition.runtime.ExecutableRepresentationPlan;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/**
 * Puente exclusivo de pruebas para ejercer la proyección de una sesión desde regresiones
 * de integración situadas en otros paquetes sin convertir el ensamblador interno en API pública.
 */
public final class EditorSessionViewAssemblerTestBridge {
    private EditorSessionViewAssemblerTestBridge() {
    }

    public static EditorSessionView assemble(StoredCanonicalDocument<EditorModelSession> stored,
            ExecutableEditorPlan plan, MetaModel metamodel, ExecutableRepresentationPlan representation,
            String selectedElementId, boolean canUndo, boolean canRedo) {
        return new EditorSessionViewAssembler(null).assemble(stored, plan, metamodel, representation,
                selectedElementId, canUndo, canRedo);
    }
}
