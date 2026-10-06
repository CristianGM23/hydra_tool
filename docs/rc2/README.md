# HYDRA 4.32.107 RC2

RC2 es una fase de estabilización. No añade capacidades funcionales al producto: endurece descubrimiento del entorno, diagnóstico, reanudación de cualificaciones, evidencia de procedencia y congelación de release.

Ruta recomendada:
1. `python scripts/doctor/hydra_doctor.py --strict`
2. Windows: `powershell -ExecutionPolicy Bypass -File scripts/release/qualify_rc2.ps1`
3. Otros sistemas: `python scripts/release/qualify_rc2.py --full`
4. Ante un fallo, usar `python scripts/release/diagnose_qualification.py` y continuar con `--resume` o `--rerun-failed`.
