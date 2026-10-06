# Primer proyecto

## Objetivo
Crear o importar un proyecto, identificar sus artefactos y comprobar que HYDRA conserva el contexto al cerrar y volver a abrir.

## Pasos
1. Abre **Proyectos** y crea un proyecto local en una carpeta de trabajo.
2. Selecciona el preset adecuado a la finalidad; si dudas, usa el proyecto MDE completo.
3. Comprueba el árbol del proyecto y la ubicación de los artefactos antes de editar.
4. Crea o importa un metamodelo y guárdalo.
5. Cierra HYDRA, vuelve a abrirla y confirma que el proyecto y el metamodelo siguen accesibles.

## Resultado esperado
El proyecto se reabre sin perder artefactos, pestañas persistibles ni asociaciones canónicas.

## Si algo falla
Usa el `correlationId` del error y la acción **Ver diagnóstico** antes de repetir una escritura.
