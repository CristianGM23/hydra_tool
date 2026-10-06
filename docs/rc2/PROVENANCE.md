# Procedencia de release

`generate_provenance.py` crea `target/release/release-provenance.json`. Incluye manifiesto, ZIP fuente, SBOM, evidencia de cualificación, objetivo de bytecode, toolchain real, sistema operativo y arquitectura.

RC2 añade `rc2-certificate.json` y `rc2-freeze.json`. Un PASS de RC2 activa feature freeze: los cambios posteriores deben ser correcciones de defectos o provocar una nueva candidata.
