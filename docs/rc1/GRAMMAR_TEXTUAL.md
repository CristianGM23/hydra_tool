# Gramáticas y editor textual

## Proyecto de referencia
`reference-projects/commerce-intermediate`.

## Flujo
1. Abre la gramática `rc1-commerce-grammar`.
2. Ejecuta análisis FAST y revisa **Problemas**.
3. Comprueba el diagrama de clases inferido y la regla de entrada `Model`.
4. Crea texto `product HYDRA-001 price 42.5;` y valida la proyección semántica.
5. Modifica una regla, vuelve a analizar y comprueba que la vista previa cambia sin perder el borrador.

## Resultado esperado
Monaco, problemas, diagrama inferido y editor textual comparten la misma revisión de gramática.
