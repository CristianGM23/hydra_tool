# Instalación, actualización y portabilidad

## Instalación limpia
La RC1 debe funcionar desde rutas con espacios y Unicode y no depender del workspace que generó el ZIP.

## Validación
Ejecuta `python scripts/release/verify_installation_matrix.py --smoke-build` y después `python scripts/release/qualify_rc1.py --full`.

## Actualización
1. Haz snapshot/backup del proyecto.
2. Arranca la nueva versión y deja que las migraciones de esquema se ejecuten mediante el registro canónico.
3. Revisa el informe de migración antes de continuar si se requiere intervención.

## Portabilidad
Exporta el proyecto portable, importa en una carpeta limpia y compara artefactos/fingerprints. Los secretos se vuelven a vincular; no se incluyen en el paquete.
