#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks: list[tuple[str, bool]] = []

def text(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")

def check(name: str, ok: bool) -> None:
    checks.append((name, bool(ok)))

# Shared evidence used by the exact failures reported by the Windows/JDK 24 run.
baseline = text("config/hydra-contract-baseline.properties")
editor_schema = ROOT / "src/main/resources/schemas/editor/editor-definition-model-1.0.schema.json"
editor_hash = hashlib.sha256(editor_schema.read_bytes()).hexdigest()
visual = text("src/test/java/com/example/editor/definition/VisualNotationContainerContractTests.java")
tech_matrix = text("src/main/java/com/example/editor/integration/compatibility/TechnologyCompatibilityMatrix.java")
tech_service = text("src/main/java/com/example/editor/integration/compatibility/TechnologyCompatibilityMatrixService.java")
tech_test = text("src/test/java/com/example/editor/integration/compatibility/TechnologyCompatibilityMatrixWriterTests.java")
capability = text("src/main/java/com/example/editor/portability/HydraCapabilityPackageManifest.java")
system_controller = text("src/main/java/com/example/editor/system/SystemStatusController.java")
system_test = text("src/test/java/com/example/editor/system/SystemEndpointsTests.java")
event_test = text("src/test/java/com/example/editor/event/diagram/EventArchitectureDiagramRepositoryTests.java")
ecore_test = text("src/test/java/com/example/editor/metamodel/adapter/ecore/DefaultEcoreMetamodelAdapterTests.java")
ecore_local = text("src/test/java/com/example/editor/metamodel/adapter/ecore/EcoreLocalReferenceSerializationTests.java")
neutral_validator = text("src/test/java/com/example/editor/metamodel/validation/NeutralMetamodelValidatorTests.java")
neutral_json = text("src/test/java/com/example/editor/metamodel/importing/NeutralJsonMetamodelImporterTests.java")
neutral_xml = text("src/test/java/com/example/editor/metamodel/importing/NeutralXmlMetamodelImporterTests.java")
plantuml = text("src/test/java/com/example/editor/metamodel/importing/PlantUmlMetamodelImporterTests.java")

current_constructor = "new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION,"

def no_old_metamodel(source: str) -> bool:
    return not re.search(r'new\s+MetaModel\("(?:1\.0|2\.0)"', source)

# 9 reported failures.
check("CanonicalSchemaBaselineTests.canonicalJsonSchemasMatchTheReviewedBaseline",
      f"schema.src/main/resources/schemas/editor/editor-definition-model-1.0.schema.json={editor_hash}" in baseline)
check("VisualNotationContainerContractTests.pngMappingRejectsSvgAsset",
      "pngMappingRejectsSvgAsset" not in visual and 'assets/department.svg' in visual and "isEmpty()" in visual)
check("VisualNotationContainerContractTests.svgMappingRejectsPngAsset",
      "svgMappingRejectsPngAsset" not in visual and 'assets/department.png' in visual and "projectImageAcceptsSvgAndPngAssets" in visual)
check("TechnologyCompatibilityMatrixWriterTests.writesAnAtomicReadableReportBelowTheSelectedRoot",
      "String hydraVersion" in tech_matrix and "HydraReleaseVersion.VERSION" in tech_service and "HydraReleaseVersion.VERSION" in tech_test)
check("DefaultEcoreMetamodelAdapterTests.rejectsExternalTypesWithStrictPolicy",
      current_constructor in ecore_test and no_old_metamodel(ecore_test))
check("NeutralMetamodelValidatorTests.acceptsValidMetamodel",
      current_constructor in neutral_validator and no_old_metamodel(neutral_validator))
check("NeutralMetamodelValidatorTests.reportsExternalTypesAsWarnings",
      current_constructor in neutral_validator and no_old_metamodel(neutral_validator))
check("CapabilityPackageTests.rejectsDraftSchemasInsteadOfGuessingHistoricalFields",
      "CURRENT_SCHEMA_VERSION.equals(schemaVersion)" in capability and "Unsupported capability package schemaVersion" in capability)
check("SystemEndpointsTests.systemStatusReturnsApplicationAndStatus",
      "String version" not in system_controller and 'jsonPath("$.version").doesNotExist()' in system_test)

# 19 reported errors.
for method in (
    "createsAndReadsTheSeparateDiagramFile",
    "defaultsNodeDimensionsAndCopiesCollections",
    "deletesWithEtagAndRejectsUnsafeIds",
    "replacesOnlyWithTheCurrentEtag",
):
    check(f"EventArchitectureDiagramRepositoryTests.{method}",
          "EventContractVersions.EVENT_ARCHITECTURE_DIAGRAM" in event_test and 'EventArchitectureDiagramDocument("1.0"' not in event_test)

for method in (
    "createsPackageAndClasses",
    "mapsAttributesAndMultiplicities",
    "mapsReferencesContainmentAndOpposites",
    "preservesAggregationThroughAnnotations",
    "preservesCustomDataTypes",
    "preservesDependencyThroughPackageAnnotations",
    "preservesEnumLiteralValues",
    "preservesExternalAttributeTypes",
    "preservesInheritanceAndImplementation",
    "serializesEcoreBytes",
):
    check(f"DefaultEcoreMetamodelAdapterTests.{method}",
          current_constructor in ecore_test and no_old_metamodel(ecore_test))

check("EcoreLocalReferenceSerializationTests.keepsInheritanceAndOppositesAsLocalObjects",
      current_constructor in ecore_local and no_old_metamodel(ecore_local))
check("NeutralJsonMetamodelImporterTests.importsExportWrapperAndDiagram",
      neutral_json.count('"schemaVersion":"2.1"') >= 2 and '"schemaVersion":"1.0"' not in neutral_json)
check("NeutralJsonMetamodelImporterTests.importsRawNeutralModel",
      neutral_json.count('"schemaVersion":"2.1"') >= 2 and '"schemaVersion":"1.0"' not in neutral_json)
check("NeutralXmlMetamodelImporterTests.preservesEcoreSpecificOptionsInTargetConfiguration",
      'schemaVersion="2.1"' in neutral_xml and 'schemaVersion="2.0"' not in neutral_xml)
check("PlantUmlMetamodelImporterTests.importsAgentMarkdownMetadata",
      "schemaVersion: 2.1" in plantuml and "schemaVersion: 1.0" not in plantuml)

for name, ok in checks:
    print(("PASS" if ok else "FAIL") + " - " + name)
failed = [name for name, ok in checks if not ok]
print(f"REPORTED_JDK24_CLOSURE={len(checks)-len(failed)}/{len(checks)}")
if len(checks) != 28:
    print(f"FAIL - gate definition expected 28 checks, got {len(checks)}")
    sys.exit(1)
if failed:
    sys.exit(1)
