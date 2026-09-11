# Panel de expertos v9 — reintento con oleadas — progreso (2026-09-11)

HEAD de partida: `07f935b` (auditoría panel expertos v9 2026-09-13, que
documentó que el panel de 13 no pudo ejecutarse dos rondas seguidas por
`session limit`). Working tree limpio al empezar este reintento.

Reglas de esta ronda: máximo 4-5 subagentes Task simultáneos, oleadas
secuenciales, consolidar antes de avanzar. Si una oleada falla por
`session limit`: esperar ~10 min, reintentar UNA vez esa oleada; si vuelve a
fallar, documentar el error exacto y continuar con la siguiente oleada sin
inventar hallazgos.

## Reparto de oleadas

- **Oleada A (4):** estética, funcionalidad end-to-end, accesibilidad WCAG AA,
  UI/componentes.
- **Oleada B (5):** UX, programador senior, arquitectura, QA/bugs, seguridad
  MASVS.
- **Oleada C (4):** privacidad/RGPD, rendimiento, red/offline/sync, cobertura
  de tests.

## Estado

- [x] Oleada A — COMPLETADA (4/4 subagentes con hallazgos verificados: estética 3 nuevos/3 ya resueltos, funcionalidad 1 nuevo/3 ya resueltos/1 abierto, accesibilidad 5 nuevos/1 abierto/8 patrones ya resueltos, UI/componentes 3 nuevos/1 abierto)
- [x] Oleada B — COMPLETADA (5/5: UX 2 nuevos/4 ya resueltos, programador senior 1 nuevo/3 abiertos, arquitectura 0 nuevos puros/2 ya resueltos/3 abiertos, QA/bugs 1 nuevo CRÍTICO, seguridad MASVS 2 nuevos/4 ya resueltos/3 abiertos). HALLAZGO CLAVE convergente (QA + seguridad, independientes): el fix de sucesión de ownerId al expulsar al owner (ronda 09-13) NO funciona en la práctica — firestore.rules:307 exige isOwner(hid) para el PATCH de households/{hid}, pero el caller de deleteMember en ese escenario siempre es un admin no-owner, así que el PATCH de updateHouseholdOwner recibe 403 silencioso. El bug original sigue abierto pese a estar marcado [APLICADO].
- [ ] Oleada C — lanzada / resultado
- [ ] Consolidación final + informe `docs/review-panel-expertos-2026-09-11.md`
- [ ] Verificación (`compileDebugKotlinAndroid`, `jvmTest`)
- [ ] Commit único

(Este archivo se actualiza tras cada oleada para poder retomar si la sesión
se corta a mitad.)
