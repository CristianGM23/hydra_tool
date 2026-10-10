# HYDRA — notas de orquestación para Claude Code

- Ejecutor principal: Sonnet 5.5, esfuerzo medium (ver `.claude/settings.json`). No cambiar de
  modelo para tareas normales de implementación, Archify, validación o integración del portal.
- Revisión estratégica (Opus): solo para una decisión arquitectónica genuinamente difícil.
  Actívala tú mismo con `/advisor opus` al empezar esa decisión y `/advisor off` al terminarla.
  Nunca la actives para leer ficheros, grep, git status, ediciones normales del portal o
  validaciones de Archify.
- **Regla de estado fuente de HYDRA**: nunca uses un SHA de Git histórico/hard-codeado (de una
  conversación anterior, un README desactualizado, etc.) como línea base de arquitectura. Al
  empezar cualquier trabajo de arquitectura: (1) resuelve el HEAD actual dinámicamente
  (`git rev-parse HEAD`); (2) inspecciona cambios staged, unstaged y untracked relevantes;
  (3) lee `docs/architecture/archify/knowledge/baseline.json`; (4) compara el estado previamente
  analizado contra el estado actual del repositorio; (5) reanaliza solo la evidencia cambiada o
  afectada, nunca todo el repositorio por defecto; (6) el árbol de trabajo local actual es la
  fuente de verdad última, no lo que diga la caché o una conversación pasada. Reutiliza siempre
  evidencia ya `VALIDATED` en `capabilities-cache.json`/`gaps.json` cuando siga sin cambios.
- Para una búsqueda de evidencia puntual y acotada, prefiere el subagente `hydra-evidence-scout`
  (Haiku, solo lectura) en vez de reabrir una exploración amplia. Sin subagente por defecto; varios
  en paralelo solo si son investigaciones realmente independientes.
