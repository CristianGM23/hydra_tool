# HYDRA V4.32.107 — Macrobloque B4+D4 — Fix177

## Alcance y propósito

HYDRA sigue siendo la aplicación web de autoría. **No se crea una aplicación «HYDRA Studio»**.
Los artefactos de distribución son editores independientes generados por HYDRA.
Este desarrollo no requiere usuarios, workspaces ni MongoDB.

### B4: matriz de paridad semántica real entre tecnologías

- `scripts/validation/run_native_parity_matrix.py` agrega suites nativas existentes (`run_native_parity_suite.py`) sin aceptar comandos arbitrarios.
- En cada grupo son obligatorios **dos perfiles tecnológicos distintos como mínimo**. Dos proyectos web con el mismo perfil no se anuncian como prueba de paridad intertecnológica.
- Cada proyecto debe declarar el mismo conjunto de escenarios canónicos para el grupo. Se comprueban identificadores, capacidades y resultados esperados mediante una representación JSON estable (las diferencias en el orden de las propiedades no invalidan la equivalencia).
- Se exige certificación de las capacidades requeridas para cada destino, ejecución real y evidencias frescas ligadas al reto generado por el ejecutor. La simulación o ejecución meramente estructural nunca concede PASS.
- El informe distingue cobertura probada, cobertura ausente, divergencias semánticas y toolchains que no llegaron a ejecutarse. Nunca afirma paridad universal.
- Los proyectos externos deben preexistir bajo una raíz seleccionada. La matriz solamente acepta perfiles cerrados (`emf`, `sirius`, `xtext`, `langium`, `glsp`, `besser`, `antlr`, `generated-web`) y sigue el sistema de ejecución segura heredado. Ejecutar comandos de construcción requiere `--execute` explícito y **solo debe realizarse con proyectos de confianza**.

Ejemplo de especificación: `docs/development/HYDRA_BD4_NATIVE_MATRIX_EXAMPLE.json`.

```powershell
python scripts/validation/run_native_parity_matrix.py --root data/generated-projects --matrix docs/development/HYDRA_BD4_NATIVE_MATRIX_EXAMPLE.json --execute --timeout-seconds 900 --report target/bd4/matrix.json
```

El ejemplo presupone que existen proyectos Xtext y Langium verdaderos, generados y probados; **no se suministran fixtures falsos**. Hasta ejecutarlos, el estado es UNVERIFIED.

### D4: publicación firmada de editores web

- `editor_release_trust.py`: firma Ed25519 separada y verificable con claves públicas fijadas por el administrador. La identidad firmada liga **producto, canal, versión y SHA-256 del ZIP completo**.
- Política JSON de confianza con lista de claves permitidas, canales autorizados y umbral de firmas. Admite validación por varios firmantes independientes.
- `editor_release_registry.py`: al habilitar una política de confianza, promoción, inspección, actualización y rollback **rechazan releases sin firmas válidas**, modificaciones de datos y ausencia de claves confiables. El registro conserva el ZIP original, el descriptor firmado y el release inmutable. Un modo legacy sin política sigue estando permitido, pero nunca acredita autenticidad.
- No se incluyen claves privadas en los paquetes; deben almacenarse **fuera del repositorio**, en el sistema de secretos institucional o CI de confianza. No se suministra ningún par de claves predeterminado.

Dependencia opcional para firma y verificación (no afecta al inicio del HYDRA web principal):

```powershell
python -m pip install -r scripts/distribution/requirements-signing.txt
```

Formato de política de confianza:

```json
{
  "schemaVersion": 1,
  "algorithm": "Ed25519",
  "channels": ["stable", "beta"],
  "minimumSignatures": 2,
  "trustedKeys": [
    {"id":"release-team", "publicKey":"BASE64_DE_32_BYTES"},
    {"id":"security-team", "publicKey":"BASE64_DE_32_BYTES"}
  ]
}
```

Uso con dos firmas independientes para **un ZIP web ya compilado y verificado**:

```powershell
python scripts/distribution/editor_release_trust.py sign --product business-editor --channel stable --version 1.0.0 --package editor.zip --signature release-signature.json --key-id release-team --private-key C:\secrets\release-team.pem
python scripts/distribution/editor_release_trust.py sign --product business-editor --channel stable --version 1.0.0 --package editor.zip --signature release-signature.json --key-id security-team --private-key C:\secrets\security-team.pem
python scripts/distribution/editor_release_trust.py verify --product business-editor --channel stable --version 1.0.0 --package editor.zip --signature release-signature.json --trust-policy C:\secrets\trusted-publishers.json
python scripts/distribution/editor_release_registry.py promote --registry data/standalone-publications --product business-editor --channel stable --package editor.zip --signature release-signature.json --trust-policy C:\secrets\trusted-publishers.json
python scripts/distribution/editor_release_registry.py verify --registry data/standalone-publications --product business-editor --channel stable --trust-policy C:\secrets\trusted-publishers.json
```

Rotación de claves: actualizar la política de confianza con un período de solape antes de retirar la clave anterior. Si se retira una clave antes de volver a firmar versiones históricas, estas **dejarán de pasar** las verificaciones de rollback, como es correcto en modo fail-closed.

### D4: integridad de instaladores nativos

- `verify_native_installer.py` comprueba el resultado **ejecutado** de la construcción Tauri, los formatos nativos esperados de cada sistema operativo, las rutas, el tamaño y los hashes de los bundles reales. No acepta un plan teórico, archivos inexistentes ni paquetes de otra plataforma.
- El workflow manual `hydra-generated-editor-matrix.yml` ha sido ampliado para instalar la dependencia opcional, ejecutar la regresión B4+D4 y generar los inventarios de Windows, Linux y macOS tras construir bundles Tauri reales.
- Los inventarios **no sustituyen** la firma de instaladores por Microsoft/Apple u otra entidad de confianza; tampoco demuestran instalación/arranque real. Para certificar esas propiedades hace falta ejecutar la matriz y pruebas de instalación en los respectivos runners y equipos.

## Validación agrupada

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_block4.ps1 -StructuralOnly
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_block4.ps1
```

Si ya existen los proyectos tecnológicos reales de una matriz de paridad, la misma cualificación puede ejecutarlos con `-NativeMatrix` y `-GeneratedRoot`, lo que implica permitir la ejecución explícita de sus scripts de build/tests **sobre proyectos confiables**.

## Alcance explícitamente no certificado

- No se declara paridad integral de EMF/Sirius/Xtext/Langium/GLSP/BESSER hasta tener proyectos nativos reales con los mismos escenarios semánticos y ejecutarlos.
- La validez criptográfica de un ZIP firmado **no equivale** a una firma de código del sistema operativo, a un servidor de actualizaciones remoto ni a instaladores certificados.
- El componente web principal de HYDRA no cambia. No se ha añadido un servicio REST de ejecución arbitraria.
- Fix177 preserva la corrección del alias Windows 8.3 introducida tras Fix176.
