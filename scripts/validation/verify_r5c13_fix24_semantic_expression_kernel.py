#!/usr/bin/env python3
from pathlib import Path
import hashlib

ROOT=Path(__file__).resolve().parents[2]
checks=[]
def text(path): return (ROOT/path).read_text(encoding='utf-8')
def exists(path): return (ROOT/path).is_file()
def check(label, condition):
    checks.append((label,bool(condition)))
    print(('PASS' if condition else 'FAIL')+' - '+label)

semantic=ROOT/'src/main/java/com/example/editor/semantic/expression'
legacy=ROOT/'src/main/java/com/example/editor/validation/expression'
transformation=ROOT/'src/main/java/com/example/editor/transformation'
semantic_text='\n'.join(p.read_text(encoding='utf-8') for p in semantic.glob('*.java'))
transformation_text='\n'.join(p.read_text(encoding='utf-8') for p in transformation.rglob('*.java'))

required=['ExpressionNode.java','ExpressionDefinition.java','ExpressionValueType.java','ExpressionTypeChecker.java','ExpressionEvaluator.java','ExpressionDependencyAnalyzer.java','ExpressionSemanticFingerprint.java','ExpressionBatchCheckResult.java']
check('neutral semantic expression kernel exists', semantic.is_dir() and all((semantic/name).is_file() for name in required))
check('kernel owns the polymorphic expression AST', 'public sealed interface ExpressionNode' in text(Path('src/main/java/com/example/editor/semantic/expression/ExpressionNode.java')))
check('kernel owns type checking and evaluation', '@Service\npublic class ExpressionTypeChecker' in text(Path('src/main/java/com/example/editor/semantic/expression/ExpressionTypeChecker.java')) and '@Service\npublic class ExpressionEvaluator' in text(Path('src/main/java/com/example/editor/semantic/expression/ExpressionEvaluator.java')))
check('kernel exposes persistence-neutral batch checking', 'ExpressionBatchCheckResult checkBatch(' in text(Path('src/main/java/com/example/editor/semantic/expression/ExpressionTypeChecker.java')))
check('neutral checker does not depend on ExpressionModel', 'ExpressionModel' not in text(Path('src/main/java/com/example/editor/semantic/expression/ExpressionTypeChecker.java')))
check('semantic kernel does not import validation expression persistence', 'com.example.editor.validation.expression' not in semantic_text)
check('semantic kernel does not import validation identifier policy', 'com.example.editor.validation.contract.StableIdentifierPolicy' not in semantic_text)
check('neutral identifier policy exists', exists(Path('src/main/java/com/example/editor/contract/StableIdentifierPolicy.java')) and '[A-Za-z0-9._:-]' in text(Path('src/main/java/com/example/editor/contract/StableIdentifierPolicy.java')))
check('legacy identifier policy is only a compatibility facade', 'com.example.editor.contract.StableIdentifierPolicy' in text(Path('src/main/java/com/example/editor/validation/contract/StableIdentifierPolicy.java')) and '@Deprecated' in text(Path('src/main/java/com/example/editor/validation/contract/StableIdentifierPolicy.java')))
check('legacy ExpressionModel now stores neutral definitions', 'import com.example.editor.semantic.expression.ExpressionDefinition;' in text(Path('src/main/java/com/example/editor/validation/expression/ExpressionModel.java')))
check('legacy application service adapts the neutral checker', 'ExpressionBatchCheckResult' in text(Path('src/main/java/com/example/editor/validation/expression/ExpressionModelApplicationService.java')) and 'typeChecker.checkBatch(' in text(Path('src/main/java/com/example/editor/validation/expression/ExpressionModelApplicationService.java')))
check('legacy model report exposes neutral diagnostics without duplicating them', 'import com.example.editor.semantic.expression.ExpressionDiagnostic;' in text(Path('src/main/java/com/example/editor/validation/expression/ExpressionModelCheckReport.java')))
check('public expression REST route remains unchanged', '@RequestMapping("/api/canonical-metamodels")' in text(Path('src/main/java/com/example/editor/validation/expression/web/CanonicalExpressionController.java')) and '@PostMapping("/{metamodelId}/expressions")' in text(Path('src/main/java/com/example/editor/validation/expression/web/CanonicalExpressionController.java')))
schema=ROOT/'src/main/resources/schemas/validation/expression-model-1.0.schema.json'
schema_hash=hashlib.sha256(schema.read_bytes()).hexdigest() if schema.is_file() else ''
check('expression JSON schema is byte-compatible with Fix23', schema_hash=='61ffae9b0acad7be5fb0f9d7913b64f11b0a0de496218c1fd961ce864159af15')
check('transformations consume the neutral kernel directly', 'com.example.editor.semantic.expression' in transformation_text and 'com.example.editor.validation.expression' not in transformation_text)
allowed_legacy_top_level={
    'ExpressionModel.java',
    'ExpressionModelApplicationException.java',
    'ExpressionModelApplicationService.java',
    'ExpressionModelCheckReport.java',
    'ExpressionModelLoadResult.java',
    'ExpressionModelProperties.java',
    'ExpressionModelSaveResponse.java',
    'ExpressionModelSummary.java',
}
legacy_top_level={p.name for p in legacy.glob('*.java')}
check('old validation package no longer owns any semantic kernel classes', legacy_top_level == allowed_legacy_top_level)
legacy_test_dir=ROOT/'src/test/java/com/example/editor/validation/expression'
check('old validation expression kernel tests are physically absent', not legacy_test_dir.exists() or not any(legacy_test_dir.glob('*.java')))
check('semantic expression unit tests moved with the kernel', exists(Path('src/test/java/com/example/editor/semantic/expression/ExpressionTypeCheckerTests.java')) and exists(Path('src/test/java/com/example/editor/semantic/expression/ExpressionEvaluatorTests.java')))
check('historical test fixture imports are migrated', 'com.example.editor.validation.expression.ExpressionTestFixtures' not in '\n'.join(p.read_text(encoding='utf-8') for p in (ROOT/'src/test/java').rglob('*.java')))
check('operation regression gate follows the neutral package', 'semantic/expression/ExpressionTypeChecker.java' in text(Path('scripts/validation/verify_operations_o3_o5.py')))
check('block1 regression gate follows the neutral services', 'SEMANTIC = ROOT / "src/main/java/com/example/editor/semantic/expression"' in text(Path('scripts/validation/verify_block1_expressions.py')))
check('architecture documentation records the completed extraction', 'Desde R5C13 Fix24' in text(Path('docs/architecture/transformation-layer.md')) and '`semantic.expression`' in text(Path('docs/architecture/transformation-layer.md')))
check('release notes record Fix24 compatibility boundary', 'R5C13 Fix 24 — kernel semántico neutral de expresiones' in text(Path('HYDRA_V4.32.107_RELEASE_NOTES.md')))
check('semantic kernel stays inside shared logical line budget', all(max(map(len,p.read_text(encoding='utf-8').splitlines()),default=0)<=1400 for p in semantic.glob('*.java')))

failed=[label for label,ok in checks if not ok]
print(f'SEMANTIC_EXPRESSION_KERNEL_FIX24={len(checks)-len(failed)}/{len(checks)} '+('PASS' if not failed else 'FAIL'))
if failed: raise SystemExit(1)
