# HYDRA Fix180 — Primera entrega funcional D8, preparación de B8/B9 y D9

**Base:** HYDRA V4.32.107 R6C4 Fix179, incluyendo la reparación de PowerShell Ed25519.  
**Estado:** incremento de desarrollo y regresiones focalizadas. **No es Fix180 completo ni certificación de instaladores Tauri.**

## Alcance comprobado

Se modifica el **servidor Java 21 realmente emitido por `StandaloneEditorGeneratorService`** (recurso de producción `src/main/resources/standalone-template/backend/src/HydraStandaloneServer.java.template`). No se crea un servidor sustituto que pase las pruebas pero no se utilice en el producto.

1. El servidor comprueba el JSON como objeto, con decodificación UTF-8 estricta, profundidad limitada y coherencia del identificador de modelo con su URL. Impide basura binaria, errores sintácticos y sustituciones silenciosas de identidad. **No pretende sustituir la validación de metamodelo, de referencias o de EVL**; los borradores pueden seguir persistiendo aunque tengan problemas semánticos.
2. Cada **creación** necesita `If-None-Match: *`. Cada **actualización** necesita `If-Match` con el hash ETag SHA-256 obtenido al leer el modelo. Las escrituras simultáneas no ocultan pérdidas: si varias peticiones usan la misma revisión, solo una la sustituye; las otras reciben `412`.
3. Cada **borrado** también requiere ETag y el servidor ignora/rechaza enlaces simbólicos del modelo y la aplicación.
4. Las aplicaciones gráficas y textuales emitidas por HYDRA incluyen el encabezado correspondiente cuando el usuario pulsa Guardar.
5. El servidor generado admite `--data-root=/ruta/persistente/modelos` para separar el almacenamiento de los binarios y recursos de la aplicación. La opción es **opt-in**, preservando el comportamiento anterior por defecto (`models/`). La documentación del generador aconseja utilizar una ruta persistente antes de actualizar los binarios.

## Pruebas ejecutables

`python scripts/validation/verify_fix180_generated_model_lifecycle.py` compila **el recurso Java real** con `javac --release 21`, lo lanza mediante `java`, realiza peticiones HTTP y vuelve a arrancar el proceso. Abarca apertura, creación, serialización, edición, concurrencia entre ocho clientes, borrado, reemplazo de archivos de la aplicación sin pérdida de modelos, límites de tamaño, JSON inválido, UTF-8 inválido, symlinks y solicitudes peligrosas. No utiliza respuestas HTTP simuladas.

Para Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_fix180_stage1.ps1 -StructuralOnly
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_fix180_stage1.ps1
```

El segundo comando conserva un **solo gate global heredado al final**. El workflow multiplataforma ejecuta estas pruebas Java HTTP en Windows, macOS y Linux, pero **no** instala un MSI/DMG/DEB ni acredita por sí solo ciclos de vida Tauri. No se sobreescribe `docs/architecture/archify`.

## Pendientes P0 de Fix180 y Fix181

- **B8/B9:** completar corpus auténtico generado por HYDRA, vincular hashes de instantánea, exportador, archivos y pruebas semánticas, y ejecutar EMF y Langium con herramientas oficiales. No se certifica por perfil declarado ni únicamente por compilar.
- **B9/B11:** cubrir cardinalidades, contención, referencias cruzadas, reglas EVL, Quick Fixes, eventos, gamificación, transformaciones, pérdidas y round-trip; siete escenarios siguen siendo solo una regresión.
- **D8/D9:** producir e instalar bundles Tauri, abrir/editar/guardar/reiniciar, y comprobar actualizaciones firmadas y rollback por cada sistema operativo. Los tests actuales prueban **el servidor local generado**, no la instalación del sistema operativo.
- **D10/D11:** migraciones de esquemas, copia de seguridad y recuperación tras interrupción. Separar datos de binarios es necesario pero no suficiente.

Cualquier capacidad que carezca de ejecución real deberá reportarse `NOT_EXECUTED`, `PARTIAL` o `UNSUPPORTED`, sin afirmar compatibilidad universal.
