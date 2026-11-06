E2E-project-tree-browse-open - stage mapping (pw:e2e/real/project/tree-browse-open.spec.ts)
- project_author_tree.a (desplegar grupos): L49 cada fila lleva el tipo y la ruta que da el arbol del backend; L54-L56 plegar y desplegar un grupo.
- project_author_tree.c (abrir el elemento): L68 el editor muestra el texto exacto del disco; L71 el servicio devuelve el mismo contenido; L61 doble clic en un grupo no abre nada.
- project_author_tree.f (aviso de cambio externo): L101 solo el fichero reescrito en disco se marca; L102-L103 los demas no; L108 el backend sirve el contenido nuevo; L113 Recargar limpia el aviso.
- project_author_tree.b (decision abrir/seleccionar): DIRECT, test 3: doble clic abre un solo fichero y el backend lo registra (L130); Ctrl+clic suma sin abrir nada (L138); cancelar el borrado no toca el disco (L147); confirmar borra solo los dos seleccionados en disco y arbol (L156-L163). Ademas, el ZIP de la seleccion se prueba en export-selection-zip.
