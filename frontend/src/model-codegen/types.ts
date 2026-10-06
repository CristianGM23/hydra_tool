export type ModelCodeGenerationFeature='TESTS'|'GETTERS_SETTERS'|'COMPARISON'|'STRING_REPRESENTATION'|'EQUALITY';
export interface CodeGenerationFeatureOptions {
    generateTests:boolean;
    generateGettersSetters:boolean;
    generateComparison:boolean;
    generateStringRepresentation:boolean;
    generateEquality:boolean;
}
export interface ModelCodeGenerationTarget {
    id:string;
    displayName:string;
    importCategory:string;
    outputKind:'SOURCE_CODE'|'MODEL_SERIALIZATION'|'DIAGRAM_PROJECTION'|'TEXTUAL_PROJECTION'|'PROJECT_PROJECTION'|'DOCUMENTATION'|string;
    extension:string;
    mediaType:string;
    description:string;
    supportedFeatures:ModelCodeGenerationFeature[];
}
export interface ModelCodeGenerationOptions { namespace:string; artifactName:string; includeTraceability:boolean; features:CodeGenerationFeatureOptions; }
export interface CodeGenerationDefinition {
    enabled:boolean;
    exposeInRuntime:boolean;
    targetIds:string[];
    defaultTargetId:string;
    outputNamespace:string;
    includeTraceability:boolean;
    defaultFeatures:CodeGenerationFeatureOptions;
    targetFeatureOverrides:Record<string,CodeGenerationFeatureOptions>;
    annotations:Record<string,string>;
}

export interface ModelCodeGenerationPreview { fileName:string; mediaType:string; content:string; selectedElementCount:number; }
