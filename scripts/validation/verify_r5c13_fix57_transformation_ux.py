from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name,cond):
    if not cond: raise SystemExit('FAIL - '+name)
    print('PASS - '+name);checks.append(name)
preview=(ROOT/'frontend/src/transformation-designer/TransformationPreviewPanel.tsx').read_text()
provision=(ROOT/'frontend/src/transformation-designer/TransformationToolchainProvisioningPanel.tsx').read_text()
binding=(ROOT/'frontend/src/transformation-designer/TransformationBindingRow.tsx').read_text()
mapping=(ROOT/'frontend/src/transformation-designer/TransformationMappingWorkspace.tsx').read_text()
relations=(ROOT/'frontend/src/transformation-designer/TransformationRelationBindings.tsx').read_text()
designer=(ROOT/'frontend/src/transformation-designer/TransformationDesigner.tsx').read_text()
check('visible execution UI uses high-level verification wording','Verify generated transformation' in preview and 'Execution language' in preview and 'Check execution support' in preview)
check('provisioning UI hides low-level package terminology','Execution support for the selected language' in provision and 'Managed toolchain packages' not in provision and 'Trusted toolchain package' not in provision)
check('target-property help is metamodel-aware','DynamicAttributeHelp' in binding and 'Direct candidates with the same type and multiplicity' in binding)
check('class mapping help is metamodel-aware','How to map these classes' in mapping and 'sourceClass.attributes.length' in mapping and 'targetClass.attributes.length' in mapping)
check('relation help uses actual source and target relations','Available source relations' in relations and 'sourceRelations.map' in relations and 'targetRelation.targetTypeId' in relations)
check('endpoint help summarizes selected metamodel','MetamodelTransformationHelp' in designer and 'model.classes.length' in designer and 'model.relations.length' in designer)
check('JavaScript is exposed by frontend language contract',"'JAVASCRIPT'" in (ROOT/'frontend/src/transformation-designer/types.ts').read_text())
check('TypeScript is exposed by frontend language contract',"'TYPESCRIPT'" in (ROOT/'frontend/src/transformation-designer/types.ts').read_text())
print(f'FIX57_TRANSFORMATION_UX={len(checks)}/{len(checks)} PASS')
