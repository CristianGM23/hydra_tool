import type { GrammarSeverity, TextRange } from '../grammar-designer/types';
export type TextualEditorFeature='COMPLETION'|'HOVER'|'GO_TO_DEFINITION'|'FIND_REFERENCES'|'RENAME'|'FORMATTING'|'FOLDING'|'OUTLINE'|'DIAGNOSTICS'|'QUICK_FIXES';
export type TextualRuntimeMode='AUTO'|'LANGUAGE_SERVER'|'BROWSER_WORKER'|'PARSER_BRIDGE';
export interface TextualEditorDefinitionModel{schemaVersion:string;id:string;name:string;grammarId:string;compilationSnapshotId:string;syntaxStyleDefinitionId:string;fileExtensions:string[];entryRule:string;runtimeMode:TextualRuntimeMode;features:TextualEditorFeature[];validationModelIds:string[];actionModelIds:string[];eventDefinitionIds:string[];eventFlowIds:string[];gamificationDefinitionIds:string[];settings:Record<string,string>;}
export interface TextualModelSourceDocument{id:string;relativePath:string;content:string;revision:number;}
export interface TextualModelDiagnostic{code:string;severity:GrammarSeverity;title:string;message:string;consequence:string;solution:string;documentId:string;range:TextRange;metadata:Record<string,string>;}
export interface TextualSemanticSourceMapEntry{semanticId:string;semanticKind:string;documentId:string;range:TextRange;}
export interface TextualModelSession{id:string;editorDefinitionId:string;grammarId:string;grammarSnapshotId:string;documents:TextualModelSourceDocument[];draftRevision:number;lastParsedRevision:number;lastParsedSourceHash:string;lastValidSemanticModel:Record<string,unknown>|null;lastValidSourceMap:TextualSemanticSourceMapEntry[];diagnostics:TextualModelDiagnostic[];updatedAt:string;metadata:Record<string,string>;draftValid:boolean;}
export interface TextualRuntimeNegotiation{requestedMode:TextualRuntimeMode;effectiveMode:TextualRuntimeMode;grammarSnapshotId:string;providerId:string;fileExtensions:string[];entryRule:string;supportedFeatures:TextualEditorFeature[];unavailableFeatures:TextualEditorFeature[];evidence:Record<string,string>;}

export interface TextualSharedLayerOptions{semanticContractId:string;validationModelIds:string[];actionModelIds:string[];eventDefinitionIds:string[];eventFlowIds:string[];gamificationDefinitionIds:string[];}
