package com.example.editor.operation.runtime;
import java.util.List;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.OperationImplementationKind;
/** Describe una operación y todas las clases sobre las que puede mostrarse en el editor. */
public record OperationRuntimeDescriptor(String operationId, String ownerClassId, String ownerClassName,
        List<String> applicableClassIds, List<String> applicableClassNames, String name,
        MetaOperationBehaviorKind behaviorKind, List<OperationRuntimeParameter> parameters, String returnTypeLabel,
        OperationImplementationKind implementationKind, boolean enabled, List<OperationSurface> surfaces) {
    public OperationRuntimeDescriptor {
        applicableClassIds = applicableClassIds == null ? List.of() : List.copyOf(applicableClassIds);
        applicableClassNames = applicableClassNames == null ? List.of() : List.copyOf(applicableClassNames);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        surfaces = surfaces == null ? List.of() : List.copyOf(surfaces);
    }
}
