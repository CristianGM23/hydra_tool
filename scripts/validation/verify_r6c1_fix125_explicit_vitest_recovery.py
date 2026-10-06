from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "frontend" / "scripts" / "hydra-test-runner.mjs"
MAIN = ROOT / "scripts" / "validation" / "validate_r6c1_fix118_123.ps1"
RESUME = ROOT / "scripts" / "validation" / "validate_r6c1_fix125.ps1"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL {message}")
    print(f"PASS {message}")

runner = RUNNER.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")
resume = RESUME.read_text(encoding="utf-8")

require("runExplicitWithInfrastructureRecovery" in runner,
        "central HYDRA runner recovers explicit Vitest runs")
require("isWorkerInfrastructureFailure(result.output)" in runner,
        "explicit recovery is gated by proven worker infrastructure failure")
require("This is a product/test failure" in runner,
        "assertion and product failures remain fail-closed")
require("Explicit frontend test run could not start with any recovery pool" in runner,
        "infrastructure exhaustion is reported separately from product failure")
require("HYDRA_TEST_RECOVERY_POOL" in runner and "resolveRecoveryPools" in runner,
        "explicit recovery uses the shared configurable pool policy")

for script, label in ((main, "full R6C1 validator"), (resume, "Fix125 resume validator")):
    require('$env:HYDRA_TEST_POOL = "vmThreads"' in script, f"{label} starts with vmThreads")
    require('$env:HYDRA_TEST_RECOVERY_POOL = "vmForks"' in script, f"{label} keeps an independent recovery pool")
    require('scripts/hydra-test-runner.mjs" full "src/integration-designer/IntegrationDesigner.test.tsx"' in script,
            f"{label} delegates IntegrationDesigner to the central runner")
    require('$env:HYDRA_TEST_FILE_PARALLELISM = "false"' in script and '$env:HYDRA_TEST_MAX_WORKERS = "1"' in script,
            f"{label} keeps execution isolated and single-worker")

require("TechnologyWorkflowServiceTests" not in resume and "TechnologyWorkflowControllerTests" not in resume,
        "Fix125 resume does not repeat backend tests already passed")
for already_passed in (
    "technologyWorkflowNormalization.test.ts",
    "integrationTechnologyRecommendations.test.ts",
    "IntegrationTechnologyJourney.test.tsx",
    "integrationTechnologyNormalization.test.ts",
    "api.test.ts",
):
    require(already_passed not in resume, f"Fix125 resume does not repeat {already_passed}")
require(resume.count("IntegrationDesigner.test.tsx") == 1,
        "Fix125 resume executes only the previously unstarted frontend test file")
require("npm.cmd run build" in resume,
        "Fix125 resume closes with the production frontend build")

print("FIX125_EXPLICIT_VITEST_RECOVERY=PASS")
