import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { MetaModel, MetamodelValidationAnalysis, MetamodelValidationGateStatus } from './types';
async function json<T>(response: Response): Promise<T>{if(!response.ok) throw await readActionableApiError(response);return response.json() as Promise<T>;}
export async function analyzeMetamodelDraft(model: MetaModel): Promise<MetamodelValidationAnalysis>{return json(await backendFetch('/api/canonical-metamodels/validation/analyze',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(model)}));}
export async function validateStoredMetamodel(id: string): Promise<MetamodelValidationAnalysis>{return json(await backendFetch(`/api/canonical-metamodels/${encodeURIComponent(id)}/validation/run`,{method:'POST'}));}
export async function loadMetamodelValidationStatus(id: string): Promise<MetamodelValidationGateStatus>{return json(await backendFetch(`/api/canonical-metamodels/${encodeURIComponent(id)}/validation/status`));}
