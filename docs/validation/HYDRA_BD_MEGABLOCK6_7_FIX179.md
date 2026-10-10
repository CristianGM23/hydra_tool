# HYDRA R6C4 Fix179 — B6+B7+D6+D7: ejecución entre runtimes y despliegue autónomo

## Identificación y límites

Este macrobloque continúa Fix178. La aplicación principal sigue siendo **HYDRA web**;
solo sus editores generados se distribuyen de forma independiente. No crea HYDRA Studio.
Es un desarrollo real con contratos de test, no una certificación universal de plataformas.

## B6: nueva tecnología ejecutada, no simulada

- Se incorpora el perfil explícito `java-standalone` al runner permitido en
  `scripts/validation/run_native_e2e_matrix.py`: invoca `javac` y `java` sin `shell=True`.
- `reference-projects/bd6-java-runtime/src/HydraJavaEditorSmoke.java` ejecuta una
  implementación Java independiente del modelo sencillo del editor web de referencia.
- Los resultados contienen SHA-256 del archivo de entrada, challenge aleatorio
  de la invocación y resultado concreto. Una evidencia antigua no se acepta.
- Se mantienen los perfiles oficiales existentes para EMF, Sirius, Xtext,
  Langium, GLSP, BESSER y ANTLR, que **requieren** proyectos y toolchains propios.

## B7: semántica comparada por capacidad

La nueva política y matriz (`bd6_native_{matrix,policy}.json`) comparan:
validación, Quick Fix, operación, eventos, gamificación, persistencia y
round-trip de un **modelo de referencia limitado**. Un proyecto es Java puro;
el otro es JavaScript/Node. Estas pruebas prueban únicamente la equivalencia
observada en esos 7 casos; NO demuestran paridad EMF/Sirius/Xtext/GLSP.
Se mantienen los diagnósticos `UNVERIFIED` cuando los toolchains no se ejecutan.

## D6: instalación independiente de editores generados

`scripts/distribution/install_standalone_web.py` instala a partir del registro
verificado de Fix177/178, sin ejecutar scripts del paquete ni descargar nada:

1. valida producto, canal, rutas y raíz de destino;
2. exige política de claves ancladas para producción; el modo sin firma exige
   la opción explícita `--allow-unsigned-dev`;
3. inspecciona los hashes y, cuando procede, las firmas Ed25519 de la publicación;
4. copia el archivo firmado a una versión inmutable, descomprime solo los recursos
   web publicables y verifica de nuevo sus bytes;
5. cambia un puntero atómico `current.json` solamente tras validar toda la instalación;
6. conserva proyectos y datos del usuario fuera de `sites/<producto>/<canal>/versions`.

El editor puede servir sus archivos desde un servidor estático convencional,
sin HYDRA web ni sus endpoints. No es un instalador de Windows/macOS/Linux.

## D7: actualización, rollback e integridad

- Las promociones deben subir de versión semántica; nunca sobrescriben una
  versión instalada ni publican contenido distinto bajo la misma identidad.
- Un cambio o pérdida de ZIP, fichero JS, manifiesto o firma provoca fallo cerrado.
- Las operaciones de instalación y rollback usan exclusión mutua y actualizan
  el puntero de forma atómica después de una verificación completa.
- El rollback requiere historia previa y una versión de destino íntegra.
- Los datos de usuario permanecen fuera del árbol versionado y no se borran.
- Las claves privadas se suministran externamente, nunca se incorporan a la distribución.

## Cualificación reproducible

En Windows, desde la raíz del proyecto:

```powershell
python -m pip install -r .\scripts\distribution\requirements-signing.txt
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_megablock6_7.ps1 -StructuralOnly
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_megablock6_7.ps1
```

`-StructuralOnly`: regresiones nuevas + heredadas sin Maven full-tree.
Sin él: además E2E real en árbol temporal, validación heredada B+D1..5 y
**una sola cualificación global** del producto al final.

Para exigir también tecnologías nativas reales:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_megablock6_7.ps1 -NativeMatrix "ruta/matriz.json" -NativePolicy "ruta/politica.json" -GeneratedRoot "data/generated-projects"
```

La matriz Java/Node de referencia se ejecuta automáticamente en el E2E.
La matriz personalizada exige tecnología, fuentes y test reales del destino.

## Advertencias de seguridad y de certificación

- Una firma Ed25519 sobre ZIP web no equivale a firma del sistema operativo.
- El sistema no instala ni arranca automáticamente ejecutables no confiables.
- Un test de referencia Java no representa un test EMF, Sirius o Xtext.
- El runner manual CI de GitHub Actions ya exige los tests nuevos en Windows,
  Linux y macOS; quedan pendientes los resultados de esos runners reales.
- Todavía deben ejecutarse Maven completo, Vitest, Playwright, runners Tauri
  y matrices externas antes de declarar Fix179 estable.
- `src` Java/TypeScript de producción y documentación adicional Archify se preservan.
