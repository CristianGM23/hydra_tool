#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks: list[tuple[str, bool]] = []

def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")

def require(name: str, condition: bool) -> None:
    checks.append((name, condition))

service = read("src/main/java/com/example/editor/integration/workflow/TechnologyWorkflowService.java")
controller = read("src/main/java/com/example/editor/integration/workflow/TechnologyWorkflowController.java")
adapter_registry = read("src/main/java/com/example/editor/integration/compiler/TechnologyAdapterRegistry.java")
validator_registry = read("src/main/java/com/example/editor/integration/verification/TechnologyProjectStructureValidatorRegistry.java")
types = read("frontend/src/integration-designer/types.ts")
normalizer = read("frontend/src/integration-designer/technologyWorkflowNormalization.ts")
designer = read("frontend/src/integration-designer/IntegrationDesigner.tsx")
recommendations = read("frontend/src/integration-designer/integrationTechnologyRecommendations.ts")
api = read("frontend/src/integration-designer/api.ts")
spanish = read("frontend/src/i18n/currentSpanishCatalogPart18.ts")
properties = read("src/main/resources/application.properties")

for relative in [
    "src/main/java/com/example/editor/integration/workflow/TechnologyWorkflowStage.java",
    "src/main/java/com/example/editor/integration/workflow/TechnologyWorkflowAction.java",
    "src/main/java/com/example/editor/integration/workflow/TechnologyWorkflowMaturity.java",
    "src/main/java/com/example/editor/integration/workflow/TechnologyWorkflowDescriptor.java",
    "src/main/java/com/example/editor/integration/workflow/TechnologyWorkflowStageDescriptor.java",
    "src/main/java/com/example/editor/integration/workflow/TechnologyWorkflowProperties.java",
    "src/main/java/com/example/editor/integration/workflow/TechnologyWorkflowService.java",
    "src/main/java/com/example/editor/integration/workflow/TechnologyWorkflowController.java",
    "frontend/src/integration-designer/IntegrationTechnologyJourney.tsx",
    "frontend/src/integration-designer/technologyWorkflowNormalization.ts",
    "frontend/src/integration-designer/integrationTechnologyRecommendations.ts",
    "docs/integration/TECHNOLOGY_WORKFLOW_PARITY.md",
]:
    require(f"exists:{relative}", (ROOT / relative).is_file())

require("adapter-registry-safe-support-query", "boolean supports(IntegrationTechnology technology)" in adapter_registry)
require("validator-registry-safe-support-query", "boolean supports(IntegrationTechnology technology)" in validator_registry)
require("workflow-uses-real-adapter-registry", "adapters.supports(technology)" in service)
require("workflow-uses-real-validator-registry", "validators.supports(technology)" in service)
require("workflow-native-execution-explicitly-external", "IntegrationSupportLevel.EXTERNAL" in service and "RUN_NATIVE_HOST" in service)
require("workflow-read-only-api", 'integrations/technologies/workflows' in controller and "@GetMapping" in controller and "@PostMapping" not in controller)
require("frontend-antlr-parity", "|'ANTLR'|" in types and "'ANTLR'" in normalizer)
require("frontend-loads-workflow-api", "listIntegrationTechnologyWorkflows" in api and "/technologies/workflows" in api)
require("designer-renders-journey", "<IntegrationTechnologyJourney workflow={technologyWorkflow}" in designer)
require("designer-blocks-declared-only", "technologyWorkflow.maturity === 'DECLARED_ONLY'" in designer)
require("designer-does-not-expose-unsupported-compile", "canCompileTechnology" in designer and "generationStage.action === 'COMPILE_ARCHIVE'" in designer)
require("designer-handles-projection-delivery-separately", "projectionGeneration" in designer and "Generate projection" in designer)
require("recommendations-full-only", "==='FULL'" in recommendations and "PARTIAL" not in recommendations)
require("workflow-weights-configurable", "dsl.integrations.workflow.generate-weight=" in properties and "dsl.integrations.workflow.full-workflow-threshold=" in properties)
require("spanish-workflow-catalog", "Technology workflow maturity" in spanish and "Solo declarado" in spanish)
require("backend-service-tests", (ROOT / "src/test/java/com/example/editor/integration/workflow/TechnologyWorkflowServiceTests.java").is_file())
require("backend-controller-tests", (ROOT / "src/test/java/com/example/editor/integration/workflow/TechnologyWorkflowControllerTests.java").is_file())
require("frontend-normalizer-tests", (ROOT / "frontend/src/integration-designer/technologyWorkflowNormalization.test.ts").is_file())
require("frontend-recommendation-tests", (ROOT / "frontend/src/integration-designer/integrationTechnologyRecommendations.test.ts").is_file())
require("frontend-journey-tests", (ROOT / "frontend/src/integration-designer/IntegrationTechnologyJourney.test.tsx").is_file())

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    print(f"R6C1_FIX118_123_TECHNOLOGY_WORKFLOW_PARITY={len(checks)-len(failed)}/{len(checks)} FAIL")
    raise SystemExit(1)
print(f"R6C1_FIX118_123_TECHNOLOGY_WORKFLOW_PARITY={len(checks)}/{len(checks)} PASS")
