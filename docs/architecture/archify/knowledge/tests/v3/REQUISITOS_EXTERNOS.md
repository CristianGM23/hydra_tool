# Requisitos externos pendientes de la certificación V3

Etapas que no pueden alcanzar STRONG con los recursos locales actuales. Su estado real se conserva (MEDIUM/LIMITED/sin test);
no se simulan ni se promueven. Cada bloque indica qué hace falta y cómo certificarlas más adelante con el runner V3.
Lista etapa a etapa: `ETAPAS_PENDIENTES_ANALISIS_FASE3.csv` (decisión `BLOCKED_EXTERNAL`).

## A. Proveedores remotos reales (GitHub, GitLab, Microsoft, Google, Dropbox)
Etapas: `x_remote_repository_exchange.{a2,a3,t1,im}`, `x_project_lifecycle_overview.rr`, `repos_connect_sync.{a2,a5}`,
`repos_oauth_connect.{a1,a2,a3,r}`, `repos_file_transfer.{a,b,c,d,r}`, `repos_author_connection.f`,
`repos_author_transfer.{a,b,c,d,r}`, `project_author_import.c`, `project_author_export.c`.

Estado actual: solo hay pruebas contra proveedor simulado en bucle local (MEDIUM): `RemoteOAuthLoopbackExchangeIntegrationTests`,
`RemoteGithubLoopbackTransferIntegrationTests`, `AggressiveRemoteRepositoryRoundTripTests`. `LiveRemoteRepositoryRoundTripTests`
está mapeada pero se omite (SKIPPED) sin credenciales.

Qué hace falta:
- Un repositorio de pruebas desechable por proveedor y un token con permiso de escritura en él.
- Variables de entorno `HYDRA_LIVE_REMOTE_TESTS=true` y, por proveedor, `HYDRA_TEST_<PROVEEDOR>_TOKEN`
  (más `..._OWNER` y `..._REPOSITORY` para GitHub, `..._PROJECT_ID` para GitLab).
- Para OAuth (`a1`, `a2`, `a3`, `r`, `f`): una aplicación OAuth registrada (client id/secret) y un inicio de sesión humano en el
  proveedor (ventana emergente y código de dispositivo con un segundo dispositivo). No hay forma honesta de automatizarlo sin esa
  intervención.
- Cobertura que falta aunque haya credenciales: la prueba en vivo solo hace CREATE_ONLY; para `repos_file_transfer.d` y
  `repos_author_transfer.{c,d}` (versión esperada obsoleta, UPSERT/UPDATE_ONLY) hay que ampliar `LiveRemoteRepositoryRoundTripTests`.

Cómo certificar: exportar las variables, añadir una tarea al manifiesto con `selectors` de `LiveRemoteRepositoryRoundTripTests`
(`python target/archify-v3/tools/add_task.py <json>`), ejecutar `python scripts/archify/v3/hydra_archify_v3.py run --only <id> --force`
y escribir el fragmento STRONG; el pase de fragmentos exige PASS real, no SKIPPED.

## B. Cadenas de herramientas nativas (Maven con red, Node/npm, Eclipse/Xtext/BESSER)
Etapas: `x_grammar_to_text_model.tc`, `x_integrate_technology_native.par`, `integration_native_parity.{nat,ass,cert,ok}`.

Estado actual: `NativeGeneratedProjectAcceptanceTests` se omite salvo `-Dhydra.native.validation=true`; el proof `compile-emf-module`
necesita `mvn` y acceso a Maven Central; `GrammarNativeToolchainServiceTests` usa un ejecutor simulado (MEDIUM). Sin eso solo se
alcanzan los veredictos UNVERIFIED/FAILED y el certificado con `certified=true` es inalcanzable.

Qué hace falta: JDK con `mvn` en el PATH y red hacia Maven Central, Node.js con `npm` y red hacia el registro npm, y Python con
`pytest` para BESSER.

Cómo certificar: ampliar el runner con argumentos Maven por tarea (`-Dhydra.native.validation=true`) o ejecutar esa clase con
`mvnw.cmd -B test -Dtest=NativeGeneratedProjectAcceptanceTests -Dhydra.native.validation=true` y registrar el XML de Surefire en el
almacén inmutable antes de escribir el fragmento.

## C. Etapas sin prueba posible por su naturaleza (`NOT_TESTABLE`)
Decisiones humanas sin efecto en código, pantallas puramente informativas y pasos de elección que solo existen en la mente de la
persona usuaria. Se mantienen como MEDIUM/LIMITED; no se infla la clasificación. Lista en el CSV (decisión `NOT_TESTABLE`).

## D. Publicación (separada de la certificación funcional)
La cualificación completa de release (matriz de GitHub Actions entre sistemas operativos, firma y verificación de publicación)
queda fuera de la certificación funcional y no se ejecuta aquí. Véase `RESULTADOS_FASE3.md`, fase 4.
