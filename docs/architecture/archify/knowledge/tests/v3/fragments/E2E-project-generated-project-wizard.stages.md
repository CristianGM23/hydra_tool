E2E-project-generated-project-wizard - stage mapping (pw:e2e/real/project/generated-project-wizard.spec.ts)
- generated_projects_author_wizard.rg (registrar): L55 el registro escrito por el backend tiene id, perfil y carpeta; L46 una carpeta fuera de la raiz se rechaza.
- generated_projects_author_wizard.pj (elegir proyecto y simulacion) y .st (elegir etapas): proyecto elegido y Dry run marcado; L66 Launch deshabilitada para el perfil.
- generated_projects_author_wizard.pv (previsualizar): L74-L76 estados PLANNED/PASSED y comandos iguales a los del backend; L84 la carpeta del proyecto sigue vacia.
- generated_projects_author_wizard.ru (ejecutar): L93-L97 ejecucion real de Prepare con FALLO real, informe igual al del backend.
- generated_projects_author_wizard.ok (todo correcto?) y .rp (resultado e informe): L93 detalle del fallo en pantalla; L107-L110 tras reajustar a Open el informe pasa y las insignias coinciden con el backend.
- Limite: el exito real solo se prueba para Open; strength MEDIUM porque el informe se compara con un segundo informe del backend.
