import type { UpdateSiteDependency } from './types';

export interface UpdateSiteDiagnosticExplanation {
    subject: string;
    category: string;
    purpose: string;
    impact: string;
    recommendation: string;
}

interface Rule {
    terms: string[];
    category: string;
    purpose: string;
    impact: string;
    recommendation: string;
}

const RULES: Rule[] = [
    {
        terms: ['content.xml', 'content.jar'],
        category: 'p2 content metadata',
        purpose: 'Describes installable units, versions, requirements and the logical dependency graph of an Eclipse update site.',
        impact: 'Without it, HYDRA can still inspect physical files, but feature and dependency reconstruction is less exact.',
        recommendation: 'Include content.xml or content.jar from the original repository when available.'
    },
    {
        terms: ['artifacts.xml', 'artifacts.jar'],
        category: 'p2 artifact metadata',
        purpose: 'Maps installable artifacts to their physical plug-in and feature files and may include repository-specific download properties.',
        impact: 'Without it, HYDRA relies on file names and directory layout to associate metadata with physical artifacts.',
        recommendation: 'Include artifacts.xml or artifacts.jar to improve artifact identity and version matching.'
    },
    {
        terms: ['feature.xml'],
        category: 'Eclipse feature',
        purpose: 'Groups related plug-ins into an installable product capability and declares included plug-ins and feature dependencies.',
        impact: 'Missing feature metadata does not normally remove the metamodel, but product composition and installation intent may be lost.',
        recommendation: 'Recover the feature JAR or keep the plug-in inventory as a lower-level reconstruction.'
    },
    {
        terms: ['plugin.xml'],
        category: 'Eclipse extension registry',
        purpose: 'Registers editors, commands, handlers, menus, views, validation, generated packages and other workbench integrations.',
        impact: 'The metamodel may still be recovered, but UI behavior and runtime integration can be incomplete.',
        recommendation: 'Locate plugin.xml, fragment.xml or generated extension declarations in another bundle.'
    },
    {
        terms: ['fragment.xml'],
        category: 'Eclipse fragment registry',
        purpose: 'Contributes extensions and resources to a host plug-in without defining a standalone bundle.',
        impact: 'Host behavior can be partially reconstructed while fragment-specific UI or runtime additions remain missing.',
        recommendation: 'Provide the fragment bundle together with its host plug-in.'
    },
    {
        terms: ['manifest.mf', 'org.eclipse.osgi', 'osgi'],
        category: 'OSGi bundle manifest',
        purpose: 'Defines bundle identity, dependencies, exported packages, fragments and runtime classpath.',
        impact: 'Bundle roles and missing dependencies become harder to determine reliably.',
        recommendation: 'Include META-INF/MANIFEST.MF or the complete plug-in JAR.'
    },
    {
        terms: ['build.properties'],
        category: 'Eclipse build configuration',
        purpose: 'Declares source folders and resources packaged into the built plug-in.',
        impact: 'HYDRA may not know whether missing models, icons or descriptors were intentionally excluded or absent from the upload.',
        recommendation: 'Provide build.properties when investigating incomplete exports.'
    },
    {
        terms: ['.ecore', 'org.eclipse.emf.ecore'],
        category: 'EMF metamodel',
        purpose: 'Defines packages, classes, attributes, references, operations, enumerations and data types.',
        impact: 'Without original Ecore evidence, HYDRA must fall back to GenModel, registry, bytecode or inference with lower fidelity.',
        recommendation: 'Prefer the original .ecore file or verify every reconstructed element against generated EMF code.'
    },
    {
        terms: ['.genmodel'],
        category: 'EMF generation model',
        purpose: 'Links Ecore packages to generated Java packages and stores code-generation configuration.',
        impact: 'Generated package names and generation intent may be missing even when the core metamodel is available.',
        recommendation: 'Provide the .genmodel to improve generated-code and package reconstruction.'
    },
    {
        terms: ['gmf', '.gmfgraph', '.gmftool', '.gmfmap', '.gmfgen'],
        category: 'GMF diagram definition',
        purpose: 'Defines graphical figures, palette tools, semantic-to-visual mappings and generated diagram-editor configuration.',
        impact: 'HYDRA may recover the metamodel but not the original diagram notation, palette or mappings.',
        recommendation: 'Provide all GMF descriptors together because each file covers a different editor layer.'
    },
    {
        terms: ['sirius', '.odesign'],
        category: 'Sirius viewpoint specification',
        purpose: 'Defines viewpoints, diagram descriptions, mappings, tools and semantic expressions.',
        impact: 'Graphical representations and viewpoint-specific editing behavior may not be recoverable.',
        recommendation: 'Provide the .odesign file and any referenced semantic models or Java services.'
    },
    {
        terms: ['.aird'],
        category: 'Sirius session',
        purpose: 'Stores project-specific Sirius representation sessions and references to semantic models and viewpoints.',
        impact: 'The viewpoint may be recoverable while concrete saved diagrams remain unavailable.',
        recommendation: 'Provide referenced semantic models and the corresponding .odesign; do not treat the session alone as an editor definition.'
    },
    {
        terms: ['xtext', '.xtext'],
        category: 'Xtext language runtime',
        purpose: 'Defines textual grammar, parser behavior, linking, validation and language-service integration.',
        impact: 'Textual syntax, cross-references or generated language services may be incomplete.',
        recommendation: 'Provide the grammar plus referenced grammars, MWE configuration and runtime/UI plug-ins.'
    },
    {
        terms: ['langium', '.langium'],
        category: 'Langium language definition',
        purpose: 'Defines textual syntax and semantic construction for a TypeScript-based language server and editor.',
        impact: 'HYDRA may derive model structure but lose custom validation, scoping or generated editor integration.',
        recommendation: 'Provide the grammar, configuration and custom language-service sources.'
    },
    {
        terms: ['org.eclipse.emf.edit'],
        category: 'EMF.Edit editing runtime',
        purpose: 'Provides item-provider adapters, editing commands, labels, icons and property metadata used by generated EMF editors.',
        impact: 'The core metamodel can still be recovered, but generated editor labels, property presentation and command behavior may be incomplete.',
        recommendation: 'Recover the Edit plug-in and its item-provider adapter factories when editor presentation matters; otherwise keep it as an external runtime dependency.'
    },
    {
        terms: ['org.eclipse.epsilon.eugenia', 'eugenia'],
        category: 'Eugenia / GMF generation',
        purpose: 'Uses Ecore annotations and generation conventions to derive GMF graphical editors, palettes, mappings and notation behavior.',
        impact: 'The Ecore structure may be exact while graphical-editor generation intent remains only partially reconstructed.',
        recommendation: 'Inspect Eugenia annotations on the Ecore model together with generated GMF descriptors; keep unsupported generation semantics as explicit evidence instead of guessing them.'
    },
    {
        terms: ['org.eclipse.emf.common'],
        category: 'EMF common runtime',
        purpose: 'Provides shared EMF utilities, notification, command and collection infrastructure used by generated models.',
        impact: 'Generated model code may be identifiable but cannot be fully validated against its runtime dependencies.',
        recommendation: 'Add the matching EMF runtime bundle or configure it as an accepted external dependency.'
    },
    {
        terms: ['org.eclipse.ui', 'org.eclipse.jface', 'org.eclipse.swt'],
        category: 'Eclipse workbench UI',
        purpose: 'Provides windows, views, editors, commands, dialogs, widgets and UI integration used by Eclipse plug-ins.',
        impact: 'Model structure is usually unaffected, but recovered editor behavior and forms can be partial.',
        recommendation: 'Treat it as a third-party runtime dependency unless reconstructing the original Eclipse UI is required.'
    },
    {
        terms: ['org.eclipse.core.runtime'],
        category: 'Eclipse core runtime',
        purpose: 'Provides extension-registry, lifecycle and runtime services required by most Eclipse plug-ins.',
        impact: 'Extension declarations can be read, but runtime behavior cannot be validated without the platform bundle.',
        recommendation: 'Record it as an external platform dependency; do not infer missing behavior.'
    },
    {
        terms: ['org.eclipse.core.resources'],
        category: 'Eclipse workspace resources',
        purpose: 'Provides projects, files, builders, markers and workspace change notifications.',
        impact: 'Project-specific builders, markers or resource listeners may not be reconstructed.',
        recommendation: 'Preserve related extension declarations and model them as runtime capabilities when needed.'
    },
    {
        terms: ['.properties'],
        category: 'Externalized labels and messages',
        purpose: 'Stores translated labels, command names, descriptions and other user-facing text outside Java or XML.',
        impact: 'Recovered functionality remains usable, but labels and help text may be missing or replaced by technical identifiers.',
        recommendation: 'Keep the file as a supporting resource and link its keys during materialization.'
    },
    {
        terms: ['.exsd'],
        category: 'Extension-point schema',
        purpose: 'Documents the attributes and child elements accepted by a custom Eclipse extension point.',
        impact: 'The extension can be preserved, but HYDRA may not understand its domain-specific semantics.',
        recommendation: 'Retain it as documentation and create a dedicated adapter if the extension is important.'
    }
];

export function explainMissingDependency(dependency: UpdateSiteDependency): UpdateSiteDiagnosticExplanation {
    const subject = dependency.targetId || 'Unknown dependency';
    const explanation = explainSubject(subject);
    return {
        ...explanation,
        subject,
        impact: `${explanation.impact}${dependency.optional ? ' This dependency is declared optional, so the main bundle may still work with reduced capability.' : ' This dependency is required by the source bundle.'}`,
        recommendation: dependency.resolved
            ? 'The dependency was resolved in the uploaded repository; inspect version compatibility if diagnostics remain.'
            : explanation.recommendation
    };
}

export function explainDiagnostic(message: string): UpdateSiteDiagnosticExplanation {
    const subject = diagnosticSubject(message);
    const explanation = explainSubject(subject || message);
    return {
        ...explanation,
        subject: subject || 'Import diagnostic',
        impact: explanation.category === 'Unclassified recovery issue'
            ? 'The reported limitation may reduce completeness or confidence. HYDRA keeps it visible rather than assuming the missing behavior.'
            : explanation.impact
    };
}

export function explainSubject(subject: string): UpdateSiteDiagnosticExplanation {
    const normalized = subject.toLowerCase();
    const rule = RULES.find(candidate => candidate.terms.some(term => normalized.includes(term.toLowerCase())));
    if (rule !== undefined) {
        return {
            subject,
            category: rule.category,
            purpose: rule.purpose,
            impact: rule.impact,
            recommendation: rule.recommendation
        };
    }
    return {
        subject,
        category: 'Unclassified recovery issue',
        purpose: 'This element supplies code, metadata or resources used by the original tooling, but HYDRA does not yet have enough evidence to classify its exact responsibility.',
        impact: 'Some runtime, UI or domain-specific behavior may remain outside the neutral reconstruction.',
        recommendation: 'Inspect the source bundle and related files. Add a dedicated adapter when this capability is essential.'
    };
}

function diagnosticSubject(message: string): string {
    const file = message.match(/(?:[A-Za-z]:)?[^"'()\s]+\.(?:xml|jar|ecore|genmodel|xtext|langium|odesign|aird|gmfgraph|gmftool|gmfmap|gmfgen|properties|exsd)/i);
    if (file !== null) return file[0];
    const dependency = message.match(/org\.eclipse\.[A-Za-z0-9_.-]+/i);
    if (dependency !== null) return dependency[0];
    const missing = message.match(/(?:missing|not found|could not locate|unresolved)\s+(?:dependency|file|element|artifact)?\s*:?\s*([A-Za-z0-9_.:/-]+)/i);
    return missing?.[1] ?? '';
}
