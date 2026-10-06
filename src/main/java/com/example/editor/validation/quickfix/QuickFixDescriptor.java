package com.example.editor.validation.quickfix;

import java.util.List;
import com.example.editor.validation.action.ActionParameter;
import com.example.editor.validation.action.ActionTransactionPolicy;
import com.example.editor.validation.action.ActionUndoPolicy;

/** Quick fix resoluble desde un diagnóstico concreto. */
public record QuickFixDescriptor(String actionId, String name, String description, boolean enabled,
        List<ActionParameter> parameters, ActionTransactionPolicy transactionPolicy, ActionUndoPolicy undoPolicy) {
    public QuickFixDescriptor {
        actionId = actionId == null ? "" : actionId;
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
