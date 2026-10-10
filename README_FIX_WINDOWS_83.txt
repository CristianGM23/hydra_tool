HYDRA R6C4 B+D2: correccion de ruta 8.3 Windows para Fix175 y Fix176

1. Extrae el archivo PY del ZIP a una carpeta cualquiera (Descargas, por ejemplo).
2. Abre PowerShell en la raiz de HYDRA.
3. Ejecuta: python "RUTA_COMPLETA_AL_PY"
4. Prueba: python .\scripts\validation\verify_bd_block2.py
5. Verifica: python .\scripts\validation\verify_manifest.py --allow-extra

Se puede ejecutar sobre Fix175 (con o sin el hotfix PWA) o sobre Fix176.
Si aplicas Fix176 despues de esta correccion, vuelve a ejecutar el mismo .py, ya que Fix176 incluye un MANIFEST_SHA256.txt anterior.
No cambia codigo de produccion ni archivos de Archify.
