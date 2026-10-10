# HYDRA B+D — Bloque integrado 1: evidencia funcional y distribución

## Baseline y alcance

- Base fuente: HYDRA V4.32.107 R6C4 Fix173, sin los extras de Archify del workspace local.
- La aplicación HYDRA principal continúa siendo web; los ejecutables nativos son **editores generados**, no un segundo producto HYDRA.
- Se preservan las APIs previas, la separación del paquete fuente canónico de los documentos adicionales de Archify y los motores existentes de validación nativa, importación, round-trip y distribución.
- No se sobrescriben archivos de `docs/architecture/archify/`.

## B — Certificación por evidencias

1. El servidor reconstruye la exportación a partir del perfil canónico.
2. El validador nativo ya existente materializa y ejecuta los comandos **generados** con límites de tiempo y recursos.
3. El catálogo de pruebas asigna únicamente pruebas revisadas a capacidades de alcance suficientemente estrecho.
4. El evaluador exige `NATIVE_REQUIRED`, resultado nativo PASS, mismo ID y mismos argumentos de comando, código de salida esperado y prueba marcada como requerida.
5. Lo no probado se informa como `UNVERIFIED`; un fallo de ejecución se informa como `FAILED`.
6. `certified=true` implica **solo** que se han superado todas las capacidades solicitadas en esa ejecución, **no** que toda la plataforma destino tenga paridad completa.
7. La respuesta conserva resultados individualizados, duraciones/errores en los informes existentes y razones de bloqueo.

### Cobertura conservadora inicial de evidencias

| Tecnología | Alcance que puede demostrar la suite generada |
| --- | --- |
| EMF / Sirius / Xtext / GLSP / BESSER WME | Compilación o test del proyecto tecnológico (`NATIVE_BUILD`) |
| Langium | Generación, compilación y prueba de servicios de lenguaje (`LANGUAGE_SERVICE_SMOKE`) |
| BESSER SDK | Modelo canónico, eventos, gamificación y ciclo de vida presentes en pytest generado |
| VS Code | Ciclo de vida de la extensión generado y probado |
| mxGraph / draw.io | Comportamiento frente a hosts de prueba simulados (`GRAPHICAL_BRIDGE_SMOKE`), **no** uso real en el host final |

La cobertura de capacidades profundas (Quick Fix, validaciones semánticas avanzadas, M2M/M2T/T2M,
round-trip bidireccional con código manual, experiencia gráfica/textual en host real) **sigue pendiente**
hasta añadir fixtures y pruebas semánticas E2E específicos. No se infiere una paridad no demostrada.

## D — Readiness real y límites

- `plan` ahora genera la estructura mínima de un proyecto Tauri 2 (configuración, Cargo, Rust, permisos mínimos).
- `validate` conserva su contrato previo de validación de **fuentes**.
- `release-readiness` requiere un `dist/` construido, activos JS/CSS locales referenciados, PWA manifest/worker distribuidos y, para escritorio, fuente Rust/Cargo completa.
- `package` conserva su comportamiento compatible para distribución de fuentes.
- `package-release-source` vuelve a comprobar readiness en servidor antes de empaquetar y declara expresamente que el ZIP **no es un instalador compilado ni firmado**.
- Los archivos secretos `.env`, claves y certificados no entran al paquete.
- **Pendiente**: generación automática de instaladores reales `msi/nsis/dmg/AppImage/deb` según plataforma y capacidades, firma, actualizaciones, rollback y pruebas funcionales del editor aislado.

## Estrategia de bloques grandes siguientes

**Bloque B2+D2 — toolchains y ejecución autónoma integral:** una sola entrega con fixtures reales por familia, pruebas positivas/negativas de parser/modelo/editor/evento y empaquetado web completamente autosuficiente con backend/local runtime cuando sea necesario. CI con matrices por tecnología y host.

**Bloque B3+D3 — paridad avanzada y publicación final:** equivalencia canónica de validaciones, Quick Fixes, EventFlow, gamificación, M2M/M2T/T2M y round-trip bidireccional; instaladores nativos, actualizaciones firmadas, pruebas de migración/rollback, rendimiento y seguridad por OS. Los fallos se agrupan por raíz antes de nuevos desarrollos.

## Gate único Windows

```
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_block1.ps1
```

`-StructuralOnly` ejecuta la cualificación rápida; sin él ejecuta también el gate global de Fix173. La cualificación nativa completa es obligatoria antes de marcar la entrega como lista para publicación.
