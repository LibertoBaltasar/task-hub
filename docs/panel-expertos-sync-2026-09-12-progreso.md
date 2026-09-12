# Panel de expertos v10 — foco sincronización entre dispositivos — progreso (2026-09-12)

HEAD de partida: `9e3641085fa59c4e2b15adff10512f716f607f78` (`feat: elimina
la auth anónima, Task Hub pasa a requerir login con Google`). Revisión NUEVA
(no continuación de v9). Working tree limpio al empezar.

**Cambio de contexto clave respecto a encargos anteriores**: el encargo 00
(`docs/auditoria-sync-dispositivos-2026-09-12.md`, HEAD `8c3f3d3`) diagnosticó
y arregló (`8cc954d`) el huérfano anónimo→Google vinculando la credencial. Un
encargo posterior (`docs/google-only-auth-2026-09-12.md`, commit `9e36410`,
SIN PUSH) fue más allá: **eliminó la auth anónima por completo** — Task Hub
ahora exige login con Google desde el arranque (`AuthGateScreen`). Esto deja
obsoleto el mecanismo de vinculación (`AccountLinkingRules`, borrado) porque
ya no existe una sesión anónima activa que vincular. El foco de esta ronda es
verificar que el flujo Google-only en sí sincroniza bien entre dispositivos
(2º dispositivo, mismo dispositivo tras logout/login, borrado de cuenta,
iOS/JVM sin Google Sign-In real ya documentado como propuesta pendiente).

Reglas de oleadas: máximo 4-5 subagentes Task simultáneos, secuenciales,
consolidar (commit) antes de avanzar. Si una oleada falla por `session limit`:
esperar ~10 min, reintentar UNA vez; si vuelve a fallar, documentar y seguir
con la siguiente oleada sin fabricar hallazgos.

## Reparto de oleadas

- **Oleada A (4):** (1) Red/offline/sincronización, (2) Arquitectura, (3) QA y
  bugs, (4) Seguridad/AppSec.
- **Oleada B (5):** (5) Programador senior, (6) Funcionalidad end-to-end,
  (7) Cobertura de pruebas (solo informa), (8) UX, (9) UI y componentes.
- **Oleada C (4):** (10) Estética/visual, (11) Accesibilidad WCAG AA,
  (12) Rendimiento, (13) Privacidad/RGPD/menores.

## Estado

- [ ] Oleada A — en curso
- [ ] Oleada B
- [ ] Oleada C
- [ ] Consolidación final + informe `docs/review-panel-expertos-sync-2026-09-12.md`
- [ ] Verificación (`compileDebugKotlinAndroid`, `jvmTest`)
- [ ] Commit único

(Este archivo se actualiza tras cada oleada para poder retomar si la sesión
se corta a mitad.)
