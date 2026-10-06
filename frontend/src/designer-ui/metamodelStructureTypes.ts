export type MetamodelStructureSelection = {
    kind: 'MODEL' | 'CLASS' | 'ATTRIBUTE' | 'OPERATION' | 'PARAMETER' | 'RELATION' | 'ENUM' | 'LITERAL' | 'DATA_TYPE';
    id: string;
    ownerId?: string;
    label: string;
    detail?: string;
    badge?: string;
    treeNodeId?: string;
};
