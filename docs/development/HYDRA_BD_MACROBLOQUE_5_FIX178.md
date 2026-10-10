# HYDRA V4.32.107 · Fix178 · B5 + D5

## A. Decisión arquitectónica

HYDRA **sigue siendo la aplicación web principal**. Los editores generados son artefactos separados, potencialmente instalables o desplegables. Este bloque no introduce «HYDRA Studio», identidades, workspaces ni MongoDB.

Se extienden, sin duplicarlos, los contratos de Fix174–Fix177: adaptadores nativos predefinidos, pruebas semánticas vinculadas a desafíos frescos, matriz entre tecnologías, empaquetado web reproducible, firmas Ed25519 y registro inmutable de editores.

## B. B5 · Política de paridad funcional exigible

El nuevo ejecutor `run_native_parity_portfolio.py` consume un **contrato de cobertura mínimo** y ejecuta internamente la matriz B4 mediante sus perfiles permitidos. La política señala los `requiredProfiles`, capacidades exigidas y **número mínimo de escenarios semánticos verificados por perfil**, sin confundir `PASSED` del compilador con prueba de paridad. Se exige un mínimo de dos perfiles tecnológicos diferentes por capacidad y ningún perfil requerido puede quedar sin acreditar.

Una ejecución que carezca de toolchain, cambie los resultados esperados, produzca evidencias antiguas, no aporte digest SHA-256 de evidencia o tenga discrepancias semánticas se bloquea. Un informe previamente guardado se puede analizar con `--from-report`, pero **nunca** produce `CERTIFIED`, porque los informes JSON no equivalen por sí mismos a atestaciones confiables de una nueva ejecución.

### Ejecución local de proyectos externos (solo con autorización explícita)

```powershell
python .\scripts\validation\run_native_parity_portfolio.py --root data/generated-projects --matrix docs/development/HYDRA_BD5_NATIVE_MATRIX_EXAMPLE.json --policy docs/development/HYDRA_BD5_NATIVE_PARITY_POLICY_EXAMPLE.json --execute --report target/bd5/native-portfolio.json
```

Los dos JSON se entregan como **plantillas**, y `REPLACE_WITH_REAL_...` debe cambiarse por directorios de proyectos generados auténticos y sus suites. No se descargan toolchains arbitrarias ni se acepta una lista de comandos del solicitante. Alcance: **solo proyectos, escenarios y capacidades expresamente comprobados**. Las tecnologías externas no se certifican por la existencia del ejemplo.

## C. D5 · Preflight de editor desplegable

`verify_editor_runtime_package.py` no se conforma con un SHA-256 correcto. Inspecciona `index.html` y sus referencias locales a scripts, hojas de estilo y manifiesto, comprueba que existan en el ZIP, bloquea recursos externos declarados e identifica una PWA offline incompleta. El empaquetador Java añade `offlineEnabled` explícito al descriptor de publicación para que el verificador pueda comprobar la declaración. Este análisis estático es adicional al gate de la PWA y no garantiza por sí solo que cualquier navegador o sistema operativo ejecute el editor correctamente.

```powershell
python .\scripts\distribution\verify_editor_runtime_package.py .\ruta\editor-publicado.zip
```

## D. D5 · Editor web autónomo en localhost

`serve_registered_editor.py` abre **solo** una publicación que ya exista en el registro inmutable y que supere integridad de ZIP, hashes de activos desplegados y, cuando corresponda, confianza Ed25519. Usa exclusivamente `127.0.0.1` y bloquea `Host` externos para reducir el riesgo de DNS rebinding; no sirve el árbol fuente de HYDRA, archivos privados del registro, rutas ocultas ni APIs. Cada recurso se vuelve a cotejar contra su hash antes de responder. Ofrece Content-Security-Policy restrictiva, `nosniff`, `no-store`, navegación SPA acotada, `GET`, `HEAD` y un endpoint local de estado.

```powershell
python .\scripts\distribution\serve_registered_editor.py --registry .\ruta\registro --product mi-editor --channel stable --trust-policy .\ruta\politica.json
```

Para publicaciones sin firma, omitir `--trust-policy`; **no se puede marcar un ZIP sin firmar como autenticado**. Para publicaciones firmadas, la política debe estar fijada por un administrador. Este ejecutable de Python es un **runtime local/previsualizador** del editor generado, no un instalador Tauri firmado ni un sustituto del navegador en despliegues remotos. El editor conserva su capa semántica JavaScript; las herramientas nativas externas no se instalan automáticamente.

## E. Pruebas y alcance verificado

La batería nueva incorpora casos positivos y negativos de cobertura entre tecnologías, informes guardados, escenarios insuficientes, firmas SHA-256, referencias a recursos remotos, manifest offline, HTTP local, rutas peligrosas, acceso solo a localhost, cabecera Host, disponibilidad de scripts, integridad ante alteraciones, recursos privados, semántica SPA y métodos HTTP. El recorrido real ejecuta siete escenarios de Node, genera un ZIP con código Java, verifica recursos, publica con dos claves efímeras Ed25519 y sirve de verdad cuatro activos mediante HTTP en localhost.

No se ha ejecutado Maven completo sobre el JDK24/25 ni CI Windows/macOS para esta entrega. No se han ejecutado toolchains externos EMF/Sirius/Xtext/Langium/GLSP/BESSER/ANTLR o instaladores de Tauri nativos. Su soporte permanece **pendiente de certificación**.

## F. Cualificación agrupada (Windows)

Primero instalar la dependencia opcional de firma:

```powershell
python -m pip install -r .\scripts\distribution\requirements-signing.txt
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_block5.ps1 -StructuralOnly
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_block5.ps1
```

Los scripts conservan los gates B1, B2, B3 y B4 y ejecutan la cualificación global **una sola vez al final**. Los archivos adicionales de Archify permanecen fuera del manifiesto canónico, no deben borrarse ni añadirse automáticamente a estos paquetes.
