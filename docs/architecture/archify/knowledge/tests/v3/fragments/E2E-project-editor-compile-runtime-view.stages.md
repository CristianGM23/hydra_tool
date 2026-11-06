E2E-project-editor-compile-runtime-view - stage mapping (pw:e2e/real/project/editor-compile-runtime-view.spec.ts)
- model_editor_author_runtime.vw (el disenador salta a la vista Ejecucion): L45 la pestana «Runtime preview» queda activa tras compilar; L33 con cambios sin guardar la orden esta bloqueada y la vista no cambia.
- model_editor_compile.vw (mostrar la vista Ejecucion tras el resultado del backend): L45-L50 la vista muestra nombre y vistas del plan; L60-L63 el plan que sirve el backend tiene el mismo nombre, una vista y la huella de la definicion guardada.
