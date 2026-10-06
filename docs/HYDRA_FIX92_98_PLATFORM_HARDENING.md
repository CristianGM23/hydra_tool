# HYDRA Fix92–Fix98 — Platform hardening

- **Fix92**: presupuesto global de procesos externos, timeout y captura de salida acotados además de los límites por módulo.
- **Fix93**: correlationId propagado de HTTP a jobs y registro temporal acotado de hitos operativos.
- **Fix94**: fault injection explícito, desactivado por defecto y limitado a failpoints registrados.
- **Fix95**: inventario operativo de cachés con límites, políticas e indicadores de hits/misses/evictions.
- **Fix96**: primer puerto neutral `MetamodelQueryPort`; runtime nativo de transformaciones deja de depender de la implementación application del bounded context metamodel.
- **Fix97**: contenedor portable con manifiesto, versión, SHA-256 y prohibición explícita de secretos.
- **Fix98**: registro central de deprecaciones HTTP, headers `Deprecation`/`Sunset` y endpoint de compatibilidad para clientes generados.
