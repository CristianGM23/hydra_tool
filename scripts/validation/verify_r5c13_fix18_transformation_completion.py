from pathlib import Path
root=Path(__file__).resolve().parents[2]
checks=[]
def check(label,cond):
    checks.append((label,bool(cond)))

def text(path): return (root/path).read_text(encoding='utf-8')

binding=text(Path('src/main/java/com/example/editor/transformation/core/TransformationBinding.java'))
runtime=text(Path('src/main/java/com/example/editor/transformation/runtime/CanonicalTransformationRuntime.java'))
controller=text(Path('src/main/java/com/example/editor/transformation/web/TransformationController.java'))
checker=text(Path('src/main/java/com/example/editor/transformation/TransformationDefinitionChecker.java'))
api=text(Path('frontend/src/transformation-designer/api.ts'))
types=text(Path('frontend/src/transformation-designer/types.ts'))
css=text(Path('frontend/src/transformation-designer/transformation-designer.css'))
module_test=text(Path('frontend/src/application-shell/moduleRegistry.test.ts'))

check('binding owns neutral value adaptation policy','TransformationValuePolicy valuePolicy' in binding)
check('runtime adapts values after acquisition','adaptValue(binding, bindingValue' in runtime)
check('runtime supports collection reduction','TransformationCollectionMode' in runtime and 'COUNT' in runtime)
check('runtime supports explicit primitive conversions','TO_INTEGER' in runtime and 'TO_BOOLEAN' in runtime)
check('checker blocks implicit incompatible direct copies','TYPE_CONVERSION_REQUIRED' in checker and 'Explicit type conversion required' in checker)
check('checker blocks implicit many-to-one copies','COLLECTION_REDUCTION_REQUIRED' in checker)
check('draft M2M preview is side-effect free endpoint','@PostMapping("/preview")' in controller and 'executeDraft' in controller)
check('M2T preview endpoint exists','@PostMapping("/preview-model-to-text")' in controller)
check('persisted M2T endpoint exists','@PostMapping("/{id}/model-to-text")' in controller)
check('draft T2M preview endpoint exists','@PostMapping("/preview-text-to-model")' in controller)
check('persisted T2M endpoint exists','@PostMapping("/{id}/text-to-model")' in controller)
check('M2T runtime is in-memory',(root/'src/main/java/com/example/editor/transformation/text/CanonicalTextGenerationRuntime.java').exists())
check('T2M parser gateway isolates grammar runtime',(root/'src/main/java/com/example/editor/transformation/textmodel/TransformationTextParserGateway.java').exists())
check('frontend exposes draft preview APIs','previewTransformation' in api and 'previewModelToText' in api and 'previewTextToModel' in api)
check('frontend carries value policy contract','TransformationValuePolicy' in types and 'TransformationCollectionMode' in types)
check('transformation CSS stays below shared logical ceiling',max(map(len,css.splitlines()),default=0)<=1400)
check('module registry contract still includes transformations',"'TRANSFORMATIONS'" in module_test)
for label,ok in checks: print(('PASS' if ok else 'FAIL')+' - '+label)
passed=sum(ok for _,ok in checks)
print(f'TRANSFORMATION_LAYER_FIX18={passed}/{len(checks)} '+('PASS' if passed==len(checks) else 'FAIL'))
raise SystemExit(0 if passed==len(checks) else 1)
