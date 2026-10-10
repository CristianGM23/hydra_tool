FIX176 Windows paths alias 8.3 — overlay directo.
Partiendo exclusivamente de HYDRA R6C4 Fix176.
Reemplaza solo scripts/validation/verify_bd_block2.py y MANIFEST_SHA256.txt.
No lo uses sobre Fix175 u otro baseline porque contiene MANIFEST_SHA256.txt de Fix176.
Pruebas:
 python .\scripts\validation\verify_bd_block2.py
 python .\scripts\validation\verify_manifest.py --allow-extra
