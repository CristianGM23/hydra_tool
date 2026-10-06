# Campaña agresiva de repositorios e integraciones

Esta extensión de la campaña no repite los contratos existentes. Añade pruebas metamórficas, concurrentes, de round-trip y de corrupción deliberada.

## Frontera navegador/API

`HydraAggressiveRemoteIntegrationBrowserFlow.test.ts` añade evidencia desde el cliente web: conexión de perfil, exportación binaria mediante `FormData`, importación con versión remota, preservación de rutas con caracteres especiales y secuencia de integración `preview -> compile -> apply` con IDs y directorios no triviales. Esta suite es distinta de los contratos backend porque comprueba la serialización real que sale del navegador.

## Repositorios externos

La suite `AggressiveRemoteRepositoryRoundTripTests` recorre los seis tipos de proveedor del núcleo y demuestra: conexión no destructiva, CREATE_ONLY, lectura posterior, preservación SHA-256, UPDATE_ONLY, control optimista con versión obsoleta, rechazo de traversal y aislamiento concurrente entre perfiles.

`AggressiveRemoteProviderProtocolTests` verifica que los cinco adaptadores HTTP reales construyen el endpoint de conexión específico del proveedor, colocan la credencial solo en `Authorization` y nunca en la URL, y no realizan escrituras durante el test de conexión.

`LiveRemoteRepositoryRoundTripTests` es opt-in. Con `HYDRA_LIVE_REMOTE_TESTS=true` y variables `HYDRA_TEST_<PROVIDER>_*` realiza una conexión real y un write/read de un JSON único bajo `hydra-live-tests/`. No contiene secretos en el repositorio. Los proveedores no configurados quedan SKIPPED. Como el SPI actual no ofrece delete remoto neutral, los artefactos live deben limpiarse manualmente si se desea.

Variables mínimas: `HYDRA_TEST_GITHUB_TOKEN`, `HYDRA_TEST_GITHUB_OWNER`, `HYDRA_TEST_GITHUB_REPOSITORY`; `HYDRA_TEST_GITLAB_TOKEN`, `HYDRA_TEST_GITLAB_PROJECT_ID`; y `HYDRA_TEST_ONEDRIVE_TOKEN`, `HYDRA_TEST_GOOGLE_DRIVE_TOKEN`, `HYDRA_TEST_DROPBOX_TOKEN`. Se admiten `*_ROOT_PATH`, `*_BASE_URL`, `*_BRANCH`, `*_DRIVE_ID`, `*_ROOT_ITEM_ID` y `*_REPOSITORY_URL` cuando el proveedor lo requiera.

## Artefactos e integraciones

`AggressiveGeneratedArtifactLifecycleTests` aplica una propiedad metamórfica a EMF, Sirius Desktop, Xtext, Langium, Eclipse GLSP, BESSER SDK, BESSER Web Modeling Editor, Eclipse Modeling Distribution, VS Code y mxGraph/draw.io: dos compilaciones equivalentes deben producir el mismo conjunto de rutas y el mismo ZIP binario; el ZIP debe poder reabrirse conservando todos los bytes y no puede filtrar rutas locales, tokens o cabeceras de autorización.

`AggressiveTechnologyArchiveSecurityIntegrationTests` manipula deliberadamente ZIPs ya parseables: traversal terminal, segmentos `.`, rutas Windows/UNC, separadores duplicados, payload alterado y entradas duplicadas. Cada corrupción debe rechazarse antes de que el paquete pueda inyectarse en un proyecto.

Como consecuencia se endurecen tanto `TechnologyGeneratedFile` como `TechnologyCompilationArchiveService`: solo aceptan rutas relativas canónicas sin segmentos vacíos, `.`/`..`, rutas absolutas o drive letters.
