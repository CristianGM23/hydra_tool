# R6C2 — Technology Delivery Readiness

R6C2 separa dos conceptos que antes podían confundirse:

1. **Madurez tecnológica global**: qué recorrido implementa HYDRA para Xtext, Langium, GLSP, EMF/Eclipse, VS Code, BESSER y el resto de destinos.
2. **Preparación de un perfil concreto**: qué puede ejecutar HYDRA ahora mismo con un perfil persistido y sus artefactos reales.

La segunda vista se obtiene sin escribir en el proyecto anfitrión y sin ejecutar toolchains externas. HYDRA carga el perfil, reutiliza su `IntegrationCheckReport` y, cuando la generación está implementada y el perfil es válido, realiza una compilación tecnológica en memoria. El resultado permite detectar fallos de generación antes de ofrecer acciones posteriores.

## Estados por fase

Cada fase recibe uno de estos estados:

- `READY`: la acción está implementada y sus precondiciones de HYDRA están satisfechas.
- `READY_WITH_WARNINGS`: la acción es ejecutable, pero el destino o el perfil contiene soporte parcial/avisos que deben revisarse.
- `BLOCKED`: falta una implementación o existe un diagnóstico/compilación bloqueante.
- `EXTERNAL`: la fase corresponde al host o toolchain nativo y HYDRA no la presenta como ejecución interna.
- `NOT_APPLICABLE`: la fase no forma parte del modo de entrega, por ejemplo la inyección de host para una proyección PlantUML.

## Contrato de seguridad

`GET /api/semantic-contexts/{scope}/integrations/{profile}/technology/readiness` es de solo lectura. No materializa un workspace externo, no ejecuta Maven/npm/Python y no escribe en el host.

La verificación del proyecto sigue siendo una acción explícita mediante `/technology/validate`:

- `STRUCTURAL`: materializa en un workspace temporal y aplica el validador estructural registrado.
- `NATIVE_IF_AVAILABLE`: añade los pasos nativos declarados por el adaptador, tolerando herramientas no disponibles como resultado parcial.
- `NATIVE_REQUIRED`: exige que la toolchain nativa pueda ejecutarse correctamente.

La aplicación al host conserva el contrato preview-first y rollback-required. El round-trip conserva baseline, detección de conflictos y resolución explícita.

Para destinos de proyección, `compilationReady` no es la fuente de verdad de la generación: la fase `GENERATE` puede estar `READY` mediante `EXPORT_PROJECTION` aunque no exista un módulo compilado. La interfaz deriva el estado visible de la propia fase para no presentar falsos bloqueos entre familias tecnológicas.

La ruta de readiness forma parte del OpenAPI canónico de HYDRA y del cliente TypeScript generado; no existe una superficie REST paralela o no documentada para esta capacidad.

## Objetivo de paridad

R6C2 no obliga a que todas las tecnologías hagan lo mismo. Obliga a que todas expongan **el mismo contrato de entrega**, y a que HYDRA diferencie de forma visible entre una fase implementada, parcial, externa o no aplicable. Esto permite comparar destinos sin acoplar el modelo canónico a Xtext, Langium, GLSP, EMF, VS Code o BESSER.
