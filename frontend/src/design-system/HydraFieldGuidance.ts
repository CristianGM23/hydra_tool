import type { HydraLanguage } from '../i18n/translationCatalog';

export interface HydraFieldGuidance {
    choose: string;
    impact: string;
    options?: string;
}

type GuidanceKind = 'IDENTITY' | 'VISIBLE_COPY' | 'SEMANTIC_REFERENCE' | 'EXPRESSION' | 'RUNTIME' | 'VISUAL' | 'LOCATION' | 'EVENT' | 'GAMIFICATION' | 'INTEGRATION' | 'BOOLEAN' | 'GENERAL';

const GUIDANCE: Readonly<Record<GuidanceKind, Readonly<Record<HydraLanguage, Pick<HydraFieldGuidance, 'choose' | 'impact'>>>>> = {
    IDENTITY: {
        en: { choose: 'Use a stable, descriptive value that can survive renames of the visible label. Once other elements reference it, avoid changing it unless you also migrate those references.', impact: 'This value is used to identify or link configuration elements. Changing it can invalidate persisted references, imports, mappings or external integrations even when the visible name stays the same.' },
        es: { choose: 'Usa un valor estable y descriptivo que pueda mantenerse aunque cambie el nombre visible. Cuando otros elementos empiecen a referenciarlo, evita modificarlo salvo que también migres esas referencias.', impact: 'Este valor se utiliza para identificar o enlazar elementos de configuración. Cambiarlo puede invalidar referencias persistidas, importaciones, mappings o integraciones externas aunque el nombre visible no cambie.' }
    },
    VISIBLE_COPY: {
        en: { choose: 'Write short domain wording that a model user can understand without knowing HYDRA internals. Prefer the terminology already used by the metamodel, project or business domain.', impact: 'This primarily changes what people read in the interface, generated editor or diagnostics. It normally does not change the stable technical identity of the configured element.' },
        es: { choose: 'Escribe un texto breve del dominio que pueda entender quien usa el modelo sin conocer los detalles internos de HYDRA. Da prioridad a la terminología que ya utiliza el metamodelo, el proyecto o el dominio.', impact: 'Este valor cambia principalmente lo que se muestra en la interfaz, el editor generado o los diagnósticos. Normalmente no modifica la identidad técnica estable del elemento configurado.' }
    },
    SEMANTIC_REFERENCE: {
        en: { choose: 'Select the element that represents the same domain concept or relationship you intend to edit. Check its type, containment and source/target meaning before accepting the reference.', impact: 'This changes the semantic element HYDRA reads, creates or connects. An incompatible choice can make mappings, generated editors, transformations or validations invalid, so run the relevant checker after changing it.' },
        es: { choose: 'Selecciona el elemento que represente el mismo concepto o relación del dominio que quieres editar. Comprueba su tipo, contención y significado de origen/destino antes de aceptar la referencia.', impact: 'Esto cambia el elemento semántico que HYDRA lee, crea o conecta. Una elección incompatible puede invalidar mappings, editores generados, transformaciones o validaciones; ejecuta la comprobación correspondiente después de modificarlo.' }
    },
    EXPRESSION: {
        en: { choose: 'Start with the simplest condition or value that expresses the rule. Use advanced expressions only when a direct attribute, relation, constant or guided condition cannot represent the requirement.', impact: 'The expression is evaluated at runtime to decide a value, condition or branch. A wrong type or unavailable reference can block execution or produce diagnostics even if the surrounding definition is structurally valid.' },
        es: { choose: 'Empieza por la condición o el valor más sencillo que exprese la regla. Usa expresiones avanzadas solo cuando un atributo, relación, constante o condición guiada no pueda representar el requisito.', impact: 'La expresión se evalúa en ejecución para decidir un valor, una condición o una rama. Un tipo incorrecto o una referencia no disponible puede bloquear la ejecución o generar diagnósticos aunque la definición sea estructuralmente válida.' }
    },
    RUNTIME: {
        en: { choose: 'Choose the least aggressive runtime behaviour that still gives users the feedback they need. Prefer immediate execution for cheap checks and explicit/manual execution for expensive or potentially disruptive operations.', impact: 'This affects when or how HYDRA performs work, reports diagnostics or commits changes. More automatic behaviour can improve feedback but may increase runtime cost or make failures appear earlier in the workflow.' },
        es: { choose: 'Elige el comportamiento de ejecución menos agresivo que siga ofreciendo la información que necesita el usuario. Usa ejecución inmediata para comprobaciones baratas y ejecución explícita/manual para operaciones costosas o potencialmente disruptivas.', impact: 'Esto afecta a cuándo o cómo HYDRA ejecuta trabajo, informa diagnósticos o confirma cambios. Un comportamiento más automático puede mejorar el feedback, pero también aumentar el coste o hacer que los fallos aparezcan antes en el flujo.' }
    },
    VISUAL: {
        en: { choose: 'Use the preview and keep the visual hierarchy consistent with the rest of the editor. Prefer readable contrast, moderate sizes and styles that communicate meaning instead of decoration alone.', impact: 'This changes presentation only; the underlying metamodel element or relationship is preserved. Extreme values can reduce readability, accessibility or diagram density without changing semantic behaviour.' },
        es: { choose: 'Usa la vista previa y mantén una jerarquía visual coherente con el resto del editor. Prioriza contraste legible, tamaños moderados y estilos que comuniquen significado en lugar de ser solo decorativos.', impact: 'Esto modifica únicamente la presentación; el elemento o relación del metamodelo se conserva. Valores extremos pueden reducir la legibilidad, la accesibilidad o la densidad útil del diagrama sin cambiar la semántica.' }
    },
    LOCATION: {
        en: { choose: 'Prefer project-relative or managed locations when the artifact must be portable. Use an absolute location only when the resource intentionally belongs to this machine or deployment.', impact: 'HYDRA uses this location to read, write or package a resource. An invalid or machine-specific path may work locally but fail after moving the project, exporting it or running it on another host.' },
        es: { choose: 'Da prioridad a ubicaciones relativas al proyecto o gestionadas cuando el artefacto deba ser portable. Usa una ubicación absoluta solo si el recurso pertenece intencionadamente a este equipo o despliegue.', impact: 'HYDRA usa esta ubicación para leer, escribir o empaquetar un recurso. Una ruta no válida o ligada al equipo puede funcionar localmente y fallar al mover, exportar o ejecutar el proyecto en otro entorno.' }
    },
    EVENT: {
        en: { choose: 'Model the business fact first: who emits it, what happened and which data a consumer actually needs. Keep payload fields stable and avoid coupling the event to one screen or optional consumer.', impact: 'This affects event contracts, bindings and flows that publish or consume the event. Changing a referenced field or event identity can break downstream mappings, EventFlows or external subscribers.' },
        es: { choose: 'Modela primero el hecho del dominio: quién lo emite, qué ha ocurrido y qué datos necesita realmente un consumidor. Mantén estables los campos del payload y evita acoplar el evento a una pantalla o consumidor opcional.', impact: 'Esto afecta a los contratos, bindings y flujos que publican o consumen el evento. Cambiar un campo o una identidad referenciada puede romper mappings posteriores, EventFlows o suscriptores externos.' }
    },
    GAMIFICATION: {
        en: { choose: 'Base the configuration on verifiable evidence and a clear user outcome. Avoid rewarding UI clicks or repeated events that can be farmed; use limits, idempotence and compensation where appropriate.', impact: 'This changes how verified evidence becomes points, progression, challenges or rewards. A permissive rule can duplicate awards or distort the ledger, while a restrictive one may prevent legitimate progression.' },
        es: { choose: 'Basa la configuración en evidencias verificables y en un resultado claro para el usuario. Evita premiar clics de interfaz o eventos repetibles sin control; usa límites, idempotencia y compensación cuando corresponda.', impact: 'Esto cambia cómo una evidencia verificada se convierte en puntos, progresión, desafíos o recompensas. Una regla demasiado permisiva puede duplicar concesiones o distorsionar el ledger; una demasiado restrictiva puede impedir progresión legítima.' }
    },
    INTEGRATION: {
        en: { choose: 'Select the technology, adapter or host element that matches the project you will actually generate or modify. Prefer verified capabilities and stable locators over assumptions about file names or generated structure.', impact: 'This changes how HYDRA projects neutral elements into an external technology. A mismatched adapter, build system or locator can make generation succeed partially but fail during injection, build or native verification.' },
        es: { choose: 'Selecciona la tecnología, el adaptador o el elemento anfitrión que corresponda al proyecto que realmente vas a generar o modificar. Prioriza capacidades verificadas y localizadores estables frente a suposiciones sobre nombres de archivo o estructura generada.', impact: 'Esto cambia cómo HYDRA proyecta elementos neutrales sobre una tecnología externa. Un adaptador, sistema de compilación o localizador incorrecto puede permitir una generación parcial y fallar después durante la inyección, compilación o verificación nativa.' }
    },
    BOOLEAN: {
        en: { choose: 'Enable this only when the described behaviour is required for this workflow. If you are unsure, keep the safer/default state and verify the result in the preview or checker before enabling it broadly.', impact: 'This switch changes behaviour immediately for the configured element or generated editor. It can expose, hide, automate or restrict functionality without changing the element’s stable identity.' },
        es: { choose: 'Actívalo solo cuando el comportamiento descrito sea necesario para este flujo. Si tienes dudas, conserva el estado más seguro o predeterminado y comprueba el resultado en la vista previa o el verificador antes de generalizarlo.', impact: 'Este interruptor cambia inmediatamente el comportamiento del elemento configurado o del editor generado. Puede mostrar, ocultar, automatizar o restringir funcionalidad sin modificar la identidad estable del elemento.' }
    },
    GENERAL: {
        en: { choose: 'Choose the value that best matches the current modeling goal and keep it consistent with nearby fields. Use the example and the available values below as a reference, then run the relevant preview or checker.', impact: 'Changing this value updates the current definition and may affect dependent configuration. Review any diagnostics that appear before considering the change complete.' },
        es: { choose: 'Elige el valor que mejor corresponda al objetivo de modelado actual y mantenlo coherente con los campos relacionados. Usa el ejemplo y los valores disponibles como referencia y ejecuta después la vista previa o comprobación correspondiente.', impact: 'Cambiar este valor actualiza la definición y puede afectar a configuración dependiente. Revisa los diagnósticos que aparezcan antes de considerar completado el cambio.' }
    }
};

const OPTIONS: Readonly<Record<HydraLanguage, string>> = {
    en: 'Review the available values below before deciding. Their descriptions explain the intended use of each option; when HYDRA derives them from the current model or project, only the currently available choices are listed.',
    es: 'Revisa los valores disponibles que aparecen debajo antes de decidir. Sus descripciones explican el uso previsto de cada opción; cuando HYDRA los obtiene del modelo o proyecto actual, solo se muestran las alternativas disponibles en ese contexto.'
};

export function shouldEnrichFieldHelp(help: string): boolean {
    return help.trim().length > 0 && help.trim().length <= 140;
}

export function fieldGuidance(label: string, help: string, language: HydraLanguage, hasOptions: boolean): HydraFieldGuidance {
    const kind = classify(`${label} ${help}`.toLowerCase());
    const base = GUIDANCE[kind][language];
    return { ...base, options: hasOptions ? OPTIONS[language] : undefined };
}

function classify(text: string): GuidanceKind {
    // Stable identifiers take precedence over the surrounding domain. A field such as
    // "Event ID" or "Mapping ID" needs migration/reference guidance, not generic
    // event or semantic-reference advice.
    if (/\b(identifier|stable id|technical id|mapping id|rule id|document id|event id|integration id|namespace|version|code\b|key\b)/.test(text) || /\bid\b/.test(text)) return 'IDENTITY';
    if (/\b(event|payload|binding|eventflow|event flow|emitter|consumer)\b/.test(text)) return 'EVENT';
    if (/\b(points?|reward|challenge|achievement|gamification|evidence|ledger|cooldown|idempot)/.test(text)) return 'GAMIFICATION';
    if (/\b(integration|adapter|technology|toolchain|build system|host module|generated module|platform|injection)/.test(text)) return 'INTEGRATION';
    if (/\b(color|opacity|width|height|font|border|outline|padding|radius|shape|route|line pattern|marker|position|visual|label position|image fill)/.test(text)) return 'VISUAL';
    if (/\b(path|file|folder|directory|repository|url|uri|location|archive|artifact|package root|output)/.test(text)) return 'LOCATION';
    if (/\b(expression|condition|guard|operator|operand|constant|default value|parameter value|formula|predicate)/.test(text)) return 'EXPRESSION';
    if (/\b(runtime|execution|trigger|validation runs|transaction|undo|timeout|retry|automatic|auto-save|autosave|session|persistence mode)/.test(text)) return 'RUNTIME';
    if (/\b(metamodel|class|relation|reference|feature|attribute|mapping|source type|target type|containment|endpoint|semantic|root class|viewpoint)/.test(text)) return 'SEMANTIC_REFERENCE';
    if (/\b(name|title|description|message|visible text|caption|heading|label\b)/.test(text)) return 'VISIBLE_COPY';
    if (/\b(enable|enabled|disable|disabled|allow|allows|show|shows|hide|hides|required|read-only|editable|active when|automatic)/.test(text)) return 'BOOLEAN';
    return 'GENERAL';
}
