E2E-project-areas-configure - stage mapping (pw:e2e/real/project/areas-configure.spec.ts)
- project_author_areas.a (modo Guiado/Libre): L40-L42 el interruptor cambia en pantalla; L70-L75 el modo FREE queda en el fichero y en la sesion real.
- project_author_areas.b (marcar/desmarcar areas): L47-L48, L59 casillas cambian; L71 solo QUICK_FIXES y VALIDATIONS quedan guardadas.
- project_author_areas.c (dependencias en ambos sentidos): L47-L48 apagar VALIDATIONS apaga QUICK_FIXES; L59 apagar EVENTS apaga EVENT_FLOWS; encender QUICK_FIXES devuelve VALIDATIONS; L71 resultado guardado.
- project_author_areas.d (confirmar): antes de aplicar el fichero no cambia (L63-L66); aplicar persiste (L70-L75); cancelar no cambia nada (L93-L99).
