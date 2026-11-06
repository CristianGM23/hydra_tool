Etapas certificadas por EventModelAuthoringPersistenceIntegrationTests (linea = aserciones clave):
- events_define.a1/a2/a3/a4: validTypesFieldsAndBindingsArePersistedAsRawJson (l.123-158, JSON crudo de tipo, campos, enlace y copias) y rawJsonWithInvalid... (l.282-297, nombre en blanco, espacio de nombres con cifra, copia con dos fuentes).
- events_define.a5 y events_author_connect.a5: theCheckerGivesExactDiagnosticCodes... (l.224-265, codigos exactos y bloqueo sin escritura).
- events_define.a6 y events_author_form.a8: replaceWithStaleEtag... (l.165-186) y theRealRestController... (l.194-218, 412 con ETag obsoleto, 200 con vigente).
- events_author_form.d1: rawJsonWithInvalid... (l.296-297, ENUM sin enumTypeId rechazado; con enumTypeId guarda).
- Indirectas (no probadas): events_author_form.a7 (validacion local del cliente) y events_author_source.a3 (normalizacion en cliente).
