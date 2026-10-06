# HYDRA V4.32.107 — Auditoría profunda, optimización y campaña agresiva final

Fecha de cierre: 2026-09-13
Baseline analizado: `hydra(9).rar`
Versión canónica resultante: `4.32.107`

## 1. Alcance y criterio de cierre

La auditoría se ha realizado sobre el inventario fuente mantenido de HYDRA, excluyendo de la lectura semántica dependencias, outputs de compilación, datos de ejecución y caches (`target/`, `data/`, `frontend/node_modules/`, `dist/`, `coverage/`, etc.). Esos árboles generados sí se han utilizado cuando aportaban evidencia útil de build, toolchain, crash o runtime.

La pasada combina:

- inventario reproducible de cada fichero publicable;
- barrido estático del contenido fuente;
- revisión manual de hotspots de complejidad, E/S, concurrencia y lifecycle;
- gates históricos y actuales del repositorio;
- pruebas focales de regresión;
- stress tests de cardinalidad, resiliencia y carreras;
- comparación binaria/CRC contra el RAR original;
- generación final reproducible del paquete fuente.

No se han relajado límites arquitectónicos, no se han añadido suppressions para obtener verde y no se han eliminado pruebas fallidas. Cuando un verificador histórico expresaba un contrato obsoleto, se corrigió el verificador únicamente después de comprobar que el contrato productivo actual era el correcto.

## 2. Inventario final

El árbol fuente canónico contiene aproximadamente 3.2k ficheros mantenidos y unas 270k líneas de texto. La distribución está dominada por Java, TypeScript/TSX y CSS. El manifest y el ZIP fuente utilizan exactamente el mismo inventario canónico, evitando que logs, caches o datos runtime alteren una entrega reproducible.

El diff exacto contra el RAR original se entrega por separado en CSV. Resumen final: 9 añadidos, 103 modificados, 8 eliminados y 3164 sin cambios.

## 3. Defectos de compilación y frontend corregidos

Se corrigieron defectos TypeScript reales detectados durante la auditoría: iteración incompatible sobre `NodeListOf`, clave duplicada en el catálogo i18n, dependencia del namespace global `JSX` y pérdida de narrowing en una operación asíncrona de compilación de gramáticas.

La auditoría ESLint inicial localizó 33 incidencias (26 errores y 7 warnings). Se eliminaron sin desactivar reglas. Las correcciones se concentraron en estado derivado duplicado, efectos que reseteaban estado de manera síncrona, callbacks asíncronos no ligados a identidad y acceso a refs durante render. Los componentes afectados incluyen Home/readiness, diseñador de gramáticas, Monaco, diagrama de clases y editor de modelos textuales.

El editor textual quedó asociado explícitamente a identidad de gramática/sesión/destino; un resultado asíncrono antiguo ya no puede mostrarse sobre una sesión nueva. El estado de validación se deriva de la revisión y sesión real, en lugar de duplicarse.

## 4. Regresiones textuales/gramaticales recuperadas

La auditoría descubrió una regresión funcional importante: el frontend seguía consumiendo diagnósticos locales y personalización efectiva de gramática que el baseline había dejado incompletos. Se restauraron:

- inspección local de gramática;
- diagnóstico provisional acotado;
- `entry` como información, no como error;
- personalización textual efectiva;
- propagación real de `maximumDiagnostics` desde la sesión textual hasta el parser.

El gate R8 vuelve a cubrir estos contratos y queda verde.

## 5. Optimización algorítmica

### 5.1 Paquetes `.hydra-cap`

`CapabilityPackageArchiveService.verifyEntries()` realizaba una búsqueda lineal en las entradas declaradas para cada entrada física del ZIP. Con miles de recursos el coste se aproximaba a O(n²). Se sustituyó por un índice `Set`, reduciendo la comprobación esencial a O(n) y detectando además paths declarados duplicados.

### 5.2 Merge semántico

`CapabilitySemanticMergeService` recalculaba firmas semánticas de los candidatos repetidamente. Ahora construye un índice `firma -> candidatos` una sola vez, no reutiliza entidades ya consumidas y conserva la ambigüedad cuando existen varios candidatos equivalentes. La unión ordenada utiliza `LinkedHashSet` en lugar de `ArrayList.contains()` repetido.

### 5.3 Análisis de gramáticas

`GrammarSourceAnalyzer` deja de recorrer todas las declaraciones para localizar la regla contenedora de cada referencia. Las declaraciones ordenadas permiten búsqueda binaria O(log n), y el índice textual se calcula una sola vez por análisis.

### 5.4 Índice de proyectos

`ProjectIndexService` incorpora un presupuesto global configurable (`dsl.projects.local.max-tree-entries`, 20.000 por defecto) para árbol físico, lógico, artefactos e indexación incremental. Un árbol ancho ya no puede materializar colecciones ilimitadas. La ordenación se aplica solo después de demostrar que la colección está dentro del presupuesto.

## 6. E/S, memoria y atomicidad

Se eliminaron las lecturas productivas principales sin límite mediante `Files.readAllBytes()`/`Files.readString()` en rutas sensibles. Los cambios afectan a Git remoto, round-trip, gramáticas nativas, lifecycle de metamodelos, update-sites, credenciales protegidas, recovery, proyectos y servidor standalone.

`AtomicFiles` concentra la publicación segura: se intenta `ATOMIC_MOVE` y solo se degrada a movimiento convencional ante `AtomicMoveNotSupportedException`. Errores reales de permisos, disco o corrupción ya no se interpretan erróneamente como falta de soporte atómico. La limpieza de temporales se ejecuta en `finally`.

El servidor standalone generado calcula ETags de forma incremental y transmite recursos mediante streaming, evitando duplicar un fichero completo en heap.

El round-trip utiliza hashing streaming y un presupuesto configurable por fichero; las rutas de merge/backup/rollback/restauración están acotadas.

`HydraArtifactScanner` utiliza SHA-256 streaming y límites configurables de cantidad de ficheros, tamaño por fichero, bytes totales y profundidad.

## 7. Proyectos y ZIP

`ProjectZipService` fue reestructurado para que exportación e importación sean acotadas y transaccionales:

- límite de entradas, profundidad, bytes expandidos, tiempo y ratio de compresión;
- rechazo de symlinks y tipos especiales en paquetes portables;
- ZIP generado mediante streaming a temporal;
- exclusión de cache interna;
- validación de nombres canónicos, duplicados normalizados y zip-slip;
- importación a staging hermano y publicación únicamente tras finalizar correctamente.

`ProjectFileOperations` y `ProjectWorkspaceBridge` usan recorridos incrementales. El bridge preserva paths relativos y evita flattening/colisiones de artefactos con el mismo basename.

## 8. Recovery y update-sites

`HydraRecoveryService` dejó de materializar entradas completas del ZIP durante verificación/restauración. Hashing y copia son streaming; la restauración publica el fichero solo después de verificar tamaño y checksum.

Las rutas de update-site comparten semántica de atomicidad uniforme, aplican presupuestos antes de materializar colecciones y rechazan symlinks inesperados. `UpdateSiteRepositoryRootDetector` usa semántica `NOFOLLOW_LINKS` para evitar inspeccionar destinos exteriores al repositorio.

## 9. Git y procesos externos

La vista de conflictos Git utiliza el mismo presupuesto de lectura para working tree y versiones Git. La enumeración de branches evita procesos adicionales por rama y utiliza el tracking que devuelve Git en la consulta principal. La salida de herramientas nativas se ejecuta con locale controlado.

Durante la campaña final apareció una carrera real en `HydraProcessTreeTerminator`: un proceso podía crear un descendiente entre la última observación y la salida del root. Se mantuvo un tracker vivo durante la terminación, refrescando descendientes durante las esperas y fusionando su snapshot final antes del barrido forzado.

El stress específico de esa carrera se completó cuatro veces; las cuatro ejecuciones finalizaron `639/639 PASS`.

## 10. Reproducibilidad y release

Se eliminó la inconsistencia 4.32.106/4.32.107. La versión canónica resultante es 4.32.107.

`generate_manifest.py`, `verify_manifest.py`, `package_source.py`, overlays y patches utilizan la misma definición de inventario. Los symlinks quedan fuera del paquete fuente. El empaquetador fuente copia por streaming, fija metadatos ZIP deterministas y calcula SHA-256 incrementalmente.

La reproducibilidad se verifica generando el paquete fuente dos veces y exigiendo SHA-256 idéntico.

## 11. Resultados de validación

### Gates estructurales

`verify_final_tree.py --structural-only` termina en `FINAL_TREE_GATE=STRUCTURAL_PASS`, incluyendo Pre-GA, contratos P0/P1, arquitectura frontend, i18n, concurrencia, rutas HTTP, Java core smoke, documentación backend, deuda/warnings, contratos backend, accesibilidad, CSS, OpenAPI, excepciones, seguridad de procesos, invariantes y release metadata.

Documentación backend final tras la corrección de terminación: `8717/8717 PASS`, sin suppressions ni marcadores de deuda.

Contratos de finalización de procesos: `31/31 PASS`.

Seguridad de procesos externos: `16/16 PASS`.

Los 36 gates históricos R8–R14 / V43283–V43315 ejecutados durante el cierre finalizaron sin fallos.

### Stress tests finales

- runtime textual: 5.000 elementos, 24 reglas y 60.000 problemas — PASS;
- resiliencia textual: 64 bindings cíclicos y EventFlow profundidad 80 — PASS;
- runtime visual agresivo: 1030/1030 — PASS;
- benchmark índices frontend hasta 5.000 nodos — ampliamente dentro del presupuesto;
- atomicidad de ficheros — PASS;
- ZIP portable con 3.000 ficheros — PASS;
- terminación de árboles de procesos con descendientes en carrera — cuatro ejecuciones completas, 639/639 PASS cada una.

Resultados de la última ejecución JS:

```text
TEXTUAL_FULL_STACK_RUNTIME_STRESS=PASS elements=5000 rules=24 problems=60000 validateMs=184.2 eventsMs=352.9 points=2
TEXTUAL_FULL_STACK_RESILIENCE=PASS cyclicBindings=64 deepFlowNodes=80
HYDRA_VISUAL_RUNTIME_AGGRESSIVE=1030/1030 PASS large=60.8ms mappings=41.3ms
100 nodes  = 2.99 ms / 1000 ms
500 nodes  = 5.95 ms / 1000 ms
2000 nodes = 20.08 ms / 2500 ms
5000 nodes = 81.96 ms / 2500 ms
ATOMIC_FILES_STRESS=PASS
PROJECT_ZIP_STRESS=PASS files=3000 archiveBytes=447170 exportMs=273 importMs=174
```

## 12. Limitaciones de validación del entorno

El cierre no oculta dos limitaciones externas:

- HYDRA declara JDK 24 y Maven 3.9.16. El runtime disponible proporciona Java 21 y no dispone de Maven 3.9.16, y el wrapper no puede descargarlo sin acceso de red. Por ello no se afirma una ejecución completa oficial de `mvn test`/`mvn verify` con JDK 24.
- El árbol reconstruido no contiene un `frontend/node_modules` completo. El toolchain esperado es Node 22.16.0, npm 10.9.2 y TypeScript 6.0.3; Node/npm coinciden, pero TypeScript local y las dependencias Vite/Vitest no están materializadas en este runtime. No se afirma una ejecución final completa de Vite/Vitest sobre este entorno incompleto.

Estas son limitaciones de reproducibilidad del entorno de auditoría, no fallos observados del código fuente. Los gates y harnesses que no dependen de esos componentes se han ejecutado realmente.

## 13. Riesgo residual aceptado

No quedan fallos conocidos de severidad alta/crítica en los gates y stress ejecutables de este entorno. Permanecen como riesgos de validación externa la suite Maven completa bajo el toolchain oficial y la suite frontend completa con dependencias instaladas exactamente desde `package-lock.json`.

Algunas APIs que contractualmente deben devolver `byte[]` continúan utilizando buffers en memoria, pero sus entradas están acotadas por presupuestos de dominio. No se han convertido mecánicamente a streaming cuando hacerlo habría cambiado la API sin beneficio proporcional.

`HydraPersistenceSupport.jsonFiles()` sigue materializando la lista de JSON de directorios administrados por HYDRA. Es una superficie de riesgo menor frente a corrupción deliberada del directorio de persistencia, pero cambiarla de forma transversal en la última fase introduciría un nuevo contrato de cardinalidad en múltiples servicios. Se documenta como mejora futura de baja prioridad en vez de introducir un cambio tardío de comportamiento.

## 14. Conclusión técnica

La versión auditada reduce costes superlineales, acota memoria/E/S, homogeneiza atomicidad, corrige carreras de lifecycle, endurece ZIP/symlink/path handling, recupera contratos textuales perdidos y hace reproducible la entrega fuente. El árbol final queda congelado únicamente después de repetir los gates y stress aplicables. La única validación pendiente fuera de este runtime es repetir Maven/JDK24 y el frontend completo con sus dependencias oficiales en un entorno que disponga de dicho toolchain.
