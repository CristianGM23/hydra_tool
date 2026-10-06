import type { ProjectPreset } from './types';

const CAPABILITY_LABELS: Readonly<Record<string, string>> = {
    METAMODEL_DESIGN: 'Concepts and structure',
    GRAMMAR_DESIGN: 'Textual language',
    MODEL_EDITING: 'Model editing',
    TEXTUAL_MODELING: 'Textual models',
    VISUAL_MODELING: 'Visual models',
    EDITOR_DEFINITIONS: 'Editor configuration',
    VISUAL_EDITOR: 'Visual editor',
    TEXTUAL_EDITOR: 'Textual editor',
    SYNTAX_STYLING: 'Text appearance',
    CONTEXTUAL_INFORMATION: 'Contextual help',
    VALIDATIONS: 'Quality rules',
    QUICK_FIXES: 'Suggested corrections',
    EVENTS: 'Events',
    EVENT_FLOWS: 'Event flows',
    GAMIFICATION: 'Gamification',
    TRANSFORMATION_M2M: 'Model-to-model transformations',
    TRANSFORMATION_M2T: 'Text and code generation',
    TRANSFORMATION_T2M: 'Create models from text',
    EDITOR_GENERATION: 'Generate reusable editors',
    INTEGRATIONS: 'External integrations',
    REPOSITORY_INTEGRATION: 'Repositories and cloud storage',
    DRAWIO_EXPORT: 'Draw.io export',
    PLANTUML_EXPORT: 'PlantUML export',
    LOCAL_FILE_EDITING: 'Project files',
    OPERATIONS: 'Model operations',
};

export function projectCapabilityLabel(id: string): string {
    return CAPABILITY_LABELS[id] ?? id.toLowerCase().replaceAll('_', ' ').replace(/(^|\s)\S/g, token => token.toUpperCase());
}

export function projectPresetLabel(preset: ProjectPreset): string {
    const labels: Record<ProjectPreset, string> = {
        EMPTY: 'Project from scratch',
        GRAPHICAL_LANGUAGE: 'Graphical language',
        TEXTUAL_DSL: 'Textual language',
        HYBRID_LANGUAGE: 'Visual and textual language',
        MODELING_VALIDATION: 'Models and quality rules',
        M2M_TRANSFORMATION: 'Model transformation',
        M2T_GENERATION: 'Text and code generation',
        T2M_ENGINEERING: 'Create models from text',
        COMPLETE_EDITOR: 'Complete modeling editor',
        COMPLETE_MDE: 'Complete modeling project',
    };
    return labels[preset];
}

export function projectPresetDescription(preset: ProjectPreset): string {
    const descriptions: Record<ProjectPreset, string> = {
        EMPTY: 'Start with an empty project and add only what you need.',
        GRAPHICAL_LANGUAGE: 'Define concepts and edit models visually.',
        TEXTUAL_DSL: 'Define a textual language, its appearance and the help shown while editing.',
        HYBRID_LANGUAGE: 'Use the same language through visual and textual editors.',
        MODELING_VALIDATION: 'Create models and add rules that detect and help correct problems.',
        M2M_TRANSFORMATION: 'Convert models from one structure into another.',
        M2T_GENERATION: 'Generate text, documentation or code from your models.',
        T2M_ENGINEERING: 'Read structured text and build models from it.',
        COMPLETE_EDITOR: 'Prepare a language with editing, validation and user assistance.',
        COMPLETE_MDE: 'Enable the complete modeling workflow from definition to generation and delivery.',
    };
    return descriptions[preset];
}

export interface ProjectPresetGuidance {
    creationTitle: string;
    summary: string;
    recommendedMode: 'GUIDED' | 'FREE';
    preparation: string[];
    nextSteps: string[];
    customizationHint: string;
}

const PRESET_GUIDANCE: Readonly<Record<ProjectPreset, ProjectPresetGuidance>> = {
    EMPTY: {
        creationTitle: 'Create a project from scratch',
        summary: 'Start with the project container only and decide the work areas yourself.',
        recommendedMode: 'FREE',
        preparation: ['Project structure and descriptor', 'Local project folder'],
        nextSteps: ['Choose the areas you need', 'Create or import the first project resource'],
        customizationHint: 'No modeling area is enabled by default. Choose project areas if you already know what you need.'
    },
    GRAPHICAL_LANGUAGE: {
        creationTitle: 'Create a graphical modeling language',
        summary: 'Prepare the concepts of the language together with visual modeling and editor support.',
        recommendedMode: 'GUIDED',
        preparation: ['Concepts and structure', 'Visual editor', 'Visual models'],
        nextSteps: ['Define the concepts and relationships', 'Configure how elements look and connect', 'Create a first visual model'],
        customizationHint: 'Keep the recommended areas unless you also need textual notation, validation or generation from the start.'
    },
    TEXTUAL_DSL: {
        creationTitle: 'Create a textual language',
        summary: 'Prepare a grammar, textual models, text appearance and contextual help in one project.',
        recommendedMode: 'GUIDED',
        preparation: ['Textual language', 'Text appearance', 'Contextual help', 'Textual models'],
        nextSteps: ['Define or import the grammar', 'Configure syntax appearance and contextual help', 'Create and validate a sample textual model'],
        customizationHint: 'Add visual modeling only if the same language must also have a graphical representation.'
    },
    HYBRID_LANGUAGE: {
        creationTitle: 'Create a visual and textual language',
        summary: 'Prepare one language that can be edited through coordinated graphical and textual views.',
        recommendedMode: 'GUIDED',
        preparation: ['Concepts and structure', 'Textual language', 'Visual editor', 'Textual editor'],
        nextSteps: ['Define the shared language structure', 'Configure visual and textual notation', 'Check that both editors represent the same concepts'],
        customizationHint: 'This starting point is broader than a single editor because both notations need to stay aligned.'
    },
    MODELING_VALIDATION: {
        creationTitle: 'Create models with quality rules',
        summary: 'Prepare model editing together with validations and suggested corrections.',
        recommendedMode: 'GUIDED',
        preparation: ['Concepts and structure', 'Model editing', 'Quality rules', 'Suggested corrections'],
        nextSteps: ['Define the model structure', 'Create a representative model', 'Add quality rules and corrections'],
        customizationHint: 'Add events or generation only when the validated models also need reactive behavior or derived outputs.'
    },
    M2M_TRANSFORMATION: {
        creationTitle: 'Create a model transformation',
        summary: 'Prepare a project for converting models from one structure into another.',
        recommendedMode: 'GUIDED',
        preparation: ['Source and target structures', 'Model-to-model transformations', 'Model examples'],
        nextSteps: ['Choose the source structure', 'Choose the target structure', 'Define correspondences and test the transformation'],
        customizationHint: 'Keep both structural-model areas enabled because the transformation needs a source and a target definition.'
    },
    M2T_GENERATION: {
        creationTitle: 'Create text or code generation',
        summary: 'Prepare model input together with generation rules for text, documentation or code.',
        recommendedMode: 'GUIDED',
        preparation: ['Model source', 'Text and code generation', 'Generated output area'],
        nextSteps: ['Select the source structure', 'Define generation rules or templates', 'Run the generator with a representative model'],
        customizationHint: 'Add reusable editor generation only if the project must also package an editor for other users.'
    },
    T2M_ENGINEERING: {
        creationTitle: 'Create models from structured text',
        summary: 'Prepare parsing and target-model support for turning structured text into models.',
        recommendedMode: 'GUIDED',
        preparation: ['Textual language', 'Target structure', 'Create models from text'],
        nextSteps: ['Define or import the input grammar', 'Define the target model structure', 'Map parsed information into model elements'],
        customizationHint: 'Keep both grammar and target-structure areas because text-to-model conversion depends on both.'
    },
    COMPLETE_EDITOR: {
        creationTitle: 'Create a complete modeling editor',
        summary: 'Prepare language definition, editing, validation and user assistance as one deliverable.',
        recommendedMode: 'GUIDED',
        preparation: ['Language definition', 'Editor configuration', 'Quality rules', 'User assistance'],
        nextSteps: ['Define the language', 'Configure the editor experience', 'Add validation and assistance', 'Generate and test the reusable editor'],
        customizationHint: 'This preset already includes the areas normally needed for an editor; remove areas only if you deliberately want a smaller product.'
    },
    COMPLETE_MDE: {
        creationTitle: 'Create a complete modeling project',
        summary: 'Prepare the full HYDRA workflow from language definition to transformations, generation and delivery.',
        recommendedMode: 'GUIDED',
        preparation: ['Language and models', 'Quality and behavior', 'Transformations and generation', 'Repositories and delivery'],
        nextSteps: ['Define the core language', 'Create representative models', 'Add quality and behavior', 'Configure transformations and generation', 'Prepare delivery and repository integration'],
        customizationHint: 'Use this starting point when you genuinely expect to use most HYDRA areas. For a focused task, a smaller preset will keep the project clearer.'
    }
};

export function projectPresetGuidance(preset: ProjectPreset): ProjectPresetGuidance {
    return PRESET_GUIDANCE[preset];
}
