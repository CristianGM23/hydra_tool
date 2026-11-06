Prueba -> etapa (pw:e2e/real/integration/wizard-features-mappings.spec.ts)
- integration_author_wizard.cp: lineas 20-26 (preajuste portatil: entradas autoseleccionadas en pantalla) y 55-58 (perfil del servidor con capacidades y documentos de entrada).
- integration_author_wizard.mp: lineas 41-43 (mapeo sin identificador: aviso, nada en API ni disco) y 58-68 (mapeo guardado con elemento y localizador, API y disco).
- Nota: el servidor rechaza (error de entrada obligatoria sin resolver) un perfil con documentos obligatorios inexistentes; por eso las entradas se guardan como no obligatorias.
- Defecto: la interfaz lanza «Integration profile collections are incomplete» al leer la respuesta de guardado de un perfil sin capacidades requeridas (el servidor omite colecciones vacias); las aserciones sobre lo guardado se hacen contra el servidor.
- Descartado: resultado de la comprobacion de compatibilidad (solo se verifica que responde).
