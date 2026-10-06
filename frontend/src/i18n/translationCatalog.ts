import { APPLICATION_SPANISH } from './applicationSpanishCatalog';
import { DESIGN_SPANISH } from './designSpanishCatalog';
import { DELIVERY_SPANISH } from './deliverySpanishCatalog';
import { DOMAIN_SPANISH } from './domainSpanishCatalog';
import { EXTENDED_SPANISH } from './extendedSpanishCatalog';
import { PRODUCTION_SPANISH } from './productionSpanishCatalog';
import { GUIDED_SPANISH } from './guidedSpanishCatalog';
import { INTERCHANGE_SPANISH } from './interchangeSpanishCatalog';
import { CURRENT_SPANISH } from './currentSpanishCatalog';
import { GRAMMAR_SPANISH } from './grammarSpanishCatalog';
import { TRANSFORMATION_SPANISH } from './transformationSpanishCatalog';
import { HOME_SHELL_SPANISH } from './homeShellSpanishCatalog';
import { INTERFACE_AUDIT_SPANISH } from './interfaceAuditSpanishCatalog';

import { translateCurrentRuntimeSpanish } from './runtimeSpanishPatterns';
import { presentVisibleTerminology } from './visibleTerminology';
import { isProtectedText, runtimeContextLabel, translateGuidedOperator } from './translationCatalogHelpers';
export type HydraLanguage = 'en' | 'es';
/**
 * Explicit resolutions for phrases that appear in more than one
 * catalogue with different Spanish copy. Keeping them here makes precedence
 * reviewable instead of depending on object-spread order.
 */
export const SPANISH_CONFLICT_RESOLUTIONS: Readonly<Record<string, string>> = {
    'Package ZIP': 'Empaquetar ZIP',
    'Mapping': 'Mapeo',
    'Mappings': 'Mapeos',
    'Analyze selected candidate': 'Analizar candidato seleccionado',
    'Merge': 'Fusionar',
    'Fingerprint': 'Huella digital',
    'Open': 'Abrir',
    'Retry': 'Reintentar',
    'selected': 'seleccionado',
    'Complete': 'Completado',
    'Payload': 'Payload',
    'Remove': 'Eliminar',
    'Payload fields': 'Campos del payload',
    '+ Mapping': '+ Mapeo',
    'Route': 'Enrutar',
    'Quick fixes': 'Correcciones rápidas',
    'Build system': 'Sistema de compilación',
    'Injection preview': 'Vista previa de inyección',
    'Payload mappings': 'Mapeos de payload',
    'Event flows': 'Flujos de eventos',
    'equal to': 'igual a',
    'different from': 'distinto de',
    'greater than': 'mayor que',
    'greater than or equal to': 'mayor o igual que',
    'less than': 'menor que',
    'less than or equal to': 'menor o igual que',
    'Solid': 'Sólido',
    'Dashed': 'Discontinuo',
    'Dotted': 'Punteado',
    'None': 'Ninguno',
    'Validations': 'Validaciones',
    'HYbrid DSL &amp; Representation Architecture': 'HYbrid DSL &amp; Representation Architecture',
    'Book.authors, library.ecore, bytecode…': 'Book.authors, library.ecore, bytecode…'
};
const EXACT_SPANISH: Readonly<Record<string, string>> = {
    'Home': 'Inicio',
    'Settings': 'Configuración',
    'Current module': 'Módulo actual',
    'Open application settings': 'Abrir la configuración de la aplicación',
    'Active metamodel': 'Metamodelo activo',
    'Select a metamodel': 'Selecciona un metamodelo',
    'Canonical metamodels': 'Metamodelos',
    'Metamodels': 'Metamodelos',
    'root package(s)': 'paquete(s) raíz',
    'HYDRA modules': 'Módulos de HYDRA',
    'Design': 'Diseño',
    'DESIGN': 'DISEÑO',
    'Behavior': 'Comportamiento',
    'BEHAVIOR': 'COMPORTAMIENTO',
    'Delivery': 'Entrega',
    'DELIVERY': 'ENTREGA',
    'Operations': 'Operaciones',
    'OPERATIONS': 'OPERACIONES',
    'START': 'INICIO',
    'Overview': 'Resumen',
    'Readiness': 'Preparación',
    'Back to overview': 'Volver al resumen',
    'Language': 'Idioma',
    'Application language': 'Idioma de la aplicación',
    'English': 'Inglés',
    'Spanish': 'Español',
    'Switch application language': 'Cambiar el idioma de la aplicación',
    'Interface language': 'Idioma de la interfaz',
    'The language changes only visible interface text. Stable IDs, technical names, persisted values and API contracts are preserved.': 'El idioma solo cambia el texto visible de la interfaz. Se conservan los identificadores estables, los nombres técnicos, los valores persistidos y los contratos de la API.',
    'HYDRA 1.0 modular workbench': 'Entorno modular HYDRA 1.0',
    'Build only the capability you need': 'Abre solo la capacidad que necesitas',
    'Open any designer directly. A metamodel is shared semantic context, not a mandatory screen that must remain open.': 'Abre directamente cualquier diseñador. El metamodelo es un contexto semántico compartido, no una pantalla obligatoria que deba permanecer abierta.',
    'Create or import a metamodel': 'Crear o importar un metamodelo',
    'Open model editor': 'Abrir el editor de modelos',
    'Active semantic context': 'Contexto semántico activo',
    'Home metamodel context': 'Contexto de metamodelo de Inicio',
    'This selection follows you when you open validations, events, gamification, editors, integrations or round-trip synchronization.': 'Esta selección se mantiene al abrir validaciones, eventos, gamificación, editores, integraciones o sincronización de ida y vuelta.',
    'Independent HYDRA capabilities': 'Capacidades independientes de HYDRA',
    'Create metamodels, models and configurable editors.': 'Crea metamodelos, modelos y editores configurables.',
    'Add validations, events, flows and gamification as independent layers.': 'Añade validaciones, eventos, flujos y gamificación como capas independientes.',
    'Translate, synchronize and package HYDRA artifacts.': 'Traduce, sincroniza y empaqueta artefactos de HYDRA.',
    'Inspect readiness and verify real toolchains.': 'Revisa la preparación y verifica cadenas de herramientas reales.',
    'Uses metamodel context': 'Usa el contexto del metamodelo',
    'Independent utility': 'Utilidad independiente',
    'Open module →': 'Abrir módulo →',
    'Loading capability readiness…': 'Cargando la preparación de capacidades…',
    'Your workspace': 'Tu espacio de trabajo',
    'Start or continue a HYDRA project': 'Empieza o continúa un proyecto HYDRA',
    'Create a project for what you want to build, open an existing one or continue recent work.': 'Crea un proyecto según lo que quieras construir, abre uno existente o continúa un trabajo reciente.',
    'Guided mode': 'Modo guiado',
    'Free mode': 'Modo libre',
    'active areas': 'áreas activas',
    'Change project': 'Cambiar de proyecto',
    'Project actions': 'Acciones del proyecto',
    'New project': 'Nuevo proyecto',
    'Choose what you want to create and HYDRA will prepare only the parts you need.': 'Elige qué quieres crear y HYDRA preparará solo las partes que necesitas.',
    'Create project': 'Crear proyecto',
    'Open project': 'Abrir proyecto',
    'Continue working with a HYDRA project already saved on this computer.': 'Continúa trabajando con un proyecto HYDRA ya guardado en este equipo.',
    'Bring an existing project': 'Traer un proyecto existente',
    'Import a HYDRA project, a project folder or a project created with another modeling technology.': 'Importa un proyecto HYDRA, una carpeta de proyecto o un proyecto creado con otra tecnología de modelado.',
    'Import': 'Importar',
    'Connect an external location': 'Conectar una ubicación externa',
    'Open or import work stored in a configured repository or cloud space.': 'Abre o importa trabajo guardado en un repositorio o espacio en la nube ya configurado.',
    'Connect': 'Conectar',
    'Continue working': 'Continuar trabajando',
    'Choose where to continue': 'Elige dónde continuar',
    'Main component': 'Componente principal',
    'You have not selected a main component yet. Open one of the project areas to continue.': 'Todavía no has seleccionado un componente principal. Abre una de las áreas del proyecto para continuar.',
    'Continue': 'Continuar',
    'Project overview': 'Resumen del proyecto',
    'This project can grow as you add new areas of work.': 'Este proyecto puede crecer a medida que añadas nuevas áreas de trabajo.',
    'Working mode': 'Modo de trabajo',
    'Active areas': 'Áreas activas',
    'Not selected': 'No seleccionado',
    'Configure project': 'Configurar proyecto',
    'Your project': 'Tu proyecto',
    'What you can work on': 'En qué puedes trabajar',
    'Only the areas enabled for this project are shown.': 'Solo se muestran las áreas activadas para este proyecto.',
    'Create quickly': 'Crear rápidamente',
    'What do you want to build?': '¿Qué quieres construir?',
    'These choices only prepare the starting point. You can add more areas later.': 'Estas opciones solo preparan el punto de partida. Podrás añadir más áreas después.',
    'Current work': 'Trabajo actual',
    'Model or language in focus': 'Modelo o lenguaje en uso',
    'Choose a model definition or grammar to review its current state and continue editing it.': 'Elige una definición de modelo o una gramática para revisar su estado actual y continuar editándola.',
    'Nothing selected yet': 'Todavía no hay nada seleccionado',
    'Choose a metamodel or grammar to see what is ready and what still needs attention.': 'Elige un metamodelo o una gramática para ver qué está preparado y qué necesita atención.',
    'Loading project status…': 'Cargando el estado del proyecto…',
    'Recent work': 'Trabajo reciente',
    'Recent projects': 'Proyectos recientes',
    'See all projects': 'Ver todos los proyectos',
    'No recent projects yet. Create or open one to see it here.': 'Todavía no hay proyectos recientes. Crea o abre uno para verlo aquí.',
    'Currently open': 'Abierto actualmente',
    'Design the language': 'Diseñar el lenguaje',
    'Designar el lenguaje': 'Diseñar el lenguaje',
    'Define the concepts, the grammar and the way they are edited.': 'Define los conceptos, la gramática y la forma en la que se editan.',
    'Create and edit models': 'Crear y editar modelos',
    'Work with examples and models created with your language.': 'Trabaja con ejemplos y modelos creados con tu lenguaje.',
    'Check and correct': 'Comprobar y corregir',
    'Add rules to detect problems and help solve them.': 'Añade reglas para detectar problemas y ayudar a solucionarlos.',
    'Define behavior': 'Definir comportamiento',
    'Configure events, flows and mechanics that react to what happens in models.': 'Configura eventos, flujos y mecánicas que reaccionan a lo que ocurre en los modelos.',
    'Transform and generate': 'Transformar y generar',
    'Convert models, generate text or code and prepare reusable editors.': 'Convierte modelos, genera texto o código y prepara editores reutilizables.',
    'Save, connect and share': 'Guardar, conectar y compartir',
    'Connect the project with external repositories and prepare its distribution.': 'Conecta el proyecto con repositorios externos y prepara su distribución.',
    'Project from scratch': 'Proyecto desde cero',
    'Graphical language': 'Lenguaje gráfico',
    'Textual language': 'Lenguaje textual',
    'Visual and textual language': 'Lenguaje visual y textual',
    'Models and quality rules': 'Modelos y reglas de calidad',
    'Model transformation': 'Transformación de modelos',
    'Text and code generation': 'Generación de texto y código',
    'Create models from text': 'Crear modelos desde texto',
    'Complete modeling editor': 'Editor de modelado completo',
    'Complete modeling project': 'Proyecto de modelado completo',
    'Start with an empty project and add only what you need.': 'Empieza con un proyecto vacío y añade solo lo que necesites.',
    'Define concepts and edit models visually.': 'Define conceptos y edita modelos visualmente.',
    'Define a textual language, its appearance and the help shown while editing.': 'Define un lenguaje textual, su aspecto y la ayuda que se muestra durante la edición.',
    'Use the same language through visual and textual editors.': 'Utiliza el mismo lenguaje mediante editores visuales y textuales.',
    'Create models and add rules that detect and help correct problems.': 'Crea modelos y añade reglas que detectan problemas y ayudan a corregirlos.',
    'Convert models from one structure into another.': 'Convierte modelos de una estructura a otra.',
    'Generate text, documentation or code from your models.': 'Genera texto, documentación o código a partir de tus modelos.',
    'Read structured text and build models from it.': 'Lee texto estructurado y crea modelos a partir de él.',
    'Prepare a language with editing, validation and user assistance.': 'Prepara un lenguaje con edición, validación y ayuda al usuario.',
    'Enable the complete modeling workflow from definition to generation and delivery.': 'Activa el flujo completo de modelado, desde la definición hasta la generación y entrega.',
    'project areas': 'áreas del proyecto',
    'Project areas': 'Áreas del proyecto',
    'Choose the parts of HYDRA that this project needs. Related areas are added automatically when required.': 'Elige las partes de HYDRA que necesita este proyecto. Las áreas relacionadas se añaden automáticamente cuando son necesarias.',
    'Needs': 'Necesita',
    'Could not load project areas': 'No se han podido cargar las áreas del proyecto',
    'Could not update project areas': 'No se han podido actualizar las áreas del proyecto',
    'Existing files are never deleted when an area is disabled. You can enable it again later without losing your work.': 'Los archivos existentes nunca se eliminan al desactivar un área. Puedes volver a activarla más adelante sin perder tu trabajo.',
    'Initial project type': 'Tipo inicial del proyecto',
    'Content type': 'Tipo de contenido',
    'Main information and current configuration for this HYDRA project.': 'Información principal y configuración actual de este proyecto HYDRA.',
    'Use recommended areas': 'Usar áreas recomendadas',
    'Choose project areas': 'Elegir áreas del proyecto',
    'Initial project areas': 'Áreas iniciales del proyecto',
    'Only the folders needed by these areas are created. You can add more areas later.': 'Solo se crean las carpetas necesarias para estas áreas. Puedes añadir más áreas después.',
    'The project starts empty. Add areas later from the project menu.': 'El proyecto comienza vacío. Añade áreas más adelante desde el menú del proyecto.',
    'Choose what you want to build. This only prepares the starting point: you can add or remove project areas later.': 'Elige qué quieres construir. Esto solo prepara el punto de partida: podrás añadir o quitar áreas del proyecto después.',
    'Concepts and structure': 'Conceptos y estructura',
    'Model editing': 'Edición de modelos',
    'Textual models': 'Modelos textuales',
    'Visual models': 'Modelos visuales',
    'Editor configuration': 'Configuración del editor',
    'Visual editor': 'Editor visual',
    'Textual editor': 'Editor textual',
    'Text appearance': 'Aspecto del texto',
    'Contextual help': 'Ayuda contextual',
    'Quality rules': 'Reglas de calidad',
    'Suggested corrections': 'Correcciones sugeridas',
    'Events': 'Eventos',
    'Event flows': 'Flujos de eventos',
    'Gamification': 'Gamificación',
    'Model-to-model transformations': 'Transformaciones entre modelos',
    'Generate reusable editors': 'Generar editores reutilizables',
    'External integrations': 'Integraciones externas',
    'Repositories and cloud storage': 'Repositorios y almacenamiento en la nube',
    'Project files': 'Archivos del proyecto',
    'Model operations': 'Operaciones sobre modelos',
    'Language design': 'Diseño del lenguaje',
    'Define the concepts, editing rules and behaviour of your language.': 'Define los conceptos, las reglas de edición y el comportamiento de tu lenguaje.',
    'Work with models': 'Trabajar con modelos',
    'Create models, connect external locations and keep generated projects synchronized.': 'Crea modelos, conecta ubicaciones externas y mantén sincronizados los proyectos generados.',
    'Import and share': 'Importar y compartir',
    'Bring existing work into HYDRA, package editors and prepare them for delivery.': 'Trae trabajo existente a HYDRA, empaqueta editores y prepáralos para su entrega.',
    'Project status': 'Estado del proyecto',
    'Check what is ready, which external tools are available and what HYDRA has generated.': 'Comprueba qué está preparado, qué herramientas externas están disponibles y qué ha generado HYDRA.',
    'Project workflow': 'Flujo del proyecto',
    'Move through the project areas in the order that best fits your work.': 'Recorre las áreas del proyecto en el orden que mejor se adapte a tu trabajo.',
    'Project preparation': 'Preparación del proyecto',
    'Review which parts of the project are ready and what still needs attention.': 'Revisa qué partes del proyecto están preparadas y cuáles necesitan atención.',
    'Available tools': 'Herramientas disponibles',
    'Check that the external tools needed by the project are installed and working correctly.': 'Comprueba que las herramientas externas que necesita el proyecto están instaladas y funcionan correctamente.',
    'Generated projects': 'Proyectos generados',
    'Create, check, locate and open the projects generated from HYDRA.': 'Crea, comprueba, localiza y abre los proyectos generados desde HYDRA.',
    'Generated files and history': 'Archivos generados e historial',
    'Generated files': 'Archivos generados',
    'Review generated files, their origin, dependencies and synchronization status.': 'Revisa los archivos generados, su origen, sus dependencias y su estado de sincronización.',
    'Could not load project status': 'No se ha podido cargar el estado del proyecto',
    'Project status unavailable': 'Estado del proyecto no disponible',
    'percent ready': 'por ciento preparado',
    'areas ready': 'áreas preparadas',
    'Open area': 'Abrir área',
    'Configure area': 'Configurar área',
    'Some project areas need attention': 'Algunas áreas del proyecto necesitan atención',
    'Current language or model definition': 'Lenguaje o definición de modelo actual',
    'Generated editor and execution': 'Editor generado y ejecución',
    'No generated version yet': 'Todavía no hay una versión generada',
    'This summary is calculated from the saved grammar and its latest generated version. Missing project status information does not block work.': 'Este resumen se calcula a partir de la gramática guardada y su última versión generada. La falta de información agregada sobre el estado del proyecto no impide seguir trabajando.',
    'Load the project files and models into the current HYDRA workspace': 'Cargar los archivos y modelos del proyecto en el espacio de trabajo actual de HYDRA',
    'Set as main component': 'Establecer como componente principal',
    'Project areas…': 'Áreas del proyecto…',
    'ETag': 'ETag',
    'File version': 'Versión del archivo',
    'Project purpose': 'Finalidad del proyecto',
    'Example project directory': '...\\MiProyecto',
    'Could not duplicate project entries': 'No se han podido duplicar los elementos del proyecto',
    'Project explorer refreshed.': 'Explorador del proyecto actualizado.',
    'Could not refresh project explorer': 'No se ha podido actualizar el explorador del proyecto',
    'Project areas updated.': 'Áreas del proyecto actualizadas.',
    'Could not copy relative path': 'No se ha podido copiar la ruta relativa',
    'Could not set the main project component': 'No se ha podido establecer el componente principal del proyecto',
    'Create a new project or open/import one to see its files and project elements here.': 'Crea un proyecto nuevo o abre/importa uno para ver aquí sus archivos y elementos.',
    'Choose a section': 'Elige una sección',
    'The Home screen shows one section at a time to reduce visual load.': 'La pantalla de inicio muestra una sección cada vez para reducir la carga visual.',
    'Quick start': 'Inicio rápido',
    'Choose what you want to work on and keep the rest of the workspace out of view.': 'Elige en qué quieres trabajar y mantén el resto del entorno fuera de la vista.',
    'Selected section': 'Sección seleccionada',
    'No metamodel selected': 'No hay ningún metamodelo seleccionado',
    'Choose a metamodel to review its readiness.': 'Selecciona un metamodelo para revisar su preparación.',
    'Model editor': 'Editor de modelos',
    'Models': 'Modelos',
    'Create and edit model instances using a selected metamodel and notation.': 'Crea y edita instancias de modelos usando un metamodelo y una notación seleccionados.',
    'Metamodel designer': 'Diseñador de metamodelos',
    'Metamodel': 'Metamodelo',
    'Create, import and evolve the neutral MetaModel independently.': 'Crea, importa y evoluciona de forma independiente el metamodelo neutral.',
    'Validation and quick-fix designer': 'Diseñador de validaciones y correcciones rápidas',
    'Validations': 'Validaciones',
    'Semantic connection object': 'Objeto de conexión semántica',
    'This imported connection is a real model element, not a direct reference. HYDRA preserves its endpoint features instead of inventing a different metamodel relationship.': 'Esta conexión importada es un elemento real del modelo, no una referencia directa. HYDRA conserva sus propiedades de origen y destino sin inventar una relación distinta en el metamodelo.',
    'Visual layers': 'Capas visuales',
    'Visual filters': 'Filtros visuales',
    'Semantic connection object ·': 'Objeto de conexión semántica ·',
    'This imported action creates a model element that owns explicit source and target references. HYDRA preserves that structure and does not convert it into a different direct relationship.': 'Esta acción importada crea un elemento del modelo con referencias explícitas de origen y destino. HYDRA conserva esa estructura y no la convierte en una relación directa diferente.',
    'Choose the visual mapping for this semantic connection object.': 'Elige el mapeo visual para este objeto de conexión semántica.',
    'No compatible semantic-edge mapping': 'No hay ningún mapeo compatible para esta conexión semántica',
    'Imported semantic connection objects are recreated transactionally instead of mutating endpoints in place until their external reconnect semantics are proven equivalent.': 'Los objetos de conexión semántica importados se recrean de forma transaccional, en lugar de modificar sus extremos directamente, hasta demostrar que la semántica externa de reconexión es equivalente.',
    'Layers and filters': 'Capas y filtros',
    'Default layer': 'Capa predeterminada',
    'Active when the view opens': 'Activa al abrir la vista',
    'Optional layer preserved but inactive by default': 'Capa opcional conservada pero inactiva de forma predeterminada',
    'mapping(s) ·': 'mapeo(s) ·',
    'Canonical filter condition': 'Condición de filtro',
    'External condition preserved for review; HYDRA does not execute it': 'Condición externa conservada para revisión; HYDRA no la ejecuta',
    'mapping(s)': 'mapeo(s)',
    'Semantic edge indexes': 'Índices de aristas semánticas',
    'External/review filters': 'Filtros externos o pendientes de revisión',
    'HYDRA inventories the complete archive and nested ZIP/JAR/WAR/EAR containers, identifies the primary metamodel and invokes the matching safe adapters for editors/representations, validations and quick fixes. Imported source code is inspected statically and is never executed.': 'HYDRA inventaría el archivo completo y los contenedores ZIP/JAR/WAR/EAR anidados, identifica el metamodelo principal e invoca adaptadores seguros para editores/representaciones, validaciones y correcciones rápidas. El código fuente importado se inspecciona de forma estática y nunca se ejecuta.',
    'When you import, exact declarative evidence is materialized automatically. Ambiguous semantics remain preserved for review instead of being guessed.': 'Al importar, la evidencia declarativa exacta se materializa automáticamente. La semántica ambigua se conserva para revisión en lugar de inferirse de forma especulativa.',
    'Imported quick fix pending mapping': 'Corrección rápida importada pendiente de mapeo',
    'HYDRA preserved the original repair body without inventing executable semantics. Map it to declarative operations before enabling it.': 'HYDRA ha conservado el cuerpo original de la reparación sin inventar una semántica ejecutable. Mapéalo a operaciones declarativas antes de habilitarlo.',
    'External repair body is preserved for review': 'El cuerpo externo de la reparación se conserva para revisión',
    'Define validation rules, diagnostics, expressions and repairs.': 'Define reglas de validación, diagnósticos, expresiones y reparaciones.',
    'Event architecture': 'Arquitectura de eventos',
    'Define event contracts, bindings and their visual architecture.': 'Define contratos de eventos, enlaces y su arquitectura visual.',
    'EventFlow designer': 'Diseñador de EventFlow',
    'Create event-driven processes independently from gamification.': 'Crea procesos dirigidos por eventos de forma independiente de la gamificación.',
    'Gamification designer': 'Diseñador de gamificación',
    'Configure verified evidence, points, penalties, levels and rewards.': 'Configura evidencias verificadas, puntos, penalizaciones, niveles y recompensas.',
    'Model editor definitions': 'Definiciones de editores de modelos',
    'Editors': 'Editores',
    'Define viewpoints, representations, palettes, forms and runtime previews.': 'Define viewpoints, representaciones, paletas, formularios y vistas previas de ejecución.',
    'Technology integrations': 'Integraciones tecnológicas',
    'Integrations': 'Integraciones',
    'Configure adapters and mappings for external technologies.': 'Configura adaptadores y mappings para tecnologías externas.',
    'Capability readiness': 'Preparación de capacidades',
    'Review which portable layers are configured and what remains.': 'Revisa qué capas portables están configuradas y cuáles faltan.',
    'Native verification center': 'Centro de verificación nativa',
    'Verification': 'Verificación',
    'Run safe native toolchain probes and inspect compatibility evidence.': 'Ejecuta comprobaciones nativas seguras y revisa evidencias de compatibilidad.',
    'Round-trip synchronization': 'Sincronización de ida y vuelta',
    'Round-trip': 'Ida y vuelta',
    'Preview conflicts, decide per element, apply safely and roll back.': 'Previsualiza conflictos, decide por elemento, aplica de forma segura y revierte.',
    'Standalone distribution center': 'Centro de distribuciones independientes',
    'Distribution': 'Distribución',
    'Prepare, validate and package PWA or Tauri editor distributions.': 'Prepara, valida y empaqueta distribuciones de editores PWA o Tauri.',
    'Generated project lifecycle': 'Ciclo de vida de proyectos generados',
    'Generate, validate, locate and launch native or standalone projects through bounded profiles.': 'Genera, valida, localiza y ejecuta proyectos nativos o independientes mediante perfiles acotados.',
    'Artifact registry': 'Registro de artefactos',
    'Artifacts': 'Artefactos',
    'Trace neutral artifacts, versions, checksums, dependencies and synchronization readiness.': 'Traza artefactos neutrales, versiones, checksums, dependencias y preparación para la sincronización.',
    'Representable imports': 'Importaciones representables',
    'Imports': 'Importaciones',
    'Reconstruct supported external changes and diagnose constructs that cannot be represented neutrally.': 'Reconstruye cambios externos compatibles y diagnostica construcciones que no pueden representarse de forma neutral.',
    'Eclipse update site recovery': 'Recuperación de update sites de Eclipse',
    'Update site': 'Update site',
    'Audit p2 repositories, compare bundle releases, select individual recovered layers, interpret custom extensions and preserve evidence for every imported artifact.': 'Audita repositorios p2, compara versiones de bundles, selecciona capas recuperadas, interpreta extensiones personalizadas y conserva evidencias de cada artefacto importado.',
    'Publication and updates': 'Publicación y actualizaciones',
    'Publication': 'Publicación',
    'Validate and publish distribution manifests with platform-specific update channels.': 'Valida y publica manifiestos de distribución con canales de actualización específicos de cada plataforma.',
    'Choose a capability, continue a project or review its readiness.': 'Elige una capacidad, continúa un proyecto o revisa su preparación.',
    'Configure typography and responsive sizing for the complete application.': 'Configura la tipografía y el tamaño adaptable de toda la aplicación.',
    'Configure language, typography and responsive sizing for the complete application.': 'Configura el idioma, la tipografía y el tamaño adaptable de toda la aplicación.',
    'Reset defaults': 'Restablecer valores predeterminados',
    'Cancel': 'Cancelar',
    'Apply settings': 'Aplicar configuración',
    'Application font': 'Fuente de la aplicación',
    'Font family': 'Familia tipográfica',
    'Responsive sizing': 'Tamaño adaptable',
    'Adapt sizes automatically to screen': 'Adaptar los tamaños automáticamente a la pantalla',
    'Preserves your selected sizes as the baseline and scales fonts, controls and spacing within safe limits.': 'Conserva los tamaños seleccionados como base y adapta fuentes, controles y espacios dentro de límites seguros.',
    'Font sizes': 'Tamaños de fuente',
    'General application': 'Aplicación general',
    'Windows and panels': 'Ventanas y paneles',
    'Tabs and command bars': 'Pestañas y barras de comandos',
    'Forms and fields': 'Formularios y campos',
    'Diagram cards and palettes': 'Tarjetas de diagramas y paletas',
    'Tooltips and guides': 'Tooltips y guías',
    'Preview': 'Vista previa',
    'Forms and fields use the selected form size.': 'Los formularios y campos usan el tamaño seleccionado.',
    'Example field': 'Campo de ejemplo',
    'Editable project setting': 'Configuración editable del proyecto',
    'Diagram card': 'Tarjeta de diagrama',
    'Palette descriptions and visual cards use this size.': 'Las descripciones de paleta y las tarjetas visuales usan este tamaño.',
    'Tooltip preview': 'Vista previa del tooltip',
    'Help dialogs and field explanations use this size.': 'Los diálogos de ayuda y las explicaciones de campos usan este tamaño.',
    'Compact': 'Compacto',
    'Comfortable': 'Cómodo',
    'Large': 'Grande',
    'Recommended': 'Recomendado',
    'Serif': 'Con serifas',
    'Monospace': 'Monoespaciada',
    'Sans-serif': 'Sin serifas',
    'Compact density for large workspaces.': 'Densidad compacta para espacios de trabajo grandes.',
    'Balanced size for ordinary desktop use.': 'Tamaño equilibrado para un uso habitual en escritorio.',
    'Large and highly readable text for long editing sessions.': 'Texto grande y muy legible para sesiones de edición prolongadas.',
    'Uses the operating-system interface font and adapts naturally to Windows, macOS and Linux.': 'Usa la fuente de interfaz del sistema operativo y se adapta a Windows, macOS y Linux.',
    'Windows-oriented interface font with high readability at ordinary sizes.': 'Fuente de interfaz orientada a Windows con alta legibilidad en tamaños habituales.',
    'Widely available sans-serif font with compact proportions.': 'Fuente sin serifas ampliamente disponible y de proporciones compactas.',
    'Wide sans-serif font designed for screen readability.': 'Fuente sin serifas ancha diseñada para facilitar la lectura en pantalla.',
    'Compact interface-oriented sans-serif font.': 'Fuente sin serifas compacta y orientada a interfaces.',
    'Serif font suited to users who prefer stronger letter shapes.': 'Fuente con serifas para quienes prefieren formas de letra más marcadas.',
    'Fixed-width font useful for technical values and source-oriented work.': 'Fuente de ancho fijo útil para valores técnicos y trabajo orientado a código.',
    'Version': 'Versión', 'Duplicate': 'Duplicar', 'Clear': 'Limpiar', 'Enumeration': 'Enumeración', 'Enumerations': 'Enumeraciones',
    'Problems': 'Problemas', 'Undo': 'Deshacer', 'Redo': 'Rehacer', 'Off': 'Desactivado', 'Warnings': 'Advertencias',
    'Browse': 'Examinar', 'Confidence': 'Confianza', 'Example': 'Ejemplo', 'Properties': 'Propiedades', 'Editor ID': 'ID del editor',
    'Order': 'Orden', 'Diamond': 'Rombo', 'Solid': 'Sólido', 'Dashed': 'Discontinuo', 'Dotted': 'Punteado',
    'Connections': 'Conexiones', 'Auto layout': 'Disposición automática', 'Fit': 'Ajustar', 'Problem resolved': 'Problema resuelto',
    'Operator': 'Operador', 'Ordered': 'Ordenado', 'Unique': 'Único', 'Parameters': 'Parámetros', 'Ungrouped': 'Sin agrupar',
    'Reload': 'Recargar', 'Dependency cycles': 'Ciclos de dependencias', 'Understood': 'Entendido', 'Data types': 'Tipos de datos',
    'Materialization': 'Materialización', 'Corner radius': 'Radio de las esquinas', 'Date': 'Fecha', 'Arrow': 'Flecha', 'Circle': 'Círculo',
    'On canvas': 'En el lienzo', 'Visual connections': 'Conexiones visuales', 'Payload mappings': 'Mappings de payload',
    'Mapping': 'Mapping', 'Namespace': 'Espacio de nombres', 'Allowed subjects': 'Sujetos permitidos', 'Check': 'Comprobar',
    'Transitions': 'Transiciones', 'Variables': 'Variables', 'No timeout transition': 'Sin transición por tiempo de espera',
    'Scheduled retries': 'Reintentos programados', 'Merge': 'Fusionar', 'Effect': 'Efecto', 'Reason': 'Motivo',
    'Achievement': 'Logro', 'Challenge': 'Desafío', 'No achievement': 'Sin logro', 'No challenge': 'Sin desafío',
    'Progression': 'Progresión', 'Injection policy': 'Política de inyección', 'Injection preview': 'Vista previa de inyección',
    'Why:': 'Motivo:', 'Alternative:': 'Alternativa:', 'Delivery mode': 'Modo de entrega', 'Technology': 'Tecnología',
    'Host module': 'Módulo anfitrión', 'Build system': 'Sistema de compilación', 'Strategy': 'Estrategia', 'Mapping ID': 'ID del mapping',
    'Locator strategy': 'Estrategia de localización', 'Right': 'Derecha', 'Left': 'Izquierda', 'Format': 'Formato',
    'Automatic detection': 'Detección automática', 'Aggregation': 'Agregación', 'Containment': 'Contención',
    'Dependency': 'Dependencia', 'Inheritance': 'Herencia', 'Implementation': 'Implementación', 'Parameter': 'Parámetro',
    'Search problems': 'Buscar problemas', 'Errors': 'Errores', 'Rename': 'Renombrar', 'Upload': 'Subir',
    'Product ID': 'ID del producto', 'Channel': 'Canal', 'Internal': 'Interno', 'Beta': 'Beta', 'Strict': 'Estricto',
    'Mappings': 'Mappings', 'Partial': 'Parcial', 'Recovered as': 'Recuperado como', 'Severity': 'Severidad',
    'Do not grant a separate reward.': 'No conceder una recompensa independiente.',
    'Create': 'Crear', 'Open': 'Abrir', 'Save': 'Guardar', 'Delete': 'Eliminar', 'Edit': 'Editar', 'Close': 'Cerrar',
    'Export': 'Exportar', 'Apply': 'Aplicar', 'Reset': 'Restablecer', 'Refresh': 'Actualizar',
    'Search': 'Buscar', 'Select': 'Seleccionar', 'Add': 'Añadir', 'Remove': 'Quitar',
    'Back': 'Atrás', 'Next': 'Siguiente', 'Previous': 'Anterior', 'Finish': 'Finalizar', 'Retry': 'Reintentar',
    'Name': 'Nombre', 'Description': 'Descripción', 'Type': 'Tipo', 'Value': 'Valor', 'Status': 'Estado',
    'Required': 'Obligatorio', 'Optional': 'Opcional', 'Enabled': 'Activado', 'Disabled': 'Desactivado', 'Requirement complete': 'Requisito cumplido', 'Requirement incomplete': 'Requisito pendiente',
    'Yes': 'Sí', 'No': 'No', 'None': 'Ninguno', 'Other': 'Otros', 'Details': 'Detalles', 'Affected resource': 'Recurso afectado',
    'Loading…': 'Cargando…', 'Saving…': 'Guardando…', 'Analyzing…': 'Analizando…', 'No results': 'Sin resultados',
    'Available values': 'Valores disponibles', 'Information': 'Información', 'Warning': 'Advertencia', 'Error': 'Error',
    'Success': 'Correcto', 'Actions': 'Acciones', 'General': 'General', 'Advanced': 'Avanzado', 'Help': 'Ayuda',
    ...APPLICATION_SPANISH,
    ...DESIGN_SPANISH,
    ...DELIVERY_SPANISH,
    ...DOMAIN_SPANISH,
    ...EXTENDED_SPANISH,
    ...PRODUCTION_SPANISH,
    ...GUIDED_SPANISH,
    ...INTERCHANGE_SPANISH,
    ...CURRENT_SPANISH,
    ...GRAMMAR_SPANISH,
    ...TRANSFORMATION_SPANISH,
    ...SPANISH_CONFLICT_RESOLUTIONS,
    ...HOME_SHELL_SPANISH,
    ...INTERFACE_AUDIT_SPANISH,
};
export function translateUiText(input: string, language: HydraLanguage): string {
    if (input.trim() === '') return input;
    if (input.includes('\n')) {
        return input.split('\n').map(line => translateUiText(line, language)).join('\n');
    }
    if (language === 'en') return presentVisibleTerminology(input, language);
    return presentVisibleTerminology(translateSingleLine(input), language);
}
/**
 * Translates text produced at runtime by backend rules/checkers. Unknown runtime
 * messages use the Spanish catalogue when available. If a newly introduced
 * backend diagnostic has not been catalogued yet, the visible fallback keeps
 * the original detailed message instead of replacing it with a generic sentence;
 * losing the concrete rule, ID or suggested value would make the diagnostic useless.
 */
export function translateRuntimeUiText(input: string, language: HydraLanguage, context = 'operation', code = '', severity = ''): string {
    const translated = translateUiText(input, language);
    if (language !== 'es' || translated !== input || input.trim() === '') return translated;
    // Transformation diagnostics are deliberately rich and element-specific. If a
    // newly introduced message has no Spanish pattern yet, preserve it verbatim
    // rather than prepending the old generic "Incidencia de..." boilerplate.
    // The designer already renders localized labels for DÓNDE / POR QUÉ / CÓMO,
    // so retaining rule IDs, feature IDs, values and repair examples is more useful
    // than a partially translated sentence that obscures the actionable evidence.
    if (isTransformationDiagnosticCode(code)) return input;
    const label = runtimeContextLabel(context);
    const normalizedSeverity = severity.trim().toUpperCase();
    const prefix = normalizedSeverity === 'INFO' ? 'Información' : normalizedSeverity === 'WARNING' || normalizedSeverity === 'WARN' ? 'Aviso' : 'Incidencia';
    const codeSuffix = code.trim() === '' ? '' : ` (${code.trim()})`;
    return `${prefix} de ${label}${codeSuffix}. ${input}`;
}

const TRANSFORMATION_DIAGNOSTIC_CODES = new Set([
    'TRANSFORMATION_REQUIRED','UNSUPPORTED_SCHEMA_VERSION','TRANSFORMATION_ID_REQUIRED','TRANSFORMATION_NAME_REQUIRED',
    'NON_DETERMINISTIC_EXECUTION_NOT_SUPPORTED','BEST_EFFORT_BINDING_EXECUTION_NOT_SUPPORTED','BIDIRECTIONAL_ONLY_M2M',
    'BIDIRECTIONAL_TRACE_REQUIRED','SOURCE_REQUIRED','TARGET_REQUIRED','GENERATION_RULE_REQUIRED','GRAMMAR_REQUIRED',
    'ENDPOINT_ID_REQUIRED','ENDPOINT_ID_DUPLICATE','ENDPOINT_ROLE_INVALID','ENDPOINT_METAMODEL_REQUIRED','ENDPOINT_METAMODEL_MISMATCH',
    'GENERATION_RULE_ID_REQUIRED','GENERATION_RULE_ID_DUPLICATE','GENERATION_CONTEXT_REQUIRED','GENERATION_CONTEXT_NOT_FOUND',
    'OUTPUT_PATH_REQUIRED','RULE_ID_REQUIRED','RULE_ID_DUPLICATE','RULE_NAME_REQUIRED','RULE_SOURCE_ENDPOINT_REQUIRED',
    'RULE_SOURCE_ENDPOINT_NOT_FOUND','SOURCE_ALIAS_REQUIRED','SOURCE_TYPE_REQUIRED','UNKNOWN_SOURCE_TYPE','TARGET_PATTERN_REQUIRED',
    'TARGET_ALIAS_REQUIRED','TARGET_ALIAS_DUPLICATE','TARGET_ENDPOINT_REQUIRED','TARGET_ENDPOINT_NOT_FOUND','TARGET_TYPE_REQUIRED',
    'UNKNOWN_TARGET_TYPE','BINDING_ID_REQUIRED','BINDING_ID_DUPLICATE','BINDING_TARGET_ALIAS_REQUIRED','BINDING_TARGET_ALIAS_NOT_FOUND',
    'BINDING_TARGET_FEATURE_REQUIRED','UNKNOWN_TARGET_ATTRIBUTE','UNKNOWN_TARGET_RELATION','BINDING_SOURCE_FEATURE_REQUIRED',
    'UNKNOWN_SOURCE_ATTRIBUTE','UNKNOWN_SOURCE_RELATION','TYPE_CONVERSION_REQUIRED','COLLECTION_REDUCTION_REQUIRED',
    'EXPLICIT_VALUE_CONVERSION','REQUIRED_TARGET_ATTRIBUTE_UNMAPPED','REVERSE_RULE_REQUIRES_BIDIRECTIONAL',
    'BIDIRECTIONAL_RULE_PAIR_REQUIRED','CORRESPONDENCE_ID_REQUIRED','BIDIRECTIONAL_REVERSE_RULE_REQUIRED',
    'INCOMPLETE_BIDIRECTIONAL_CORRESPONDENCE','ENDPOINT_METAMODEL_CHANGED','DIRECTION_NOT_SUPPORTED','REVERSE_REQUIRES_BIDIRECTIONAL',
    'UNSUPPORTED_KIND','BIDIRECTIONAL_NOT_NATIVE','METAMODELS_REQUIRED','SOURCE_METAMODEL_REQUIRED','GUARD_NOT_NATIVE',
    'MULTI_TARGET_NOT_NATIVE','RULE_TYPE_NOT_FOUND','BINDING_NOT_NATIVE','VALUE_POLICY_NOT_NATIVE','BINDING_FEATURE_NOT_FOUND',
    'DIRECT_BINDING_NOT_EXACT','CONSTANT_COLLECTION_NOT_NATIVE','CONTEXT_TYPE_NOT_FOUND','CANONICAL_ID_TOKEN_NOT_NATIVE',
    'TEMPLATE_ATTRIBUTE_NOT_FOUND','CANONICAL_SERIALIZATION_FAILED','NATIVE_PLAN_SERIALIZATION_FAILED','NATIVE_COLLISION_POLICY_REQUIRES_ENGINE_GUARD'
]);
function isTransformationDiagnosticCode(code: string): boolean {
    const normalized=code.trim().toUpperCase();
    return TRANSFORMATION_DIAGNOSTIC_CODES.has(normalized) || normalized.startsWith('GUARD_') || normalized.startsWith('BINDING_') || normalized.startsWith('GENERATION_GUARD_') || normalized.startsWith('CANONICAL_');
}
function translateSingleLine(input: string): string {
    const leading = input.match(/^\s*/)?.[0] ?? '';
    const trailing = input.match(/\s*$/)?.[0] ?? '';
    const text = input.trim();
    if (text === '') return input;

    // Explicit catalogue entries win before technical-name protection. This
    // allows safe phrases such as “Run on Windows” while keeping the proper
    // name Windows unchanged when it appears on its own.
    const exact = EXACT_SPANISH[text];
    if (exact !== undefined) return `${leading}${exact}${trailing}`;

    // Controlled patterns run before generic technical-text protection so an
    // actionable diagnostic may translate its fixed prefix while preserving
    // the path, identifier or proper name contained in the remainder.
    const dynamic = translateDynamic(text);
    if (dynamic !== null) return `${leading}${dynamic}${trailing}`;
    if (isProtectedText(text)) return input;

    // Unknown copy is preserved as one complete English string. HYDRA never
    // builds Spanish text from isolated words because that produces unnatural
    // labels and can corrupt proper names or technical terminology.
    return input;
}

function translateDynamic(text: string): string | null {
    const v43270 = translateCurrentRuntimeSpanish(text); if (v43270 !== null) return v43270;
    let transformation = text.match(/^Missing (FORWARD|REVERSE) rule for correspondence '(.+)'$/);
    if (transformation !== null) return `Falta la regla ${transformation[1] === 'FORWARD' ? 'directa (FORWARD)' : 'inversa (REVERSE)'} para la correspondencia '${transformation[2]}'`;
    transformation = text.match(/^Correspondence '(.+)' is incomplete\. Existing rule '(.+)' \(id '(.+)'\) is (FORWARD|REVERSE) from source type '(.+)' to target type\(s\) '(.+)', but no enabled (FORWARD|REVERSE) rule uses the same correspondence ID\.$/);
    if (transformation !== null) return `La correspondencia '${transformation[1]}' está incompleta. La regla existente '${transformation[2]}' (id '${transformation[3]}') es ${transformation[4] === 'FORWARD' ? 'directa (FORWARD)' : 'inversa (REVERSE)'}, transforma el tipo origen '${transformation[5]}' en '${transformation[6]}', pero no existe ninguna regla ${transformation[7] === 'FORWARD' ? 'directa (FORWARD)' : 'inversa (REVERSE)'} activa con el mismo identificador de correspondencia.`;
    transformation = text.match(/^Create an enabled (FORWARD|REVERSE) rule with correspondenceId='(.+)'\. Example: name='(.+)', direction=(FORWARD|REVERSE), sourceTypeId='(.+)', target type='(.+)'\. Then map the inverse attributes\/relations explicitly; HYDRA will not guess inverse bindings\.$/);
    if (transformation !== null) return `Crea una regla ${transformation[1] === 'FORWARD' ? 'directa (FORWARD)' : 'inversa (REVERSE)'} activa con correspondenceId='${transformation[2]}'. Por ejemplo: name='${transformation[3]}', direction=${transformation[4]}, sourceTypeId='${transformation[5]}' y tipo destino='${transformation[6]}'. Después define de forma explícita los mapeos inversos de atributos y relaciones; HYDRA no inventará esos enlaces.`;
    transformation = text.match(/^A bidirectional transformation needs a rule pair$/);
    if (transformation !== null) return 'Una transformación bidireccional necesita al menos una pareja de reglas';
    transformation = text.match(/^There are no enabled rules\. HYDRA needs at least one FORWARD rule and its explicit REVERSE partner to execute a bidirectional M2M transformation\.$/);
    if (transformation !== null) return 'No hay ninguna regla activa. Para ejecutar una transformación M2M bidireccional, HYDRA necesita al menos una regla directa (FORWARD) y su pareja inversa (REVERSE) definida de forma explícita.';
    transformation = text.match(/^Create a FORWARD rule, assign a correspondence ID, then create a REVERSE rule with that same correspondence ID\.$/);
    if (transformation !== null) return 'Crea una regla directa (FORWARD), asígnale un identificador de correspondencia y crea después una regla inversa (REVERSE) con exactamente el mismo identificador.';
    const activity = text.match(/^(Loading|Saving|Refreshing|Deleting|Importing|Exporting|Generating|Analyzing|Checking|Preparing) (.+?)(…|\.\.\.)?$/);
    if (activity !== null) {
        const verbs: Readonly<Record<string, string>> = { Loading: 'Cargando', Saving: 'Guardando', Refreshing: 'Actualizando', Deleting: 'Eliminando', Importing: 'Importando', Exporting: 'Exportando', Generating: 'Generando', Analyzing: 'Analizando', Checking: 'Comprobando', Preparing: 'Preparando' };
        return `${verbs[activity[1]] ?? activity[1]} ${activity[2]}${activity[3] ?? ''}`;
    }
    const integrationOption = text.match(/^Uses the (.+) option supported by the selected target\.$/);
    if (integrationOption !== null) return `Usa la opción ${integrationOption[1]} compatible con el destino seleccionado.`;
    let match = text.match(/^Skipped invalid (gamification|validation|event|event-flow|editor|integration) document (.+): Could not parse document: (.+)$/i);
    if (match !== null) {
        const kinds: Record<string, string> = { gamification: 'gamificación', validation: 'validación', event: 'eventos', 'event-flow': 'flujo de eventos', editor: 'editor', integration: 'integración' };
        return `Se omitió el documento no válido de ${kinds[match[1].toLowerCase()] ?? match[1]} ${match[2]}: no se pudo interpretar el documento: ${match[3]}`;
    }
    match = text.match(/^Could not parse document: (.+)$/);
    if (match !== null) return `No se pudo interpretar el documento: ${match[1]}`;
    match = text.match(/^No compatible containment reference was found for (.+)$/);
    if (match !== null) return `No se encontró una referencia de contención compatible para ${match[1]}`;
    match = text.match(/^HYDRA could not read the JSON entered in (.+)\.$/);
    if (match !== null) return `HYDRA no pudo leer el JSON introducido en ${match[1]}.`;
    match = text.match(/^The technical ID entered in (.+) is not a valid document ID\.$/);
    if (match !== null) return `El ID técnico introducido en ${match[1]} no es un ID de documento válido.`;
    match = text.match(/^A required value is missing in (.+)\.$/);
    if (match !== null) return `Falta un valor obligatorio en ${match[1]}.`;
    match = text.match(/^HYDRA rejected a value in (.+) because it does not satisfy the HYDRA data contract\.$/);
    if (match !== null) return `HYDRA ha rechazado un valor en ${match[1]} porque no cumple el contrato de datos de HYDRA.`;
    match = text.match(/^Backend unavailable at (.+)\. Check the configured HYDRA backend and retry\.$/);
    if (match !== null) return `El backend no está disponible en ${match[1]}. Comprueba la configuración del backend de HYDRA y vuelve a intentarlo.`;
    match = text.match(/^An unexpected internal error occurred\. Correlation ID: (.+)$/);
    if (match !== null) return `Se ha producido un error interno inesperado. ID de correlación: ${match[1]}`;
    match = text.match(/^(\d+) root package\(s\)$/);
    if (match !== null) return `${match[1]} paquete(s) raíz`;
    match = text.match(/^(\d+) of (\d+)$/);
    if (match !== null) return `${match[1]} de ${match[2]}`;
    match = text.match(/^(\d+)\/(\d+) complete$/);
    if (match !== null) return `${match[1]}/${match[2]} completados`;
    match = text.match(/^(\d+) trigger\(s\) selected\.$/);
    if (match !== null) return match[1] === '1' ? '1 disparador seleccionado.' : `${match[1]} disparadores seleccionados.`;
    match = text.match(/^(\d+) repair option\(s\) linked\.$/);
    if (match !== null) return match[1] === '1' ? '1 opción de reparación asociada.' : `${match[1]} opciones de reparación asociadas.`;
    match = text.match(/^Step (\d+) of (\d+)$/);
    if (match !== null) return `Paso ${match[1]} de ${match[2]}`;
    match = text.match(/^(Import|Export) workflow$/);
    if (match !== null) return match[1] === 'Import' ? 'Flujo de importación' : 'Flujo de exportación';
    match = text.match(/^(Import|Export) (Validations|Quick fixes|Events and bindings|Event flows)$/);
    if (match !== null) {
        const action = match[1] === 'Import' ? 'Importar' : 'Exportar';
        const layer: Record<string, string> = { 'Validations': 'validaciones', 'Quick fixes': 'correcciones rápidas', 'Events and bindings': 'eventos y bindings', 'Event flows': 'flujos de eventos' };
        return `${action} ${layer[match[2]]}`;
    }
    match = text.match(/^(\d+) class\(es\), (\d+) enum\(s\), (\d+) datatype\(s\)\.$/);
    if (match !== null) return `${match[1]} clase(s), ${match[2]} enumeración(es), ${match[3]} tipo(s) de datos.`;
    match = text.match(/^(\d+) relation\(s\) currently defined\. Relations are optional\.$/);
    if (match !== null) return `${match[1]} relación(es) definida(s) actualmente. Las relaciones son opcionales.`;
    match = text.match(/^(\d+) event\(s\) · (\d+) binding\(s\)$/);
    if (match !== null) return `${match[1]} evento(s) · ${match[2]} enlace(s)`;
    match = text.match(/^(\d+) field\(s\) define the portable evidence carried by this event\.$/);
    if (match !== null) return `${match[1]} campo(s) definen la evidencia portable transportada por este evento.`;
    match = text.match(/^Condition (\d+)$/);
    if (match !== null) return `Condición ${match[1]}`;
    match = text.match(/^number of elements in (.+) is (greater than or equal to|less than or equal to|greater than|less than|equal to|different from) (.+)$/);
    if (match !== null) return `el número de elementos en ${match[1]} ${translateGuidedOperator(match[2])} ${match[3]}`;
    match = text.match(/^number of (.+) elements is (greater than or equal to|less than or equal to|greater than|less than|equal to|different from) (.+)$/);
    if (match !== null) return `el número de elementos ${match[1]} ${translateGuidedOperator(match[2])} ${match[3]}`;
    match = text.match(/^(.+) is (empty or missing|not empty)$/);
    if (match !== null) return match[2] === 'empty or missing' ? `${match[1]} está vacío o ausente` : `${match[1]} no está vacío`;
    match = text.match(/^(.+) is (greater than or equal to|less than or equal to|greater than|less than|equal to|different from|contains|starts with|ends with|matches pattern) (.+)$/);
    if (match !== null) return `${match[1]} ${translateGuidedOperator(match[2])} ${match[3]}`;
    match = text.match(/^(Docker|Kubernetes|standalone source) editor package generated · (\d+) portable capabilities(?: · (\d+) optional layer\(s\) were not available)?( · SHA-256 .+)?$/);
    if (match !== null) {
        const target = match[1] === 'standalone source' ? 'fuente autónoma' : match[1];
        const warning = match[3] === undefined ? '' : ` · ${match[3]} capa(s) opcional(es) no estaban disponibles`;
        return `Paquete de editor ${target} generado · ${match[2]} capacidades portables${warning}${match[4] ?? ''}`;
    }
    match = text.match(/^Field (\d+)$/);
    if (match !== null) return `Campo ${match[1]}`;
    match = text.match(/^Mapping (\d+)$/);
    if (match !== null) return `Mapping ${match[1]}`;
    match = text.match(/^Custom event type ·\s*(.*)$/);
    if (match !== null) return `Tipo de evento personalizado · ${match[1]}`.trimEnd();
    match = text.match(/^System event ·\s*(.*)$/);
    if (match !== null) return `Evento del sistema · ${match[1]}`.trimEnd();
    match = text.match(/^Canonical and custom events for (.+)\. Events remain independent from optional gamification\.$/);
    if (match !== null) return `Eventos de HYDRA y personalizados para ${match[1]}. Los eventos siguen siendo independientes de la gamificación opcional.`;
    match = text.match(/^Visual connections for (.+)$/);
    if (match !== null) return `Conexiones visuales de ${match[1]}`;
    match = text.match(/^Map source evidence into the fields of (.+)\.$/);
    if (match !== null) return `Mapea la evidencia de origen en los campos de ${translateKnownEntity(match[1])}.`;
    match = text.match(/^(.+) · select to edit the binding$/);
    if (match !== null) return `${match[1]} · seleccionar para editar el enlace`;
    match = text.match(/^(\d+) actor\(s\)$/);
    if (match !== null) return `${match[1]} actor(es)`;
    match = text.match(/^(\d+) ledger entries$/);
    if (match !== null) return `${match[1]} entradas del ledger`;
    match = text.match(/^(\d+) achievement\(s\) · (\d+) reward\(s\)$/);
    if (match !== null) return `${match[1]} logro(s) · ${match[2]} recompensa(s)`;
    match = text.match(/^configured · (.+)$/i);
    if (match !== null) return `configurado · ${match[1]}`;
    match = text.match(/^Evidence \((\d+)\)$/);
    if (match !== null) return `Evidencias (${match[1]})`;
    match = text.match(/^(\d+) operation\(s\) · (\d+) diagnostic\(s\)$/);
    if (match !== null) return `${match[1]} operación(es) · ${match[2]} diagnóstico(s)`;
    match = text.match(/^(\d+) file\(s\) selected$/);
    if (match !== null) return `${match[1]} archivo(s) seleccionado(s)`;
    match = text.match(/^(\d+) files inspected · (\d+) recovery candidate\(s\)\.$/);
    if (match !== null) return `${match[1]} archivos inspeccionados · ${match[2]} candidato(s) de recuperación.`;
    match = text.match(/^(\d+) tools · (\d+) representations · (\d+) mappings$/);
    if (match !== null) return `${match[1]} herramientas · ${match[2]} representaciones · ${match[3]} mappings`;
    match = text.match(/^Configure (.+)$/);
    if (match !== null) {
        const layers: Readonly<Record<string, string>> = {
            'validations and quick fixes': 'validaciones y correcciones rápidas',
            'event flows': 'flujos de eventos',
            'gamification': 'gamificación',
            'model editors and representations': 'editores de modelos y representaciones',
            'technology integrations': 'integraciones tecnológicas',
            'events and bindings': 'eventos y vinculaciones',
            'metamodel structure': 'estructura del metamodelo'
        };
        const translated = layers[match[1].toLowerCase()];
        if (translated !== undefined) return `Configura ${translated}`;
    }
    match = text.match(/^Current policy: (.+)\.$/);
    if (match !== null) return `Política actual: ${match[1]}.`;
    match = text.match(/^Current mode: (.+)\.$/);
    if (match !== null) return `Modo actual: ${match[1]}.`;
    match = text.match(/^Current channel: (.+)\.$/);
    if (match !== null) return `Canal actual: ${match[1]}.`;
    match = text.match(/^Controlled by (.+)\.$/);
    if (match !== null) return `Controlado por ${match[1]}.`;
    match = text.match(/^(\d+) parameter\(s\) configured\.$/);
    if (match !== null) return `${match[1]} parámetro(s) configurado(s).`;
    match = text.match(/^(\d+) operation\(s\) configured\.$/);
    if (match !== null) return `${match[1]} operación(es) configurada(s).`;
    match = text.match(/^(\d+) trigger\(s\) selected\.$/);
    if (match !== null) return `${match[1]} disparador(es) seleccionado(s).`;
    match = text.match(/^(\d+) repair option\(s\) linked\.$/);
    if (match !== null) return `${match[1]} opción(es) de reparación vinculada(s).`;
    match = text.match(/^(\d+) representation\(s\)$/);
    if (match !== null) return `${match[1]} representación(es)`;
    match = text.match(/^(\d+) group\(s\) · (\d+) tool\(s\)$/);
    if (match !== null) return `${match[1]} grupo(s) · ${match[2]} herramienta(s)`;
    match = text.match(/^(\d+) generated form\(s\)$/);
    if (match !== null) return `${match[1]} formulario(s) generado(s)`;
    match = text.match(/^(\d+) capability\(ies\)$/);
    if (match !== null) return `${match[1]} capacidad(es)`;
    match = text.match(/^(\d+) open view\(s\) · (\d+) ms operation timeout$/);
    if (match !== null) return `${match[1]} vista(s) abierta(s) · ${match[2]} ms de tiempo límite por operación`;
    match = text.match(/^(\d+) local file\(s\)$/);
    if (match !== null) return `${match[1]} archivo(s) local(es)`;
    match = text.match(/^(\d+) files · (\d+) candidate\(s\)$/);
    if (match !== null) return `${match[1]} archivos · ${match[2]} candidato(s)`;
    match = text.match(/^(\d+) action\(s\) planned\.$/);
    if (match !== null) return `${match[1]} acción(es) planificada(s).`;
    match = text.match(/^(\d+) target\(s\) selected\.$/);
    if (match !== null) return `${match[1]} destino(s) seleccionado(s).`;
    match = text.match(/^(\d+) probe\(s\) selected\.$/);
    if (match !== null) return `${match[1]} comprobación(es) seleccionada(s).`;
    match = text.match(/^(\d+) result\(s\) recorded\.$/);
    if (match !== null) return `${match[1]} resultado(s) registrado(s).`;
    match = text.match(/^(\d+) selected result\(s\) available\.$/);
    if (match !== null) return `${match[1]} resultado(s) seleccionado(s) disponible(s).`;
    match = text.match(/^(\d+) selected$/);
    if (match !== null) return `${match[1]} seleccionado(s)`;
    match = text.match(/^(\d+) of (\d+) identified definition\(s\) selected\.$/);
    if (match !== null) return `${match[1]} de ${match[2]} definición(es) identificada(s) seleccionada(s).`;
    match = text.match(/^(\d+) classes · (\d+) relations\.$/);
    if (match !== null) return `${match[1]} clases · ${match[2]} relaciones.`;
    const diagnosticPrefixes: ReadonlyArray<readonly [string, string]> = [
        ['What went wrong:', 'Qué ha ocurrido:'],
        ['Field:', 'Campo:'],
        ['Why it is invalid:', 'Por qué no es válido:'],
        ['How to fix it:', 'Cómo corregirlo:'],
        ['How to correct it:', 'Cómo corregirlo:'],
        ['Possible checks:', 'Comprobaciones posibles:'],
        ['How to resolve it:', 'Cómo resolverlo:'],
        ['Next step:', 'Siguiente paso:'],
        ['Related section:', 'Sección relacionada:'],
        ['Cause:', 'Causa:'],
        ['Resolution:', 'Resolución:']
    ];
    for (const [english, spanish] of diagnosticPrefixes) {
        if (!text.startsWith(english)) continue;
        const remainder = text.slice(english.length).trim();
        if (remainder === '') return spanish;
        return `${spanish} ${translateDiagnosticBody(remainder)}`;
    }

    match = text.match(/^Could not parse document:\s*(.+)$/);
    if (match !== null) return `No se pudo analizar el documento: ${match[1]}`;
    match = text.match(/^Could not load (.+)$/);
    if (match !== null) return `No se pudo cargar ${translateKnownEntity(match[1])}`;
    match = text.match(/^Could not save (.+)$/);
    if (match !== null) return `No se pudo guardar ${translateKnownEntity(match[1])}`;
    match = text.match(/^Could not initialize (.+)$/);
    if (match !== null) return `No se pudo inicializar ${translateKnownEntity(match[1])}`;
    match = text.match(/^Information about (.+)$/);
    if (match !== null) return `Información sobre ${translateKnownEntity(match[1])}`;
    match = text.match(/^About (.+)$/);
    if (match !== null) return `Acerca de ${translateKnownEntity(match[1])}`;
    return null;
}

function translateDiagnosticBody(text: string): string {
    const exact = EXACT_SPANISH[text];
    if (exact !== undefined) return exact;
    const parse = text.match(/^Could not parse document:\s*(.+)$/);
    if (parse !== null) return `No se pudo analizar el documento: ${parse[1]}`;

    // Actionable errors often join two or more recommendations in the same
    // text node. Translate only complete known sentences and leave unknown
    // prose untouched, avoiding mixed word-by-word output.
    const sentences = text.split(/(?<=\.)\s+/);
    if (sentences.length > 1) {
        const translated = sentences.map(sentence => EXACT_SPANISH[sentence] ?? sentence);
        if (translated.some((sentence, index) => sentence !== sentences[index])) return translated.join(' ');
    }
    return text;
}

function translateKnownEntity(text: string): string {
    const genericEntities: Readonly<Record<string, string>> = {
        model: 'modelo',
        diagram: 'diagrama',
        editor: 'editor',
        metamodel: 'metamodelo',
        notation: 'notación',
        project: 'proyecto',
        workspace: 'espacio de trabajo',
    };
    const generic = genericEntities[text.toLowerCase()];
    if (generic !== undefined) return generic;
    const exact = EXACT_SPANISH[text];
    return exact === undefined ? text : exact.toLowerCase();
}
