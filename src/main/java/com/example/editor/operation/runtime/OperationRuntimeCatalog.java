package com.example.editor.operation.runtime;
import java.util.List;
public record OperationRuntimeCatalog(String metamodelId, List<OperationRuntimeDescriptor> operations) { public OperationRuntimeCatalog { operations = operations == null ? List.of() : List.copyOf(operations); } }
