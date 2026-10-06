# Metamodelo, modelo y validación

## Proyecto de referencia
`reference-projects/library-basic`.

## Flujo
1. Importa o crea `library.metamodel.json`.
2. Valida el metamodelo antes de crear modelos dependientes.
3. Carga expresiones, acciones y validaciones del proyecto de referencia.
4. Crea un modelo y provoca de forma deliberada una infracción de páginas.
5. Abre **Problemas**, revisa el diagnóstico y previsualiza el quick fix.
6. Aplica el quick fix solo después de comprobar el cambio propuesto.

## Resultado esperado
El diagnóstico desaparece tras aplicar una acción segura y la operación queda trazable.
