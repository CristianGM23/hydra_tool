import { ActionableApiError, formatActionableError } from './actionableError';
import { currentHydraLanguage, localizeErrorText, localizeKnownErrorText } from './errorLocalization';

/** Converts local form, diagram and raw-source errors into the same what/where/why/how format. */
export function explainInputError(error: unknown, fallback: string, context = 'the current form'): string {
    if (error instanceof ActionableApiError) return error.message;
    const rawMessage = error instanceof Error && error.message.trim() !== '' ? error.message.trim() : fallback;
    if (isActionableErrorMessage(rawMessage)) return rawMessage;
    const language = currentHydraLanguage();
    const localizedContext = language === 'es' ? translateContext(context) : context;
    const message = language === 'es' ? localizeErrorText(rawMessage, context) : rawMessage;
    const lower = rawMessage.toLowerCase();

    if (error instanceof SyntaxError || lower.includes('json')) {
        return formatActionableError(
            language === 'es' ? `HYDRA no pudo leer el JSON introducido en ${localizedContext}.` : `HYDRA could not read the JSON entered in ${context}.`,
            inferLocalField(rawMessage),
            message,
            language === 'es' ? 'Comprueba comillas, comas, llaves, corchetes y tipos de valor. Si existe un formulario estructurado equivalente, corrige allí el dato en vez de editar JSON sin validar.' : 'Check quotes, commas, braces, brackets and value types. If an equivalent structured form exists, correct the value there instead of editing unvalidated JSON.',
            [language === 'es' ? 'Los nombres de propiedades y los textos usan comillas dobles. Elimina comas finales y cierra cada llave o corchete abierto.' : 'Property names and text values use double quotes. Remove trailing commas and close every opening brace or bracket.']
        );
    }
    if (lower.includes('stable document id')) {
        return formatActionableError(
            language === 'es' ? `El identificador técnico indicado en ${localizedContext} no es válido.` : `The technical ID entered in ${context} is not a valid document ID.`,
            'id',
            language === 'es' ? 'Debe empezar por una letra o número y solo puede contener letras, números, puntos, guiones bajos y guiones. No admite espacios, dos puntos ni barras.' : 'Document IDs must start with a letter or number and may contain only letters, numbers, dots, underscores and hyphens. Spaces, colons and slashes are not allowed.',
            language === 'es' ? 'Usa un identificador estable como action-model, people.events o validation_rules. Escribe el nombre legible para personas en el campo Nombre.' : 'Use a value such as action-model, people.events or validation_rules. Put the human-readable wording in Name instead.',
            []
        );
    }

    const known = explainKnownLocalFailure(rawMessage, context, language, localizedContext);
    if (known !== null) return known;

    if (lower.includes('required') || lower.includes('must not be blank') || lower.includes('must not be empty')) {
        return formatActionableError(
            language === 'es' ? `Falta un valor obligatorio en ${localizedContext}.` : `A required value is missing in ${context}.`,
            inferLocalField(rawMessage),
            message,
            language === 'es' ? 'Completa el campo marcado con * y, si depende de otro elemento, selecciona primero la referencia necesaria. El mensaje junto al campo indica el valor concreto que falta.' : 'Complete the field marked with * and, if it depends on another element, select the required reference first. The message beside the field identifies the missing value.',
            []
        );
    }
    // Mapping/binding diagnostics must be classified before the generic unknown/not-found branch.
    // A message such as "unknown target feature" is still a mapping error and needs the
    // ordered mapping repair recipe rather than a generic stale-reference explanation.
    if (/mapping|binding|correspondence|source feature|target feature|direction|reverse rule|forward rule/.test(lower)) {
        const bindingId = rawMessage.match(/(?:binding|mapping)\s+id\s*=\s*['"`]?([^'"`\s]+)['"`]?/i)?.[1] ?? '';
        const sourceType = rawMessage.match(/source\s+type\s*['"`:= ]+([^'"`,;\s]+)/i)?.[1] ?? '';
        const targetType = rawMessage.match(/target\s+type\s*['"`:= ]+([^'"`,;\s]+)/i)?.[1] ?? '';
        const sourceFeature = rawMessage.match(/source\s+feature\s*['"`:= ]+([^'"`,;\s]+)/i)?.[1] ?? '';
        const targetFeature = rawMessage.match(/target\s+feature\s*['"`:= ]+([^'"`,;\s]+)/i)?.[1] ?? '';
        const concrete = [
            bindingId ? `${language === 'es' ? 'binding' : 'binding'}=${bindingId}` : '',
            sourceType ? `${language === 'es' ? 'tipo origen' : 'source type'}=${sourceType}` : '',
            targetType ? `${language === 'es' ? 'tipo destino' : 'target type'}=${targetType}` : '',
            sourceFeature ? `${language === 'es' ? 'atributo origen' : 'source feature'}=${sourceFeature}` : '',
            targetFeature ? `${language === 'es' ? 'atributo destino' : 'target feature'}=${targetFeature}` : '',
        ].filter(Boolean).join(', ');
        const concreteSuffix = concrete === '' ? '' : (language === 'es' ? ` Datos detectados: ${concrete}.` : ` Detected values: ${concrete}.`);
        return formatActionableError(
            message,
            inferLocalField(rawMessage) || (bindingId ? `binding ${bindingId}` : ''),
            language === 'es'
                ? `El mapping o correspondencia de ${localizedContext} no es coherente con sus extremos.${concreteSuffix}`
                : `The mapping/correspondence in ${context} is inconsistent with its endpoints.${concreteSuffix}`,
            language === 'es'
                ? 'Revísalo en este orden: 1) tipo origen, 2) tipo destino, 3) atributo origen, 4) atributo destino, 5) dirección y 6) conversión. En este caso comprueba que el atributo destino indicado exista realmente en el tipo destino; si fue renombrado, vuelve a seleccionarlo. Si la transformación es bidireccional, la regla inversa debe reutilizar el correspondenceId e intercambiar origen y destino con mappings realmente reversibles.'
                : 'Review it in this order: 1) source type, 2) target type, 3) source feature, 4) target feature, 5) direction and 6) conversion. In this case verify that the indicated target feature really exists on the target type; if it was renamed, select it again. For bidirectional transformations, the reverse rule must reuse the correspondenceId and swap source/target with genuinely reversible mappings.',
            [language === 'es'
                ? 'Ejemplo: si el binding personName apunta a target feature fullName, abre ese binding, confirma primero el tipo destino y selecciona en su lista un atributo existente equivalente a fullName antes de volver a validar.'
                : 'Example: if binding personName points to target feature fullName, open that binding, confirm the target type first, and select an existing equivalent attribute from that type before validating again.']
        );
    }

    if (lower.includes('not found') || lower.includes('unknown') || lower.includes('unresolved')) {
        return formatActionableError(
            message,
            inferLocalField(rawMessage),
            language === 'es' ? `Una referencia de ${localizedContext} apunta a un elemento, archivo o recurso que HYDRA no puede localizar en el contexto actual.` : `A reference in ${context} points to an element, file or resource that HYDRA cannot locate in the current context.`,
            language === 'es' ? 'Localiza la referencia indicada, vuelve a seleccionarla desde el desplegable o explorador y comprueba que el elemento dependiente exista y esté guardado. Si se eliminó o renombró, sustituye la referencia antigua antes de continuar.' : 'Locate the indicated reference, select it again from the picker and verify that the dependency exists and is saved. Replace stale references if the target was removed or renamed.',
            []
        );
    }
    if (lower.includes('duplicate') || lower.includes('already exists') || lower.includes('must be unique')) {
        return formatActionableError(
            message,
            inferLocalField(rawMessage) || 'ID',
            language === 'es' ? 'Dos elementos intentan utilizar un identificador, nombre técnico o relación que debe ser único en este documento.' : 'Two elements are trying to use an identifier, technical name or relation that must be unique in this document.',
            language === 'es' ? 'Conserva el identificador del elemento ya referenciado y cambia el del elemento nuevo. Si pretendías modificar el existente, selecciónalo y edítalo en vez de crear otro.' : 'Keep the identifier of the element already referenced and change the newer element. If you intended to modify the existing item, select and edit it instead of creating another one.',
            []
        );
    }
    const field = inferLocalField(rawMessage);
    const subject = inferConcreteSubject(rawMessage);
    const suffix = subject === '' ? '' : (language === 'es' ? ` El valor implicado es «${subject}».` : ` The involved value is “${subject}”.`);
    if (/cardinal|multiplicit|lower bound|upper bound|collection|many-to-one|one-to-many/.test(lower)) {
        return formatActionableError(message, field,
            language === 'es' ? `La multiplicidad configurada en ${localizedContext} no puede representar de forma segura el valor actual.${suffix}` : `The multiplicity configured in ${context} cannot safely represent the current value.${suffix}`,
            language === 'es' ? 'Compara los límites mínimo y máximo del origen y del destino. Si varios valores llegan a un destino simple, define una reducción explícita; si el destino es obligatorio, garantiza al menos uno. Vuelve a comprobar con casos de 0, 1 y varios elementos.' : 'Compare the source and target minimum/maximum bounds. If many values reach a single-valued target, define an explicit reduction; if the target is mandatory, guarantee at least one. Check again with zero, one and many values.', []);
    }
    if (/cycle|cyclic|loop/.test(lower)) {
        return formatActionableError(message, field,
            language === 'es' ? `Las referencias o conexiones de ${localizedContext} forman un ciclo que esa operación no admite.${suffix}` : `References or connections in ${context} form a cycle that operation does not allow.${suffix}`,
            language === 'es' ? 'Sigue la cadena de IDs hasta volver al primer elemento y elimina o redirige solo la conexión que cierra el ciclo. Ejecuta de nuevo la validación antes de modificar el resto de la estructura.' : 'Follow the ID chain until it returns to the first element, then remove or redirect only the connection that closes the cycle. Validate again before changing the rest of the structure.', []);
    }
    if (/syntax|parse|parser|grammar|compile|compilation|expression|operator|datatype|conversion/.test(lower)) {
        return formatActionableError(message, field,
            language === 'es' ? `HYDRA no puede interpretar o tipar correctamente el contenido de ${localizedContext}.${suffix}` : `HYDRA cannot correctly parse or type the content in ${context}.${suffix}`,
            language === 'es' ? 'Corrige primero el primer error de la zona indicada y vuelve a analizar antes de tocar los siguientes, porque pueden ser errores en cascada. Comprueba nombres de símbolos, tipos, operadores y delimitadores; aplica un quick-fix solo después de revisar el cambio propuesto.' : 'Fix the first error in the indicated area and analyse again before touching later ones because they may be cascading failures. Check symbol names, types, operators and delimiters; apply a quick fix only after reviewing the proposed change.', []);
    }
    return formatActionableError(
        message,
        field,
        language === 'es' ? `La operación en ${localizedContext} ha rechazado el valor actual.${suffix} El detalle concreto devuelto por la capa que lo valida es: ${message}` : `The operation in ${context} rejected the current value.${suffix} The concrete detail returned by the validating layer is: ${message}`,
        language === 'es' ? `Abre exactamente ${field || localizedContext}, contrasta el valor actual con el detalle anterior y modifica únicamente la propiedad relacionada. Después vuelve a ejecutar la misma validación. Si el mensaje cita un ID o ruta, úsalo para navegar al elemento concreto en lugar de revisar todo el documento.` : `Open exactly ${field || context}, compare the current value with the detail above and change only the related property. Then run the same validation again. If the message cites an ID or path, use it to navigate to the concrete element instead of reviewing the whole document.`,
        [language === 'es' ? `Mensaje técnico conservado para diagnóstico: ${rawMessage}` : `Technical message preserved for diagnosis: ${rawMessage}`]
    );
}

function inferConcreteSubject(message:string):string {
    const quoted=message.match(/[«“"'`]([^«»“”"'`]{1,120})[»”"'`]/)?.[1]?.trim();
    if(quoted)return quoted;
    return message.match(/\b(?:id|rule|binding|feature|node|edge|type|class|field|property)\s*[=:]\s*([A-Za-z0-9_.:-]{1,120})/i)?.[1]??'';
}

export function isActionableErrorMessage(message: string): boolean {
    const value = message.trim();
    return value.startsWith('What went wrong:') || value.startsWith('Qué ha ocurrido:');
}

function explainKnownLocalFailure(rawMessage: string, context: string, language: 'en' | 'es', localizedContext: string): string | null {
    const lower = rawMessage.toLowerCase();
    const es = language === 'es';
    const saveDependency = rawMessage.match(/^Save the (.+?) before (.+?)[.!]?$/i);
    if (saveDependency !== null) {
        const dependency = saveDependency[1].trim();
        const operation = saveDependency[2].trim();
        return formatActionableError(
            es ? `No se puede ${translateOperation(operation)} porque ${translateDependency(dependency)} todavía no está guardado.` : `HYDRA cannot ${operation} because the ${dependency} has not been saved yet.`,
            dependency,
            es ? `Esta capa necesita una versión persistida y estable de ${translateDependency(dependency)} para asociar correctamente IDs, referencias y cambios.` : `This layer needs a persisted, stable ${dependency} so IDs, references and changes can be associated safely.`,
            es ? `Abre ${translateDependency(dependency)}, guarda sus cambios y espera a que desaparezca el estado de cambios pendientes. Después vuelve a ${localizedContext} y repite la operación.` : `Open the ${dependency}, save its pending changes and wait until the unsaved state clears. Then return to ${context} and run the operation again.`,
            es ? [`No continúes creando referencias a ${translateDependency(dependency)} mientras siga sin guardar.`] : [`Do not create references to the ${dependency} while it is still unsaved.`]
        );
    }
    if (/(save|persist).*(pending|unsaved|dirty).*(before|first)|save the pending .* changes before/i.test(rawMessage)) {
        return formatActionableError(
            es ? `Hay cambios pendientes en ${localizedContext} que deben guardarse antes de continuar.` : `There are pending changes in ${context} that must be saved before continuing.`,
            es ? 'Cambios pendientes' : 'Pending changes',
            es ? 'La siguiente operación depende de la última versión persistida. Ejecutarla sobre un borrador podría compilar, validar o publicar una versión distinta de la que estás viendo.' : 'The next operation depends on the last persisted version. Running it on a draft could compile, validate or publish a different version from the one currently shown.',
            es ? 'Guarda primero la capa actual. Cuando HYDRA confirme el guardado y desaparezca el indicador de cambios pendientes, ejecuta de nuevo la compilación, validación, publicación o exportación.' : 'Save the current layer first. Once HYDRA confirms the save and the pending-changes indicator disappears, run the compile, validation, publication or export action again.',
            []
        );
    }
    if (/create or select a .* first|create .* first/.test(lower)) {
        const subject = extractSubject(rawMessage, /(?:create or select|create) (?:a|an) (.+?) first/i) || (es ? 'Elemento necesario' : 'Required element');
        return formatActionableError(
            es ? `Falta seleccionar o crear ${translateDependency(subject)} antes de continuar.` : `Create or select ${subject} before continuing.`,
            subject,
            es ? `La acción actual necesita un ${translateDependency(subject)} activo para saber dónde crear o aplicar el cambio.` : `The current action needs an active ${subject} to know where the change should be created or applied.`,
            es ? `Usa la lista o el botón de creación de ${localizedContext} para crear o seleccionar ${translateDependency(subject)}. Comprueba que quede resaltado como selección activa y repite la acción.` : `Use the list or create button in ${context} to create or select ${subject}. Verify that it is shown as the active selection and run the action again.`,
            []
        );
    }
    if (/select .+ before/.test(lower)) {
        const subject = extractSubject(rawMessage, /select (.+?) before/i) || (es ? 'Selección' : 'Selection');
        return formatActionableError(
            es ? `La operación necesita una selección previa en ${localizedContext}.` : `The operation needs a prior selection in ${context}.`,
            subject,
            es ? 'HYDRA no puede determinar de forma segura sobre qué elemento debe actuar sin esa selección.' : 'HYDRA cannot safely determine which element the operation should target without that selection.',
            es ? `Selecciona ${translateDependency(subject)} en la lista, árbol o diagrama correspondiente. Verifica que quede resaltado y vuelve a ejecutar la operación.` : `Select ${subject} in the corresponding list, tree or diagram, verify that it is highlighted, and run the operation again.`,
            []
        );
    }
    if (/transition requires at least two nodes|source and a target element|requires at least two .*nodes/.test(lower)) {
        return formatActionableError(
            es ? 'No se puede crear la conexión porque faltan sus dos extremos.' : 'The connection cannot be created because both endpoints are not selected.',
            es ? 'Selección del diagrama' : 'Diagram selection',
            es ? 'Una transición o relación necesita un elemento de origen y otro de destino válidos; con uno solo HYDRA no puede construir una conexión inequívoca.' : 'A transition or relation needs a valid source and target; with only one endpoint HYDRA cannot create an unambiguous connection.',
            es ? 'En el diagrama, selecciona primero el nodo de origen y después el nodo de destino. Si uno de ellos todavía no existe, créalo antes. A continuación vuelve a pulsar Crear transición/relación.' : 'In the diagram, select the source node first and the target node second. Create a missing endpoint first if needed, then run Create transition/relationship again.',
            []
        );
    }
    if (lower.includes('source metamodelid must match') || (lower.includes('metamodel') && lower.includes('must match the current'))) {
        return formatActionableError(
            es ? 'El contenido que intentas cargar pertenece a un metamodelo distinto del que está activo.' : 'The content being loaded belongs to a different metamodel from the active one.',
            es ? 'Metamodelo del documento' : 'Document metamodel',
            es ? 'HYDRA conserva el identificador del metamodelo para impedir que reglas, eventos, flujos o modelos se conecten accidentalmente a una estructura semántica incompatible.' : 'HYDRA keeps the metamodel identifier to prevent rules, events, flows or models from being attached accidentally to an incompatible semantic structure.',
            es ? 'Comprueba el metamodelo activo en la cabecera. Cambia al metamodelo al que pertenece el archivo o exporta/importa de nuevo la capa usando el metamodelo actual. No edites el identificador a mano salvo que sepas que ambas estructuras son realmente equivalentes.' : 'Check the active metamodel in the header. Switch to the metamodel that owns the file or export/import the layer again using the current metamodel. Do not edit the identifier manually unless the structures are genuinely equivalent.',
            []
        );
    }
    if (lower.includes('requires at least one concrete metamodel class')) {
        return formatActionableError(
            es ? 'No se puede crear el formulario porque el metamodelo no contiene ninguna clase concreta utilizable.' : 'The form cannot be created because the metamodel has no usable concrete class.',
            es ? 'Clases del metamodelo' : 'Metamodel classes',
            es ? 'Un formulario necesita una clase instanciable como raíz. Las clases abstractas no pueden representar por sí solas una instancia editable.' : 'A form needs an instantiable root class. Abstract classes cannot represent an editable instance by themselves.',
            es ? 'Abre el Diseñador de metamodelos, crea una clase o cambia a concreta una clase adecuada, guarda el metamodelo y vuelve a Definición del editor. Después crea el formulario seleccionando esa clase.' : 'Open Metamodel Designer, create a class or make a suitable class concrete, save the metamodel and return to Editor Definition. Then create the form using that class.',
            []
        );
    }
    if (lower.includes('every target field already has a mapping')) {
        return formatActionableError(
            es ? 'No se puede añadir otro mapeo porque todos los campos de destino ya están cubiertos.' : 'Another mapping cannot be added because every target field is already mapped.',
            es ? 'Mapeos de payload' : 'Payload mappings',
            es ? 'Cada campo de destino admite un único origen en este binding para evitar asignaciones contradictorias.' : 'Each target field accepts a single source in this binding to avoid conflicting assignments.',
            es ? 'Edita uno de los mapeos existentes si necesitas cambiar su origen. Si falta un campo de destino, añádelo primero al tipo de evento de destino y vuelve después a este binding.' : 'Edit an existing mapping if you need to change its source. If a target field is missing, add it to the target event type first and then return to this binding.',
            []
        );
    }
    if (lower.includes('binding target must be a custom event type')) {
        return formatActionableError(
            es ? 'El destino del binding no puede ser un evento reservado o no editable.' : 'The binding target cannot be a reserved or non-editable event type.',
            es ? 'Evento de destino' : 'Target event',
            es ? 'Los bindings transforman payloads hacia contratos de evento personalizados. Los eventos reservados de HYDRA tienen una estructura controlada y no pueden usarse como destino de esa transformación.' : 'Bindings transform payloads into custom event contracts. HYDRA reserved events have a controlled structure and cannot be used as that transformation target.',
            es ? 'Crea o selecciona un tipo de evento personalizado en Eventos y úsalo como destino. Si necesitas reaccionar a un evento reservado, úsalo como origen y mapea sus datos hacia el evento personalizado.' : 'Create or select a custom event type in Events and use it as the target. To react to a reserved event, use it as the source and map its data into the custom event.',
            []
        );
    }
    if (lower.includes('no compiled relationship tool accepts')) {
        return formatActionableError(
            es ? 'La paleta actual no contiene una relación compatible con los dos elementos seleccionados.' : 'The current palette has no relationship compatible with the selected elements.',
            es ? 'Origen y destino de la relación' : 'Relationship source and target',
            es ? 'La definición compilada del editor restringe qué clases pueden conectarse. La combinación seleccionada no coincide con ninguna herramienta de relación disponible.' : 'The compiled editor definition restricts which classes can be connected. The selected class combination does not match any available relationship tool.',
            es ? 'Selecciona otra combinación de origen/destino compatible o abre Definición del editor y añade una herramienta de relación cuyo origen y destino incluyan esas clases. Guarda y recompila el editor antes de probar de nuevo.' : 'Select a compatible source/target combination or open Editor Definition and add a relationship tool whose source and target include those classes. Save and recompile the editor before trying again.',
            []
        );
    }
    if (lower.includes('selected container does not accept')) {
        return formatActionableError(
            es ? 'El elemento no puede insertarse dentro del contenedor seleccionado.' : 'The element cannot be inserted into the selected container.',
            es ? 'Contenedor seleccionado' : 'Selected container',
            es ? 'La definición del editor limita los tipos de elementos que cada contenedor puede contener y la clase elegida no está admitida en ese punto.' : 'The editor definition limits which element types each container may contain, and the chosen class is not accepted there.',
            es ? 'Selecciona un contenedor compatible o abre Definición del editor y revisa la regla de contención de esa herramienta. Si cambias la definición, guarda y recompila antes de volver al editor generado.' : 'Select a compatible container or open Editor Definition and review that tool’s containment rule. Save and recompile after changing the definition.',
            []
        );
    }
    if (lower.includes('unsupported') || lower.includes('not supported')) {
        return formatActionableError(
            es ? `La opción, formato o característica indicada no está admitida en ${localizedContext}.` : `The indicated option, format or feature is not supported in ${context}.`,
            inferLocalField(rawMessage),
            es ? 'La operación solicita una capacidad que este importador, generador o proveedor no puede representar de forma segura con la configuración actual.' : 'The operation requests a capability that this importer, generator or provider cannot represent safely with the current configuration.',
            es ? 'Usa una de las opciones disponibles en el selector correspondiente. Si procede de una importación, consulta los diagnósticos para identificar la característica no representable y decide si debes simplificar el origen o mantenerla como información para revisión manual.' : 'Choose one of the options offered by the relevant selector. For imports, review diagnostics to identify the unsupported feature and decide whether to simplify the source or keep it for manual review.',
            []
        );
    }
    return null;
}

function inferLocalField(message: string): string {
    const explicit = message.match(/(?:field|campo)\s+['"`]?([A-Za-z0-9_.\-/]+)['"`]?/i);
    if (explicit?.[1]) return explicit[1];
    const property = message.match(/^([A-Za-z][A-Za-z0-9_.\-/]{0,80})\s+(?:must|is required|cannot|does not)/i);
    if (property?.[1]) return property[1];
    const lower = message.toLowerCase();
    if (lower.includes('metamodelid') || lower.includes('metamodel id')) return 'metamodelId';
    if (lower.includes('repository') && lower.includes('branch')) return 'repository/branch';
    if (lower.includes('callback') || lower.includes('redirect uri')) return 'callbackUrl';
    if (lower.includes('scope') || lower.includes('permission')) return 'scopes';
    if (lower.includes('client id') || lower.includes('application id') || lower.includes('app key')) return 'clientId';
    return '';
}

function extractSubject(message: string, pattern: RegExp): string {
    return message.match(pattern)?.[1]?.replace(/[.!]$/, '').trim() ?? '';
}

function translateDependency(value: string): string {
    const normalized = value.trim().toLowerCase();
    const table: Record<string, string> = {
        'metamodel': 'el metamodelo',
        'editor definition': 'la definición del editor',
        'validation definitions': 'las definiciones de validación',
        'event contracts': 'los contratos de eventos',
        'event flow': 'el flujo de eventos',
        'event flows': 'los flujos de eventos',
        'gamification': 'la capa de gamificación',
        'flow': 'un flujo',
        'custom event type': 'un tipo de evento personalizado',
        'source and a target element': 'un elemento de origen y otro de destino',
    };
    return table[normalized] ?? value;
}

function translateOperation(value: string): string {
    const normalized = value.trim().replace(/[.!]$/, '');
    const lower = normalized.toLowerCase();
    if (lower.startsWith('saving ')) return `guardar ${normalized.slice(7)}`;
    if (lower.startsWith('configuring ')) return `configurar ${normalized.slice(12)}`;
    if (lower.startsWith('checking ')) return `comprobar ${normalized.slice(9)}`;
    if (lower.startsWith('compiling ')) return `compilar ${normalized.slice(10)}`;
    if (lower.startsWith('defining ')) return `definir ${normalized.slice(9)}`;
    return normalized;
}

function translateContext(context: string): string {
    const known = localizeKnownErrorText(context);
    if (known !== context) return known;
    const table: Record<string, string> = {
        'the current form': 'el formulario actual',
        'the current diagram': 'el diagrama actual',
        'repository connection': 'la conexión con el repositorio',
        'external connection': 'la conexión externa',
        'project explorer': 'el explorador de proyectos',
        'validation designer': 'el diseñador de validaciones',
        'event designer': 'el diseñador de eventos',
        'event flow designer': 'el diseñador de flujo de eventos',
        'gamification designer': 'el diseñador de gamificación',
        'editor definition': 'la definición del editor',
        'integration designer': 'el diseñador de integraciones',
        'model editor': 'el editor de modelos',
        'metamodel import': 'la importación del metamodelo',
        'remote metamodel import': 'la importación remota del metamodelo',
        'metamodel import analysis': 'el análisis de importación del metamodelo',
        'metamodel export': 'la exportación del metamodelo',
        'complete project export': 'la exportación del proyecto completo',
        'associated metamodel analysis': 'el análisis del metamodelo asociado',
        'activity log': 'el registro de actividad',
        'capability readiness': 'la comprobación de disponibilidad',
        'technical settings': 'los ajustes técnicos',
        'round-trip': 'la sincronización de ida y vuelta',
        'native compatibility': 'la compatibilidad nativa',
        'update-site import': 'la importación de Update Site',
        'representable import': 'la importación reconstruible',
        'project file': 'el archivo del proyecto',
        'distribution': 'la distribución',
        'generated project': 'el proyecto generado',
        'artifact registry': 'el registro de documentos',
        'publication': 'la publicación',
        'remote storage': 'el almacenamiento remoto',
        'visual definition import': 'la importación de la definición visual',
        'Metamodel Designer': 'el diseñador de metamodelos',
        'Validation Designer': 'el diseñador de validaciones',
        'Event Designer': 'el diseñador de eventos',
        'EventFlow Designer': 'el diseñador de flujo de eventos',
        'Gamification Designer': 'el diseñador de gamificación',
        'Integration Designer': 'el diseñador de integraciones',
        'Integration properties': 'las propiedades de integración',
        'the Event source tab': 'la pestaña Fuente de Eventos',
        'the EventFlow source tab': 'la pestaña Fuente de Flujo de eventos',
        'the Gamification source tab': 'la pestaña Fuente de Gamificación',
    };
    return table[context] ?? context;
}
