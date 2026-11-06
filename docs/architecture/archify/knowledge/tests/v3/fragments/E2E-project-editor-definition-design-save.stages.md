E2E-project-editor-definition-design-save - stage mapping (pw:e2e/real/project/editor-definition-design-save.spec.ts)
- model_editor_author_design.rp (+ Representacion): L31-L33 se anade la vista con la clase raiz detectada; L85-L88 el backend guarda 2 vistas y la segunda con su raiz.
- model_editor_author_design.nm (+ sobre una clase): L39-L40 mapeo de nodo; L89-L90 y L94 mapeo y herramienta de crear nodo guardados.
- model_editor_author_design.em (+ sobre una relacion): L44-L45 mapeo de arista; L91-L92 y L95 mapeo y herramienta de crear arista guardados con sus extremos.
- model_editor_author_design.pt (accion en un grupo): L51-L52 operacion y accion configurada bloqueadas si no existen; L53-L55 y L96-L98 herramienta de validar guardada en el grupo. «Sin grupo» no es ejercitable desde la interfaz.
- model_editor_author_design.fm (+ Formulario): L59-L60 formulario de la primera clase concreta; L99-L100 guardado; L116 error si no hay clase concreta.
- model_editor_author_design.cs (estilo condicional): L70-L71 sin condicion no se guarda; L101-L107 regla con condicion y preajuste guardada y condicion en el modelo de expresiones.
- model_editor_author_design.art (definicion actualizada): L84-L107 todo lo disenado esta en la definicion que sirve el backend.
- Hallazgo (no es etapa): tras guardar con aristas la interfaz lanza un error de lectura de null al normalizar la respuesta y no muestra el mensaje de guardado; el backend si guarda.
