E2E-project-home-entry - stage mapping (pw:e2e/real/project/home-entry.spec.ts)
- project_author_home.a (decision: acciones / continuar / reciente): las tres ramas se recorren (Continuar L43, recientes L48-L63, acciones principales L87-L121) y se rechaza un reciente sin carpeta (L57) y una carpeta inexistente (L99).
- project_author_home.c (Continuar): el artefacto principal queda en el fichero del proyecto (L23) y Continuar abre su modulo (L43).
- project_author_home.d (abrir un reciente, hasta 6): 6 tarjetas aunque el registro tenga mas (L48); abrir uno lo deja activo y primero del registro (L63).
- project_author_home.b (accion principal): DIRECT; abrir y crear (L87, L116-L121), importar un ZIP real desde la tarjeta (L171-L173: proyecto activo y bytes iguales) y conectar (L154: el dialogo de repositorio rechaza sin datos y no cambia nada). Conectar con exito NO se prueba (servicio externo).
