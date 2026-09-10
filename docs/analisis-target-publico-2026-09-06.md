# Task Hub — Análisis de target público (2026-09-06)

> Encargo de Liberto. Motivación literal del encargo: *"La app empezó con
> enfoque familiar pero ya NO está enfocada únicamente (ni fundamentalmente)
> en familias/niños: hay hogares con chat, calendario, recurrencia, ranking,
> espacios personales, recompensas… El encuadre de 'familias con niños' sigue
> vivo en docs y en la propia app (rol 'Niño/a', 'perfil infantil', README
> 'Pensado para parejas, pisos de estudiantes y familias', guia-marketing y
> competitors centradas en familias), mientras Play está deliberadamente
> clasificada como NO 'diseñada para familias/niños' (docs/guia-publicacion.md
> §2.3). El dueño quiere una revisión seria del target."*
>
> Informe de solo lectura. No modifica código, strings de UI/i18n ni ningún
> doc existente. Cita y enlaza los docs existentes en `docs/` en lugar de
> repetir su contenido.

---

## 1. Estado actual

### 1.1 Documentación de producto

- **README.md:1-7** — `"Task Hub convierte las tareas del hogar en un sistema
  gamificado (...). Pensado para parejas, pisos de estudiantes y familias."`
  Encuadre neutro-amplio (parejas, pisos, familias, en ese orden), sin
  mencionar niños ni "perfil infantil" explícitamente.
  **Nota aparte (ver §5, hallazgo H1):** el resto del README (líneas 3, 9-21,
  43-93) describe un stack **obsoleto** (Ktor Server + PostgreSQL + Flyway +
  compose-resources), que no es el stack real actual descrito en
  `CLAUDE.md` (Firestore vía REST, sin servidor propio, multiplatform-settings).
  El README no se ha tocado desde la Fase 0 del proyecto.

- **docs/specs.md:12** — `"Está diseñado para núcleos de convivencia —
  parejas, pisos de estudiantes, familias — donde las tareas del hogar
  necesitan repartirse..."` (encuadre amplio, igual que README).
  - **specs.md:29-31** — Público objetivo explícito, en orden:
    1. Parejas, 2. Compañeros de piso, 3. *"Familias con hijos donde las
    tareas pueden ser también un juego educativo"*, 4. "Cualquier grupo de
    convivencia de 2 a 10 personas".
  - **specs.md:52-56 (HU-04b, HU-04c)** — Historias de usuario explícitas
    de **"perfil infantil"**: el admin crea perfiles para que "los niños del
    hogar" solo vean sus tareas asignadas, con una "vista simplificada
    infantil" (solo lista + botón «✅ Hecho», sin puntuaciones, ranking,
    creación ni configuración).
  - **specs.md:146, 472, 491-492, 526, 528** — El rol de datos se llama
    literalmente `'child'` en el modelo (`role TEXT ... DEFAULT 'child'`) y
    en el glosario: *"Child — Perfil infantil. Solo ve sus tareas asignadas
    (...)"*.
  - **specs.md:533, 553** — Etiqueta predefinida de tareas "niños" como tag
    de categoría (limpieza, cocina, compras, mascotas, mantenimiento,
    **niños**, exterior, administración, otro).

- **docs/guia-marketing.md** (última revisión 18-ago-2026):
  - **líneas 13-16** — Público en orden de prioridad: 1) pisos compartidos,
    2) parejas, 3) *"Familias con hijos — Dolor: motivar a los niños (tienes
    el modo 'perfil infantil')"*.
  - **línea 21** — *"Perfil infantil simplificado → única en su categoría
    para familias"* listado como ventaja diferencial de producto.
  - **línea 29** — Frente a OurHome/Nipto: *"tu UI Material 3 + perfil
    infantil es superior"*.
  - **línea 44** — Copy de captura de pantalla sugerido: *"Perfil infantil
    para los peques"*.
  - **línea 77** — Canales de difusión sugeridos incluyen *"grupos de
    madres/padres"*.

- **docs/competitors.md**:
  - **líneas 8-10** — Tres nichos de competencia: piso compartido, pareja,
    **"Familia con niños — tareas + puntos + recompensas + perfil
    infantil"**.
  - **líneas 16-35, 65-73** — Media docena de competidores analizados
    (S'moresUp, Sweepy, Homey, BusyKid, Cozi, OurHome) son explícitamente
    apps *family/parental-control/allowance*; el "perfil infantil" de Task
    Hub se compara punto por punto con su "leaderboard + aprobación
    parental" (línea 35) y se cita como ventaja (línea 83: *"Sweepy —
    ranking + perfil infantil"*).

- **docs/play-store-listing.md** (última revisión 18-ago-2026, listado real
  copy-paste para Play Console):
  - **línea 33 (ES)** — `"👨‍👩‍👧 Perfil infantil — un modo simplificado para
    que los más pequeños participen sin complicaciones, gestionado por un
    adulto."` — aparece como una de las 6 features destacadas de la
    descripción larga, con emoji de familia.
  - **línea 55 (EN)** — Traducción: `"👨‍👩‍👧 Kids' profile — a simplified mode
    so the little ones can join in, managed by an adult."`
  - **línea 67** — Keywords incluyen `family`, `couple`, `flatmates`,
    `roommates` (mezcla géneros de audiencia).
  - **línea 75** — Copy de captura de pantalla: *"Perfil infantil → 'Modo
    para los peques'"*.
  - **línea 87** — Tabla de configuración de Play Console:
    `"Diseñada para familias / niños | ❌ NO marcar (ver guía de publicación
    §2.3)"`.

- **docs/guia-publicacion.md §2.3 (líneas 56-68)** — Es la pieza que fuerza
  la contradicción a la vista: exige declarar **"Todas las edades /
  Everyone"** en IARC y advierte explícitamente:
  > *"⚠️ Aviso importante — perfiles infantiles. La app tiene 'perfil
  > hijo/a'. NO marques la app como 'Diseñada para familias' ni 'dirigida a
  > niños' en Play Console (...). El 'perfil infantil' es una función
  > *dentro* de la app gestionada por el adulto, no que la app esté dirigida
  > a niños."*
  Esto es correcto desde el punto de vista de cumplimiento (evita disparar
  la Families Policy de Play, con sus requisitos de privacidad infantil
  reforzados), pero dice implícitamente lo que este informe pone negro sobre
  blanco: el propio dueño del producto ya sabe que la narrativa "familia con
  niños" del marketing no encaja con cómo se declara la app ante Google.

### 1.2 i18n / strings de UI reales

- **`ui/i18n/AppStrings.kt`** — Búsqueda de "niño"/"infantil"/"familia"/
  "child"/"kid" da **cero resultados de cara al usuario**. Las únicas
  claves relacionadas son `member_role_child_full` / `member_role_child_short`
  (líneas 274-277 ES, 881-884 EN), y su valor visible es:
  - ES: `"Miembro"` / `"👤 Miembro"`
  - EN: `"Member"` / `"👤 Member"`
  No existe ningún string visible en la app que diga "Niño/a", "Kids",
  "infantil" o similar. El propio glosario de rol usa "Admin" vs "Miembro"
  (permisos), no "Adulto" vs "Niño".
- **`network/models/DTOs.kt:49-53`** documenta esto explícitamente en KDoc:
  > *"'admin' | 'child'. Valor interno en código/Firestore — NO se renombra
  > (evita migración de datos). El nombre de display para 'child' en la UI
  > es 'Miembro' (...), no 'Niño/a'."*
- **Hallazgo clave de historial (git log):** el commit `b529100` — *"feat:
  roles seguros (Cloud) + renombrar 'Niño/a' → 'Miembro'"* (31-ago-2026) —
  hizo justo ese cambio: antes la UI mostraba literalmente `"Niño/a"` /
  `"🧒 Niño/a"` (ES) y `"Child"` / `"🧒 Child"` (EN); el commit lo sustituyó
  por `"Miembro"` / `"Member"` con emoji neutro `👤`, dejando el valor
  interno `child` intacto "para evitar migración de datos". Es decir: **el
  código ya pivotó de facto hacia un naming neutro de convivencia antes que
  este informe**, pero la documentación de marketing/listing (con fecha
  18-ago-2026, *anterior* al commit) nunca se actualizó para reflejarlo.

### 1.3 Features reales en el código

- **Rol `child` real (`network/MemberRepository.kt`, `ui/screens/
  CreateProfileScreen.kt:157-171`, `ui/screens/JoinHouseholdScreen.kt:221-225`)**
  — Es únicamente un **nivel de permisos** (no puede crear/editar/borrar
  tareas ni gestionar el hogar), asignado a mano por el admin o por defecto
  al unirse por invitación. **No existe** en el código ninguna "vista
  simplificada infantil" (la HU-04c de specs.md no está implementada): un
  miembro con rol `child` sigue viendo ranking, puntos, recompensas y el
  resto de la app normal; solo tiene botones de acción ocultos/deshabilitados
  (ver comprobaciones `role == "admin"` repartidas en `TaskDetailScreen.kt`,
  `HouseholdScreen.kt`, `RewardListScreen.kt`, `RankingScreen.kt`, etc.).
  Esto es una brecha spec-vs-implementación real, no solo de naming (ver §5).
- **Señal de anuncios dirigida a menores (`platform/AdController.kt:20-36`,
  `ui/models/TaskScreenModel.kt:264,791`)** — Aquí sí hay tratamiento real y
  cuidadoso de "menor": `updateChildDirectedSignal(myRole == "child")` ajusta
  por sesión la señal TFCD de AdMob (nunca a FALSE, solo TRUE/UNSPECIFIED,
  con comentario extenso explicando por qué). Es la única pieza del código
  que trata el rol `child` como *potencialmente un menor real* a efectos de
  protección de privacidad publicitaria — coherente con el enfoque de
  guia-publicacion.md (función gestionada por el adulto, no un target
  declarado).
- **`ui/screens/PersonalSpaceScreen.kt:24-33`** — "Espacio Personal": hogar
  de un único miembro ("Yo"), sin invitaciones, código QR, ranking ni
  recompensas — solo tareas propias + calendario. Es una feature que apunta
  a **uso individual de un adulto** (gestión personal de tareas propias),
  no a convivencia familiar ni mucho menos a un "usuario niño".
- **`ui/components/HouseholdChatSection.kt`** — Chat de mensajes por hogar
  (lista + envío), más "agradecer/donar puntos" entre miembros (ver
  `docs/encargos/agradecer-donar-puntos.md`, `docs/encargos/
  mensajeria-espacios.md`, commit `6949ae7`). Es una feature de
  **comunicación entre convivientes adultos** (agradecimientos, mensajes),
  sin ningún control parental ni moderación pensada para menores.
- **`network/GoogleCalendarRepository.kt`, `ui/models/
  CalendarSyncManager.kt`, `ui/screens/CalendarScreen.kt`** — Sincronización
  bidireccional con Google Calendar de cada miembro. Requiere que el usuario
  tenga su propia cuenta de Google Calendar — algo que un "niño" con perfil
  infantil gestionado por un adulto, por diseño, no tiene ni necesita.
- **`network/RecurrenceRules.kt` (recurrencia diario/semanal/mensual +
  rotación)**, **`network/PenaltyRules.kt`** (penalizaciones configurables
  por retraso) y **`ui/screens/RankingScreen.kt`** (ranking del hogar) son
  mecánicas de reparto justo y transparencia entre convivientes con
  capacidad de decisión — de nuevo, pensadas para adultos (o adolescentes
  autónomos), no para un perfil infantil supervisado.

**Conclusión del estado actual:** el conjunto de features reales (espacio
personal, chat, calendario, recurrencia + rotación, penalizaciones, ranking,
donación de puntos) describe una app de **gestión de convivencia entre
adultos** (parejas, pisos, grupos), con una única función explícitamente
pensada como accesoria para el contexto familiar: un rol de permisos
reducidos (`child`) sin vista ni UX diferenciada, ya renombrado en la UI a
un genérico "Miembro". La documentación de marketing/producto, sin embargo,
sigue construida en gran parte alrededor del encuadre "familia con niños"
como diferenciador competitivo principal.

---

## 2. Contradicciones y tensiones

1. **Marketing/UI vs clasificación de Play.** `docs/play-store-listing.md`
   dedica una de sus 6 balas de la descripción larga (línea 33/55) a "Perfil
   infantil"/"Kids' profile" con emoji de familia (👨‍👩‍👧), usa la keyword
   `family` (línea 67) y una captura con copy "Modo para los peques" (línea
   75) — pero la misma tabla de configuración (línea 87) exige marcar
   explícitamente que la app **NO** está "diseñada para familias/niños", y
   `guia-publicacion.md §2.3` lo remarca con un aviso en mayúsculas. Un
   revisor de Play Console (o un usuario que lea el listing) puede
   razonablemente interpretar "Kids' profile" + emoji familiar como una
   señal de app dirigida a niños, exactamente lo que se está intentando
   evitar declarar. Esto no es solo un riesgo de percepción: Google revisa
   activamente el *contenido del listado*, no solo el checkbox, para decidir
   si aplica la Families Policy.

2. **Naming interno ('Niño/a'/'child') vs público real actual.** El propio
   equipo (commit `b529100`) ya concluyó que "Niño/a" no era el naming
   correcto de cara al usuario y lo cambió a "Miembro" en la UI. Pero:
   - `specs.md` (HU-04b/c, glosario) sigue describiendo la feature como
     "perfil infantil" / "Child" sin reflejar el rename.
   - `guia-marketing.md` y `competitors.md` siguen vendiendo "perfil
     infantil" como ventaja competitiva central frente a apps *family*
     (S'moresUp, Sweepy, OurHome).
   - El valor interno de datos sigue siendo literalmente `"child"` (por
     decisión explícita de evitar migración), lo cual perpetúa el concepto
     en el código aunque ya no sea visible en pantalla.
   Es decir: hay tres capas del producto (datos, UI, marketing) desalineadas
   entre sí sobre si esto es una feature "infantil" o una feature de
   "permisos reducidos".

3. **A quién sirve cada feature por diseño.** Separando el catálogo de
   features reales por su público de diseño:
   - **Apuntan a convivencia adulta / grupo autónomo:** espacio personal
     (uso individual), chat (comunicación entre iguales), calendario
     (requiere cuenta Google propia), recurrencia + rotación + penalización
     por retraso (asume responsabilidad y consecuencias autogestionadas),
     ranking (competición entre pares), donar/agradecer puntos (interacción
     social entre adultos).
   - **Apunta (nominalmente) a familias con niños:** un único rol de
     permisos reducidos, sin vista propia, sin protecciones de contenido
     adicionales más allá del banner de anuncios, gestionado enteramente por
     un adulto que crea el perfil y le pone contraseña/dispositivo.
   El ratio es muy desigual: 6-7 features de convivencia adulta activas y
   centrales en la experiencia, frente a 1 función accesoria de permisos que
   ni siquiera tiene una interfaz diferenciada — y aun así, esa única
   función ocupa un lugar desproporcionado en marketing/competitors (una de
   las 4 ventajas competitivas citadas, y el eje de comparación con media
   docena de competidores "de familia").

4. **La propia guia-publicacion.md ya lo señala sin decirlo del todo.** El
   aviso de §2.3 justifica no marcar "familias" diciendo que el perfil
   infantil es *"una función dentro de la app gestionada por el adulto, no
   que la app esté dirigida a niños"* — esa misma frase es, de hecho, el
   argumento correcto para *dejar de vender la app* como si estuviera
   dirigida a niños. El equipo ya tiene la conclusión correcta a nivel de
   compliance, pero el marketing no la ha heredado todavía.

---

## 3. Recomendaciones

### Opción A — Reposicionar explícitamente como "gestor de tareas para núcleos de convivencia"

Encuadre: *"gestor de tareas para núcleos de convivencia: parejas, pisos,
familias y grupos"*, con el perfil infantil relegado a función accesoria
gestionada por adultos (ya no vendida como diferenciador de "familia").

- **Producto:** ninguna feature nueva estrictamente necesaria. Se podría
  (opcional, no ahora) invertir el orden de prioridad de HU-04b/c en
  specs.md o directamente archivarlas si no hay intención de construir la
  "vista simplificada infantil" (que hoy no existe).
- **UI:** ya está prácticamente hecho (el rename `b529100` a "Miembro" es
  exactamente este movimiento). Quedaría revisar que no queden más rastros
  de framing infantil en pantallas (no encontré ninguno visible salvo el
  posible emoji histórico, ya corregido).
- **Marketing:** reescribir guia-marketing.md, competitors.md y
  play-store-listing.md quitando "perfil infantil"/"Kids' profile" como
  bala destacada y "familia con niños" como nicho propio; fusionarlo con
  "grupos de convivencia" (pisos + familias + grupos), manteniendo como
  mucho una mención lateral de "control de permisos por miembro" (útil
  también para invitados temporales, compañeros nuevos, etc., no solo
  niños).
- **Play Console:** sin cambios respecto al estado ya decidido en
  guia-publicacion.md §2.3 (Todas las edades, sin marcar familias) — de
  hecho esta opción *reduce* el riesgo de disonancia entre listado y
  declaración, porque deja de mencionar niños en el copy visible.
- **Trade-off:** se pierde el ángulo "único en su categoría para familias"
  que hoy se usa para diferenciarse de Tody/Sweepy/OurHome en
  competitors.md; hay que sustituirlo por otro eje diferencial (gamificación
  + penalizaciones configurables + espacio personal + calendario, que ya
  están documentados como ventajas reales).

### Opción B — Mantener el perfil infantil pero renombrarlo/depriorizarlo

Conservar la función (rol de permisos reducidos) y su mención en marketing,
pero bajarla de "diferenciador principal" a "detalle secundario", y
formalizar el naming neutro ya adoptado en código ("Miembro" / control de
permisos) también en toda la documentación y en el copy de Play.

- **Producto:** sin cambios de código.
- **UI:** sin cambios (ya coherente desde `b529100`).
- **Marketing:** en play-store-listing.md, bajar "Perfil infantil" del
  bloque de 6 features destacadas a una línea secundaria sin emoji familiar,
  sin la keyword `family` genérica suelta, y sin captura de pantalla
  dedicada ("Modo para los peques"). En guia-marketing.md, quitar "familias
  con hijos" como nicho de prioridad 3 o mantenerlo pero sin citar el
  perfil infantil como ventaja núcleo.
- **Play Console:** mismo estado que hoy (Todas las edades, sin family
  flag), pero con menor riesgo de disonancia entre lo que se lee en el
  listado y lo que se declara.
- **Trade-off:** conserva parte del posicionamiento actual y el trabajo ya
  invertido en competitors.md, a costa de mantener una tensión residual
  (aunque menor) entre "vendo perfil infantil" y "no soy app de familias".

### Opción C — Statu quo (no tocar nada)

- **Trade-off:** el riesgo real es de cara a Google Play: un listado que
  menciona "Kids' profile" con imaginería familiar es exactamente el tipo
  de señal que el propio equipo, en guia-publicacion.md, identifica como
  disparador de revisión bajo la Families Policy — aunque el checkbox esté
  sin marcar. Además, la documentación interna (specs.md HU-04c) sigue
  describiendo una feature que no existe, lo cual puede llevar a
  malentendidos en futuras iteraciones ("¿ya tenemos vista infantil?" No).

### Recomendación

**Recomiendo la Opción A.** El código ya ha dado el primer paso (el rename
`b529100` de "Niño/a" a "Miembro" fue una decisión de producto acertada, no
solo un lavado de nombre) y las features que realmente definen la
experiencia de uso — espacio personal, chat, calendario, recurrencia,
ranking, penalizaciones, donación de puntos — son, por diseño, mecanismos
de convivencia entre adultos con autonomía y responsabilidad propia, no
mecanismos pensados para menores supervisados. Mantener "familia con niños"
como uno de los tres pilares del posicionamiento (guia-marketing.md líneas
13-16) cuando en la práctica es una feature de permisos sin interfaz propia
genera dos costes concretos: (1) riesgo real frente a Play (una feature
vendida con lenguaje e imaginería familiar en el listado, en una app
declarada explícitamente como no dirigida a familias/niños, es el tipo de
inconsistencia que puede acabar en una revisión de Data Safety/Families
Policy no deseada); y (2) dilución del mensaje de marketing, que compite
contra apps de "familia" fuertemente capitalizadas (S'moresUp, ~130k
familias según competitors.md línea 18) en un terreno donde Task Hub no
tiene ninguna ventaja real (no hay aprobación parental, ni vista infantil,
ni controles de contenido), en vez de competir en el terreno donde sí tiene
ventajas documentadas: gamificación real, penalizaciones configurables y
justicia de reparto entre iguales (pisos, parejas, grupos). La Opción B es
una alternativa razonable si Liberto cree que el perfil infantil sigue
aportando conversión real y no quiere reescribir competitors.md a fondo,
pero en ese caso recomendaría al menos ejecutar la parte de Play Console
(bajar prioridad del copy) cuanto antes, por ser la de mayor riesgo/menor
esfuerzo. La Opción C (no tocar nada) no la recomiendo: dado que el propio
dueño ha identificado la tensión y ha pedido este análisis, dejarla sin
resolver solo pospone un riesgo de compliance ya diagnosticado en
guia-publicacion.md.

---

## 4. Cambios concretos propuestos (PROPUESTA — no aplicada por este informe)

> Todo lo que sigue es texto **sugerido** para si Liberto decide ejecutar la
> Opción A (o una versión mixta). Este informe **no modifica** README.md,
> ningún doc de `docs/` ni ningún string de `AppStrings.kt`. Aplicar estos
> cambios requeriría PRs separados y explícitos.

### README.md (línea 7)

Actual:
> "Task Hub convierte las tareas del hogar en un sistema gamificado donde
> los miembros ganan puntos, mantienen rachas y desbloquean logros. Pensado
> para parejas, pisos de estudiantes y familias."

Propuesta:
> "Task Hub convierte las tareas del hogar en un sistema gamificado donde
> los miembros ganan puntos, mantienen rachas y desbloquean logros. Pensado
> para cualquier núcleo de convivencia: parejas, pisos de estudiantes,
> familias o grupos — con control de permisos por miembro para adaptarlo a
> cada casa."

*(Nota aparte, no relacionada con el target pero detectada durante este
análisis: el resto del README describe un stack obsoleto — ver hallazgo H1
en §5 — que convendría actualizar en un PR distinto, específico de
documentación técnica.)*

### docs/play-store-listing.md

- **Línea 33 (ES)** — sustituir:
  > `👨‍👩‍👧 **Perfil infantil** — un modo simplificado para que los más
  > pequeños participen sin complicaciones, gestionado por un adulto.`

  por (bajado de prioridad, sin emoji familiar, encuadrado como control de
  permisos general):
  > `🔐 **Permisos por miembro** — limita quién puede crear, editar o
  > configurar el hogar; útil para peques, invitados o cualquiera que solo
  > necesite marcar tareas como hechas.`

- **Línea 55 (EN)**, equivalente:
  > `🔐 **Per-member permissions** — control who can create, edit or manage
  > the household; handy for kids, guests, or anyone who should just tick
  > off tasks.`

- **Línea 67** — quitar `family` de la lista de keywords si se adopta la
  Opción A (mantenerla si se opta por B), y valorar añadir `roommates`,
  `shared living`, `cohabitants`.

- **Línea 75** — sustituir el copy de captura *"Perfil infantil → 'Modo
  para los peques'"* por algo como *"Permisos por miembro → 'Tú decides
  quién puede qué'"*, o eliminar esa captura del set si no aporta suficiente
  diferenciación.

- **Línea 87** — sin cambios (la fila ya está correcta: seguir sin marcar
  "diseñada para familias/niños"). Si se ejecuta la Opción A, esta fila deja
  de tener tensión con el resto del documento.

### docs/guia-marketing.md

- **Líneas 13-16** — reordenar/fusionar el público en dos bloques en vez de
  tres: (1) pisos compartidos y parejas — dolor "reparto injusto/discusiones";
  (2) "cualquier grupo de convivencia (incluidas familias)" — dolor
  "coordinar y motivar a todo el mundo, con permisos ajustables por
  persona". Quitar la frase *"tienes el modo 'perfil infantil'"* como razón
  de compra para el segmento familia.
- **Línea 21** — quitar *"Perfil infantil simplificado → única en su
  categoría para familias"* del listado de propuestas de valor
  diferenciales, o sustituirlo por *"Permisos por miembro"* si se quiere
  conservar la idea sin el framing infantil.
- **Línea 29** — en la tabla de competencia frente a OurHome/Nipto, cambiar
  *"tu UI Material 3 + perfil infantil es superior"* por *"tu UI Material 3
  + gamificación real (rachas/ranking/penalizaciones) es superior"*.
- **Línea 44** — quitar *"Perfil infantil para los peques"* del set de
  capturas sugeridas o sustituirlo por una feature con más peso real en la
  experiencia (p.ej. "Sincroniza con tu Google Calendar" o "Espacio
  Personal, para ti solo/a").

### docs/competitors.md

- **Línea 10** — el nicho "Familia con niños" se podría renombrar a
  "Familias" a secas (sin "+ perfil infantil" como rasgo definitorio),
  reconociendo que Task Hub no compite de verdad en aprobación
  parental/allowance con S'moresUp, Sweepy o BusyKid.
- **Líneas 35, 65-73, 83** — las comparaciones que usan el "perfil
  infantil" de Task Hub como equivalente a la "aprobación parental" de
  Sweepy o el modelo allowance de S'moresUp/BusyKid conviene revisarlas: no
  son features equivalentes (una es un check de permisos, las otras
  implican aprobación bidireccional/pagos reales). Suavizar la comparación
  o quitarla evita una promesa implícita que el producto no cumple.

### docs/specs.md

- **HU-04b/HU-04c (líneas 52-56)** — decidir entre: (a) marcarlas como
  implementadas parcialmente y corregir su descripción para que refleje la
  realidad actual (rol de permisos, sin vista simplificada, display "Miembro"
  no "perfil infantil"); o (b) si se adopta la Opción A y no hay intención de
  construir la vista simplificada, archivarlas explícitamente como "no
  implementado / descartado" en vez de dejarlas como si describieran el
  comportamiento actual.
- **Línea 31** — *"Familias con hijos donde las tareas pueden ser también un
  juego educativo"* podría fusionarse con el resto de público objetivo bajo
  un único punto de "grupos de convivencia", coherente con la Opción A.

### AppStrings.kt

No se identifican strings visibles que requieran cambio (la migración
"Niño/a" → "Miembro" ya está hecha, commit `b529100`). Si se quiere reforzar
el encuadre de "permisos" en vez de "rol", se podría considerar (solo si se
ejecuta la Opción A) renombrar la etiqueta de la pantalla de creación de
perfil `create_profile_role_label` de "Rol" a algo como "Permisos", pero es
un cambio menor y no urgente.

---

## 5. Lista de hallazgos (bugs, inconsistencias, deuda técnica)

Detectados durante la lectura de código/docs para este análisis. No se ha
corregido nada; se listan aquí para que el dueño decida priorizarlos.

- **H1 — README.md desactualizado respecto al stack real. [YA CORREGIDO
  en esta misma pasada de documentación, ver `README.md` actual.]**
  `README.md:3,9-21,43-93` (versión previa) describía un backend Ktor Server
  + PostgreSQL + Flyway y un estado de "Fase 0 — Setup" (línea 111), que no
  correspondía al stack real documentado en `CLAUDE.md` (Firestore vía REST,
  sin servidor propio, Koin, Voyager, multiplatform-settings, features
  avanzadas como chat/calendario/recurrencia/ranking ya implementadas). Se
  reescribió como parte del Entregable 1 de este mismo encargo; se deja aquí
  constancia del hallazgo original por trazabilidad.

- **H2 — HU-04c de `docs/specs.md:55-56` describe una feature no
  implementada.** La "vista simplificada infantil" (solo tareas asignadas +
  botón «Hecho», sin ranking/puntos/configuración) no existe en el código:
  un miembro con rol `child` ve la misma UI que un `admin`, solo con botones
  de acción restringidos vía checks `role == "admin"` dispersos por
  `TaskDetailScreen.kt`, `HouseholdScreen.kt`, `RewardListScreen.kt`,
  `RankingScreen.kt`, `CreateTaskScreen.kt`, `EditTaskScreen.kt`. AC-25 de
  specs.md (línea 492) tampoco se cumple tal como está escrito.

- **H3 — Naming interno (`role = "child"`) desincronizado del naming de UI
  ("Miembro") desde el commit `b529100`, documentado solo en un KDoc
  (`DTOs.kt:49-53`).** Riesgo bajo pero real de confusión futura para quien
  toque este código sin leer el comentario (p. ej. al añadir un tercer rol,
  o al escribir nuevas queries/reglas de Firestore que asuman semántica de
  "niño" en vez de "permisos reducidos").

- **H4 — Documentación de marketing con fecha anterior al rename de
  producto.** `docs/guia-marketing.md` y `docs/play-store-listing.md` están
  fechados 18-ago-2026; el commit que renombró "Niño/a" → "Miembro" en la UI
  es del 31-ago-2026 (`b529100`). Ningún doc de marketing se actualizó tras
  ese cambio de producto — es la causa raíz mecánica de la contradicción que
  motiva este informe.

- **H5 — `docs/play-store-listing.md:33,55` usa imaginería/keyword de
  familia ("👨‍👩‍👧", "family") en el mismo documento que, en su línea 87,
  exige no declarar la app como dirigida a familias.** Riesgo de
  compliance/revisión de Play si Google interpreta el copy del listado
  (independientemente del checkbox) como señal de contenido dirigido a
  menores. Ver detalle en §2.1 de este informe.

- **H6 — Tag predefinida "niños" en `docs/specs.md:533,553`** (categoría de
  tareas tipo "cuidado de niños") no se ha verificado contra el código de
  tags real (`network/models/DTOs.kt` u otro); si ya no coincide con las
  tags implementadas, es una inconsistencia doc-código adicional a revisar
  (no confirmado en este análisis, fuera de alcance verificarlo a fondo, se
  deja apuntado).

- **H7 — Comparaciones competitivas potencialmente engañosas en
  `docs/competitors.md:35,83`.** Equiparar el "perfil infantil" de Task Hub
  (un simple check de permisos, sin vista propia) con la "aprobación
  parental" de Sweepy o el modelo allowance de S'moresUp/BusyKid es una
  comparación de features no equivalentes; útil solo como nota interna, pero
  si se usa tal cual en comunicación externa podría interpretarse como
  promesa de una funcionalidad que no existe.

- **H8 — `CLAUDE.md` tenía `targetSdk 35` desactualizado. [YA CORREGIDO
  en esta misma pasada.]** `composeApp/build.gradle.kts` ya usa
  `compileSdk = 36` / `targetSdk = 36` desde el commit `4b5d9ef` ("subir
  targetSdk a 36"), pero esa subida no se había propagado a la memoria corta
  del proyecto. Corregido al reescribir `CLAUDE.md` en este mismo encargo.

- **H9 — `firestore.rules` no puede verificar que `points`/`pointsSpent`
  escritos en `taskHistory`/`rewardRedemptions` sean el valor correcto
  calculado para una tarea/recompensa concreta** (solo valida que no sean
  negativos ni superen el coste real). Es una limitación estructural de no
  tener backend/Cloud Functions: cualquier cliente autenticado como miembro
  del hogar podría, en teoría, escribir un `pointsSpent` menor al real. Ya
  señalado en el propio repo (ver comentarios de `firestore.rules` y
  `docs/atomicidad-commit-pendiente.md`); se deja constancia aquí porque es
  relevante también para la conversación de a quién va dirigida la app
  (confianza entre convivientes, no anonimato entre desconocidos).

- **H10 — `storage/SecureStore.ios.kt` nunca se ha compilado/ejecutado en un
  entorno con Xcode/macOS** (según su propio comentario interno). Antes de
  cualquier release a iOS habría que verificarlo en un build real; hoy es
  código no probado.

- **H11 — El widget de pantalla de inicio (`TaskHubWidgetProvider.kt`) no
  propaga `householdId`/`taskId` al abrir la app**, a diferencia de
  notificaciones y recordatorios: tocar el widget siempre lleva a la
  `HomeScreen` genérica aunque el usuario esperara ir directo a la tarea
  mostrada en el widget.

- **H12 — `NotificationRepository.getNotifications` no pagina**, a
  diferencia de otras colecciones que también crecen sin límite. Mitigado
  parcialmente por `purgeOldRead` (purga notificaciones leídas de más de 90
  días), pero un hogar muy activo entre purgas podría devolver una lista
  grande en cada sondeo de `NotificationPollWorker` (cada ~30 min).

- **H13 — `firestore.rules` valida `pointsSpent == cost` al canjear una
  recompensa, pero no se ha verificado si existe (o debería existir) un
  tope de canjes repetidos de la misma recompensa** a nivel de regla; no
  confirmado como bug, señalado como área a revisar por quien conozca el
  modelo `RewardResponse` en detalle.

---

*Fin del informe. Archivo de solo lectura: no se ha modificado ningún otro
archivo del repositorio como parte de este análisis (los hallazgos H8-H13 se
recopilaron de los informes de los agentes que documentaron el código en
paralelo, como parte del mismo encargo, y se añadieron aquí por ser el lugar
designado para consolidar hallazgos).*
