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
- [x] Oleada C — COMPLETADA en el reintento (falló 4/4 la primera vez por `session limit`, se reintentó UNA vez y los 4 subagentes produjeron hallazgos: privacidad/RGPD 2 nuevos/2 abiertos/2 resueltos, rendimiento 2 nuevos/4 abiertos, red/offline/sync 1 nuevo CRÍTICO/3 abiertos/1 resuelto, cobertura de tests 3 nuevos/2 resueltos/1 abierto)
- [x] Consolidación final + informe `docs/review-panel-expertos-v9-reintento-2026-09-11.md` (nombre corregido para no colisionar con el informe preexistente de la cadena 2026-09-11)
- [x] Verificación: `compileDebugKotlinAndroid` BUILD SUCCESSFUL, `jvmTest` BUILD SUCCESSFUL (216 tests, 0 fallos, sin regresión)
- [x] Commit único

**RONDA COMPLETADA.** Panel de 13 expertos ejecutado por entero en 3 oleadas
(9/9 a la primera en A+B, 4/4 tras un reintento en C). 8 correcciones
aplicadas (2 críticas: pérdida de perfil por falta de updateMask, bloqueo de
acciones sobre el owner del hogar; 1 RGPD; resto accesibilidad/UI/UX/seguridad
menor), resto documentado como propuesta. Ver informe final para el detalle
completo.

(Este archivo se actualiza tras cada oleada para poder retomar si la sesión
se corta a mitad.)
