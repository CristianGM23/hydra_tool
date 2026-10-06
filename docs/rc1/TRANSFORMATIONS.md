# Transformaciones y sincronización

## Proyecto de referencia
`reference-projects/sync-advanced`.

## Flujo
1. Carga `rc1-people` y `rc1-contacts`.
2. Abre `people-contacts.transformation.json` y ejecuta **Check**.
3. Ejecuta FORWARD y comprueba el trace Person → Contact.
4. Ejecuta REVERSE usando las reglas explícitas inversas.
5. Para incremental, conserva source/target baseline y trace previo.
6. Modifica un nombre en un solo lado y previsualiza el delta antes de aplicar.
7. Provoca un conflicto en ambos lados: la política `FAIL_ON_CONFLICT` debe impedir una actualización silenciosa.

## Resultado esperado
Las correspondencias son estables, la dirección es explícita y ningún conflicto se resuelve sin política.
