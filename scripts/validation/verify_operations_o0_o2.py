#!/usr/bin/env python3
"""Auditoría reproducible de los bloques intermedios O0, O1 y O2."""
from __future__ import annotations
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks: list[tuple[str, bool]] = []

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def check(label: str, condition: bool) -> None:
    checks.append((label, bool(condition)))

# O0: contrato neutral y versión.
meta_model = read("src/main/java/com/example/editor/metamodel/core/MetaModel.java")
meta_class = read("src/main/java/com/example/editor/metamodel/core/MetaClass.java")
meta_operation = read("src/main/java/com/example/editor/metamodel/core/MetaOperation.java")
meta_parameter = read("src/main/java/com/example/editor/metamodel/core/MetaParameter.java")
behavior = read("src/main/java/com/example/editor/metamodel/core/OperationBehaviorModel.java")
validator = read("src/main/java/com/example/editor/metamodel/validation/NeutralMetamodelValidator.java")
check("MetaModel 2.1", 'CURRENT_SCHEMA_VERSION = "2.1"' in meta_model)
check("MetaClass contiene operations", "List<MetaOperation> operations" in meta_class)
check("MetaClass exige operations explícitas", "List<MetaOperation> operations" in meta_class and "Constructor temporal" not in meta_class)
check("MetaOperation neutral", all(value in meta_operation for value in ("returnType", "returnMultiplicity", "parameters", "exceptionTypes", "behaviorKind")))
check("MetaParameter neutral", all(value in meta_parameter for value in ("MetaTypeReference type", "Multiplicity multiplicity", "ordered", "unique")))
check("QUERY COMMAND UNSPECIFIED", all(value in read("src/main/java/com/example/editor/metamodel/core/MetaOperationBehaviorKind.java") for value in ("QUERY", "COMMAND", "UNSPECIFIED")))
check("Behavior model separado", all(value in behavior for value in ("OperationBinding", "metamodelId", "bindings")))
check("Validador de operaciones", "validateOperations" in validator and "OPERATION_SIGNATURE_DUPLICATE" in validator and "exceptionTypes" in validator)
check("Núcleo sin EMF", not re.search(r"org\.eclipse\.emf|EOperation|EClass", "\n".join((meta_model, meta_class, meta_operation, meta_parameter, behavior))))

# Esquemas y ejemplos.
for schema_path in (
    "src/main/resources/schemas/metamodel/meta-model-2.1.schema.json",
    "src/main/resources/schemas/metamodel/operation-behavior-model-1.0.schema.json",
):
    data=json.loads(read(schema_path))
    check(f"JSON válido {schema_path}", isinstance(data, dict) and "$schema" in data)
example=json.loads(read("src/test/resources/validation/contracts/examples/library.metamodel.json"))
bindings=json.loads(read("src/test/resources/validation/contracts/examples/library.operation-behaviors.json"))
check("Ejemplo 2.1 contiene operations", example.get("schemaVersion") == "2.1" and any(c.get("operations") for c in example.get("classes", [])))
operation_ids={o["id"] for c in example["classes"] for o in c.get("operations", [])}
check("Bindings apuntan a operaciones existentes", all(b["operationId"] in operation_ids for b in bindings.get("bindings", [])))

# O1: diseñador y representación visual.
types=read("frontend/src/metamodel-designer/types.ts")
factory=read("frontend/src/metamodel-designer/metaModelFactory.ts")
selectors=read("frontend/src/metamodel-designer/metaModelSelectors.ts")
operations=read("frontend/src/metamodel-designer/metaModelOperations.ts")
panel=read("frontend/src/metamodel-designer/MetamodelPropertyPanel.tsx")
node=read("frontend/src/metamodel-designer/MetamodelClassNode.tsx")
diagram=read("frontend/src/metamodel-designer/diagram.ts")
check("Tipos TypeScript de operación", all(value in types for value in ("MetaOperation", "MetaParameter", "behaviorKind", "operations:")))
check("Selecciones OPERATION y PARAMETER", "'OPERATION'" in types and "'PARAMETER'" in types)
check("Fábricas de operaciones y parámetros", "createMetaOperation" in factory and "createMetaParameter" in factory)
check("Selectores de operaciones y parámetros", "findMetaOperation" in selectors and "findMetaParameter" in selectors)
check("Actualización de operaciones y parámetros", "updateOperation" in operations and "updateParameter" in operations)
check("Borrado limpia operaciones dependientes", "operations:" in operations and "removed" in operations)
check("Editor permite operaciones", "Operations" in panel and "Add operation" in panel)
check("Editor permite parámetros", "Add parameter" in panel and "Parameters" in panel)
check("Editor permite excepciones", "Exception types" in panel and "Add exception" in panel)
check("Interfaces conservan atributos", "Attributes" in panel and "MetaClassKind" in types)
check("Compartimento atributos propios", "metaClass.attributes.map" in node)
check("Compartimento referencias derivadas", "data.relations.map" in node and "No references" in node)
check("Compartimento operaciones", "metaClass.operations.map" in node and "metamodel-operation-compartment" in node)
check("Altura suma atributos referencias operaciones", "classDesign.attributes.length" in diagram and "relationCount" in diagram and "classDesign.operations.length" in diagram)

# O2: Ecore, formatos y código.
ecore_writer=read("src/main/java/com/example/editor/metamodel/adapter/ecore/EcoreProjectionWriter.java")
ecore_reader=read("src/main/java/com/example/editor/metamodel/adapter/ecore/EcoreProjectionReader.java")
metadata=read("src/main/java/com/example/editor/metamodel/adapter/ecore/EcoreNeutralMetadataSupport.java")
check("Ecore exporta EOperation", "createEOperation" in ecore_writer and "getEOperations().add" in ecore_writer)
check("Ecore exporta EParameter", "createEParameter" in ecore_writer and "getEParameters().add" in ecore_writer)
check("Ecore importa operaciones", "List<MetaOperation> operations" in ecore_reader and "new MetaOperation" in ecore_reader)
check("Ecore conserva behaviorKind", "OPERATION_BEHAVIOR_KIND_KEY" in ecore_writer and "OPERATION_BEHAVIOR_KIND_KEY" in ecore_reader)
check("Ecore conserva excepciones externas", "OPERATION_EXTERNAL_EXCEPTION_TYPES_KEY" in ecore_writer and "OPERATION_EXTERNAL_EXCEPTION_TYPES_KEY" in ecore_reader and "writeExternalTypeList" in metadata)
for path, tokens in {
    "src/main/java/com/example/editor/metamodel/export/JavaMetamodelExporter.java": ("operation", "UnsupportedOperationException"),
    "src/main/java/com/example/editor/metamodel/export/PythonMetamodelExporter.java": ("operation", "NotImplementedError"),
    "src/main/java/com/example/editor/metamodel/export/PlantUmlMetamodelExporter.java": ("operations", "parameters"),
    "src/main/java/com/example/editor/metamodel/export/NeutralXmlMetamodelExporter.java": ("operation", "parameter"),
    "src/main/java/com/example/editor/metamodel/export/DrawioMetamodelExporter.java": ("operation", "parameter"),
    "src/main/java/com/example/editor/metamodel/export/SvgMetamodelExporter.java": ("operations", "structuralRelations"),
    "src/main/java/com/example/editor/metamodel/export/PngMetamodelExporter.java": ("operations", "structuralRelations"),
}.items():
    text=read(path)
    check(f"Soporte de operaciones en {Path(path).name}", all(token in text for token in tokens))
for path in (
    "src/main/java/com/example/editor/metamodel/importing/importers/NeutralXmlMetamodelImporter.java",
    "src/main/java/com/example/editor/metamodel/importing/importers/PlantUmlMetamodelImporter.java",
    "src/main/java/com/example/editor/metamodel/importing/importers/DrawioMetamodelImporter.java",
):
    check(f"Importador de operaciones {Path(path).name}", "Operation" in read(path) and "Parameter" in read(path))

# Pruebas de regresión clave.
front_node_test=read("frontend/src/metamodel-designer/MetamodelClassNode.operations.test.tsx")
check("Test conserva atributo de relación", "orders : Order" in front_node_test)
check("Test muestra atributo propio y operación", "name : String" in front_node_test and "displayName" in front_node_test)
check("Test Ecore de operaciones", (ROOT / "src/test/java/com/example/editor/metamodel/adapter/ecore/EcoreOperationRoundTripTests.java").is_file())
check("Test exportaciones", (ROOT / "src/test/java/com/example/editor/metamodel/export/OperationExportTests.java").is_file())
check("Sin tests de migración interna pre-GA", not (ROOT / "src/test/java/com/example/editor/metamodel/migration").exists())

failed=[label for label, ok in checks if not ok]
for label, ok in checks:
    print(f"[{'OK' if ok else 'FAIL'}] {label}")
print(f"RESULTADO: {len(checks)-len(failed)}/{len(checks)} comprobaciones correctas")
if failed:
    print("Fallos:", *failed, sep="\n- ")
    sys.exit(1)
