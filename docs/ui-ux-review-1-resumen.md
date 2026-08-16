# Revisión UI/UX — Task Hub · Resumen Ejecutivo

**Alcance:** 1 `Theme.kt`, 20 pantallas y 3 componentes de `org.taskhub.ui` (Compose Multiplatform + Material 3, Voyager, Koin). Contrastes WCAG verificados por script (fórmula de luminancia sRGB). La lista de colores del enunciado es **correcta** y los ratios previos se **confirman**, con dos matices (ver abajo).

---

## Diagnóstico general

Task Hub es una app **funcional y coherente en su lógica**, pero con un problema transversal de **accesibilidad de contraste**: la marca usa teal/coral en fondos saturados con texto blanco, y esos pares fallan AA en las acciones y la navegación más importantes. A esto se suma una **inconsistencia estructural** (dos tipos de top bar), un **formulario de creación sin validación** y un uso generalizado de **emoji como sustituto de iconos y de texto semántico**.

**Matices sobre los ratios previos:**
- Blanco/Teal500 = 3.05 y blanco/Coral500 = 3.07 **no fallan** el umbral large (≥3.0), pero se usan en botones con texto normal, así que el fallo AA se mantiene y el margen es despreciable.
- Hallazgo nuevo no listado: "HUB" del splash = Coral500 sobre Teal800 = **1.82:1** (falla incluso large).

---

## Top 10 cambios por impacto/esfuerzo

| # | Cambio | Impacto | Esfuerzo | Relación |
|---|--------|---------|----------|----------|
| 1 | Unificar top bars en un `TaskHubTopBar` basado en `TopAppBar` (fondo `surface`, `ArrowBack`) | Alto (14 pantallas, resuelve C1+I1+I3-back+M3) | Bajo-Medio | ★★★★★ |
| 2 | Cambiar CTAs a `Teal800`/`Coral700` con blanco (o texto oscuro sobre `Teal100`/`Coral100`) | Alto (todos los botones primarios) | Bajo (search-replace de `containerColor`) | ★★★★★ |
| 3 | Corregir splash "HUB" (1.82:1) → `Coral100` sobre `Teal800` | Medio (primera impresión) | Muy bajo (1 línea) | ★★★★★ |
| 4 | Crear `PointsBadge` reutilizable (fondo `Coral700`+blanco o `Coral50`+`Coral800`) | Medio (información gamificada) | Bajo | ★★★★★ |
| 5 | Validar "Nueva tarea": deshabilitar Crear sin título + `isError`/`supportingText` + `DatePicker` | Alto (flujo principal) | Medio | ★★★★☆ |
| 6 | Arreglar tema Naturaleza (`primary=Green800`, `onPrimary=blanco` 5.74:1) | Medio (tema completo) | Muy bajo (Theme.kt) | ★★★★★ |
| 7 | Sustituir emoji por `Icon` con `contentDescription` en acciones (⚙️🔔🗑️✏️📤) y roles (👑/🧒) | Medio (accesibilidad) | Medio (muchos puntos) | ★★★★☆ |
| 8 | Garantizar touch target ≥48 dp (mini-FABs 40 dp, delete 36 dp, emoji 40 dp) | Medio | Bajo (`minimumInteractiveComponentSize`) | ★★★★☆ |
| 9 | Definir `Typography` propia y eliminar `fontSize`/`sp` literales (8–72 sp) | Medio (jerarquía/legibilidad) | Medio | ★★★☆☆ |
| 10 | FAB: iconos diferenciados (`Add`/`GroupAdd`) + estado `Close` al abrir menú | Bajo-Medio | Muy bajo | ★★★★☆ |

---

## Vistazo por prioridad

- **CRÍTICOS (6):** C1 top bars Teal600 3.61:1 · C2 CTAs Teal500/Coral500 3.05–3.07:1 · C3 splash "HUB" 1.82:1 · C4 badges Coral500 3.07:1 · C5 tema Naturaleza Green700 4.12:1 · C6 formulario sin validación. → `ui-ux-review-2-critico.md`
- **IMPORTANTES (6):** I1 top bars inconsistentes · I2 emoji como único diferenciador · I3 touch targets <48 dp · I4 contentDescription ausente · I5 FAB ambiguo · I6 jerarquía tipográfica. → `ui-ux-review-3-importante.md`
- **MENORES (10):** M1 texto de color insuficiente · M2 densidad de tarjetas · M3 "← Volver" textual · M4 emoji en títulos · M5 splash 5 s · M6 "Reintentar" vacío · M7 gráficas 8–10 sp · M8 Spacer 72 dp · M9 LaunchedEffect duplicado · M10 texto DEBUG. → `ui-ux-review-4-menor.md`

**Métrica global:** de ~25 pares de color auditados, ~9 fallan AA para texto normal en los flujos principales. El fix es mayoritariamente de tokens de color (bajo esfuerzo) y de una decisión de consistencia (top bar única).
