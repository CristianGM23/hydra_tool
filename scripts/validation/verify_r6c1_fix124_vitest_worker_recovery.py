from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "scripts" / "validation" / "validate_r6c1_fix118_123.ps1"
RESUME = ROOT / "scripts" / "validation" / "validate_r6c1_fix124.ps1"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL {message}")
    print(f"PASS {message}")


main = MAIN.read_text(encoding="utf-8")
resume = RESUME.read_text(encoding="utf-8")

require('r6c1-frontend-targeted-core' in main, "frontend targeted core is isolated")
require(main.count('src/integration-designer/IntegrationDesigner.test.tsx') == 1,
        "IntegrationDesigner is executed exactly once in the full R6C1 validator")
require('r6c1-frontend-integration-designer-recoverable' in main,
        "IntegrationDesigner has a dedicated recoverable validation stage")
require('$env:HYDRA_TEST_POOL = "vmThreads"' in main,
        "dedicated stage starts with vmThreads after native worker failures")
require('$env:HYDRA_TEST_FILE_PARALLELISM = "false"' in main,
        "dedicated stage disables file parallelism")
require('$env:HYDRA_TEST_MAX_WORKERS = "1"' in main,
        "dedicated stage uses one worker")
require('$env:HYDRA_TEST_TIMEOUT_MS = "90000"' in main,
        "dedicated stage grants bounded test timeout")
require('finally {' in main and 'Remove-Item Env:HYDRA_TEST_POOL' in main,
        "temporary Vitest environment is restored")

require('TechnologyWorkflowServiceTests' not in resume and 'TechnologyWorkflowControllerTests' not in resume,
        "Fix124 resume validator does not repeat backend tests already passed")
for already_passed in (
    'technologyWorkflowNormalization.test.ts',
    'integrationTechnologyRecommendations.test.ts',
    'IntegrationTechnologyJourney.test.tsx',
    'integrationTechnologyNormalization.test.ts',
    'api.test.ts',
):
    require(already_passed not in resume, f"Fix124 resume does not repeat {already_passed}")
require(resume.count('src/integration-designer/IntegrationDesigner.test.tsx') == 1,
        "Fix124 resume executes only the previously unstarted test file")
require('$env:HYDRA_TEST_POOL = "vmThreads"' in resume,
        "Fix124 resume delegates to the generalized recovery starting with vmThreads")
require('npm.cmd run build' in resume,
        "Fix124 resume completes with the frontend production build")

print("FIX124_VITEST_WORKER_RECOVERY=PASS")
