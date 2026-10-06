# Cualificación reanudable

El motor compartido guarda checkpoints en `target/release/checkpoints` y logs por etapa en `target/release/logs`. Los checkpoints están ligados a un fingerprint de fuentes y toolchain; no se reutilizan después de un cambio relevante.

Opciones:
- `--resume`: reutiliza etapas PASS compatibles.
- `--rerun-failed`: conserva PASS y repite fallos/missing.
- `--from <etapa>`: continúa desde una etapa concreta cuando existe evidencia previa válida.
- `--only a,b`: ejecuta un subconjunto para diagnóstico.

Los timeouts finalizan el árbol completo de procesos, no solo el proceso padre.
