# Task Hub — Guía de marketing y visibilidad

> Última revisión: 18-ago-2026

Estrategia de lanzamiento y crecimiento para Task Hub, pensada para un producto **solo Android, gratis con anuncios, bilingüe ES/EN**, con **presupuesto mínimo**. El canal de adquisición más potente de esta app es estructural: **el código de invitación** convierte a cada usuario en embajador de su propio hogar.

---

## 1. Posicionamiento

**Una frase:** *"Reparte las tareas del hogar sin discusiones — y que gane quien más se esfuerza."*

**Público (en orden de prioridad):**
1. **Pisos compartidos** (compañeros de piso, estudiantes, Erasmus) — España, Madrid. Dolor: "nadie limpia".
2. **Parejas que conviven** — Dolor: "siempre hago yo todo".
3. **Familias** con hijos — Dolor: motivar a los niños (tienes el modo "perfil infantil").

**Propuesta de valor diferencial** (frente a competencia):
- **Gamificación real** con puntos, rachas y ranking → no es un simple *to-do* compartido.
- **Penalizaciones configurables** por retraso → resuelve la "justicia" del reparto.
- **Perfil infantil** simplificado → única en su categoría para familias.
- **Gratis** y sin servidor propio (Firestore) → bajo coste de mantenimiento.

### Competencia (conoce tu hueco)
| App | Fuerte | Tu ventaja |
|---|---|---|
| Tody | Diseño pulido, iOS+Android | Es de pago/limitada; tu gamificación (puntos/rachas/ranking) es más jugona |
| Sweepy | Bonito, gamificado | Menos orientado a "justicia entre miembros"; tu penalización configurables no existe ahí |
| OurHome / Nipto | Familia, puntos | Interfaces anticuadas; tu UI Material 3 + perfil infantil es superior |
| Listas de WhatsApp/Notas | Gratis, ubicuo | Sin puntos, sin ranking, sin recordatorios |

**No compitas en "app de tareas"** (mercado saturado). Compite en **"quitamos las discusiones del piso"** y **"hazlo un juego"**.

---

## 2. ASO — App Store Optimization (gratis, primero esto)

Antes de gastar un euro en ads, optimiza el listado. Es el tráfico de mayor calidad y coste cero.

- **Título** (30 chars, con keyword): `Task Hub – Tareas y puntos` o `Task Hub – Reparte las tareas`.
- **Descripción corta** (80 chars): `Reparte las tareas del hogar, gana puntos y acaba con las discusiones del piso.`
- **Descripción larga**: primeros 250 caracteres = lo más importante (se ven sin "leer más"). Habla del dolor ("¿harta de limpiar siempre tú?"), luego features, luego prueba social.
- **Keywords**: tareas, hogar, limpieza, pisos, compartir piso, pareja, familia, puntos, recompensas, rutinas, chores, flatmates, roommates, couple, family.
- **Capturas con texto superpuesto**: cada captura con un beneficio ("Racha de 7 días", "Ranking del piso", "Perfil infantil para los peques").
- **Localización**: ya tienes ES/EN en la app → publica el listado en ambos idiomas (ES + EN-US) sin coste. Amplía a LATAM (mismo español) si quieres volumen.
- **Icono**: reconocible, color de marca (Teal/Coral de tu tema), sin texto pequeño.

---

## 3. Estrategia de lanzamiento (fases)

### Fase A — Pre-lanzamiento (testing cerrado) · 2-4 semanas · coste 0 €
- **Objetivo**: 20+ testers reales (requisito Play) + pulir + primeras reviews de 5★.
- **Dónde**: amigos, familia, y **pisos reales** de tu entorno (compañeros de piso, grupos de Erasmus de Madrid). Cada piso = 3-5 testers de golpe.
- **Qué pedirles**: que usen el código de invitación de verdad (valida el flujo viral), que reporten fricciones.
- **A cambio**: agradecimiento, "beta tester" en los créditos, o simplemente el favor.

### Fase B — Lanzamiento blando (open testing) · 1-2 semanas
- Abre el *open testing* y publica en 2-3 comunidades de nicho (abajo). Mide: ¿instalan? ¿se quedan? ¿invitan a su piso?
- **KPI**: retención D1 > 30%, D7 > 15%. Si no llegas, **para y arregla** antes de gastar en ads (un producto con mala retención quema el presupuesto de ads sin retorno).

### Fase C — Producción + push de visibilidad
- Pasa a producción y ejecuta los canales de §4. Primero orgánico, luego ads si el retorno lo justifica.

---

## 4. Canales de adquisición (ordenados por ROI)

### 4.1 Loop viral integrado (coste 0 € — TU MAYOR PALANCA)
El código de invitación es un bucle de crecimiento: **1 usuario que crea un hogar = 2-10 instalaciones**. Optimízalo:
- Compartir código con un solo toque (ya lo tienes, y arreglaste el crash de compartir en 0.7.6).
- Mensaje de compartir atractivo: *"Únete a mi piso en Task Hub y repartamos las tareas"* con enlace directo a la Play Store.
- Si el enlace no lleva código embebido, añádelo más adelante (deep link) → baja la fricción de unirse.

### 4.2 Comunidades de nicho (coste 0 €, tiempo alto)
- **Reddit**: r/es, r/Madrid, r/Barcelona, r/askspain (hilo de "recomienda apps"), y en inglés r/roommates, r/relationships (hilos de "mi compañero no limpia"), r/productivity.
- **Grupos Facebook**: "Pisos en alquiler Madrid", grupos de Erasmus en España (Erasmus Madrid, ESN), grupos de madres/padres.
- **Foros**: Badi/Spotahome no, pero sí foros de estudiantes y de "adulting".
- **Regla de oro**: **no spamear**. Aporta valor (un comentario útil), menciona la app solo cuando resuelve el problema exacto. El mensaje: *"en mi piso usamos X para esto"*.

### 4.3 Contenido corto — TikTok / Instagram Reels (coste 0 €, tiempo medio)
Es el canal con mayor potencial viral para este producto:
- **Formato**: escenas de 15-30 s tipo "POV: tu compañero no limpia" / "repartimos las tareas y quien no cumple pierde puntos" / "así quedó el ranking semanal del piso".
- **Gancho emocional**: humor de convivencia + la app como "árbitro" neutral.
- **Consistencia > perfección**: 2-3 videos/semana. Uno que funcione vale más que 50 perfectos que no.
- Muestra capturas reales de la app (ranking, rachas) como prueba social.

### 4.4 ASO + reseñas (coste 0 €)
- Pide review **en el momento exacto** de satisfacción: justo tras completar una tarea o alcanzar una racha (no al abrir la app). *(Ojo: Google prohíbe intercambiar reviews por recompensas; pide, no compres.)*
- Responde **todas** las reviews, sobre todo las negativas (visible en la tienda).

### 4.5 Product Hunt (coste 0 €, audiencia tech/anglo)
- Un lanzamiento bien hecho da un pico de descargas + visibilidad en prensa tech. Requiere: perfil en inglés pulido, capturas, y "maker" activo respondiendo. Mejor en un martes. Es más para visibilidad/reputación que para volumen sostenido.

### 4.6 Publicidad de pago — Google Ads UAC (solo si §4.1-4.5 no dan suficiente tracción)
- **Cuándo**: solo después de validar retención (Fase B). Gasto mínimo realista: **10-20 €/día** de UAC (Universal App Campaign).
- **Qué esperar**: CPI en España ~0,5-1,5 €. Con 300 €/mes → 200-600 instalaciones/mes, pero **solo tiene sentido si la retención es buena** (si no, quemas dinero).
- **Alternativa barata**: Apple Search Ads no (es iOS). Para Android, UAC es casi la única vía de ads con sentido.

---

## 5. Métricas (mide o estás ciego)

Integra **Firebase Analytics** (ver guía de publicación) y sigue:

| Métrica | Objetivo lanzamiento | Cómo se mide |
|---|---|---|
| Instalaciones | 500-1000 el primer mes | Analytics |
| Retención D1 / D7 | >30% / >15% | Analytics (cohortes) |
| Hogares activos | >50 | Firestore (count de households con actividad) |
| Invitaciones enviadas / instal | >1,0 (loop viral) | Analytics + evento custom |
| Valoración media | ≥4,5★ | Play Console |
| Conversión listado (visitas→instala) | >30% | Play Console (adquisición) |

---

## 6. Presupuesto y priorización

| Acción | Coste | Prioridad |
|---|---|---|
| ASO (listado ES/EN + capturas + icono) | 0 € (tiempo) | 🔴 Máxima |
| Loop viral + compartir pulido | 0 € | 🔴 Máxima |
| Testing cerrado 20+ testers | 0 € | 🔴 Máxima |
| Firebase Analytics | 0 € | 🔴 Máxima |
| Comunidades de nicho (Reddit/Facebook) | 0 € (tiempo) | 🟡 Alta |
| TikTok/Reels | 0 € (tiempo) | 🟡 Alta |
| Product Hunt | 0 € | 🟢 Media |
| Google Ads UAC | 10-20 €/día | ⚪ Solo tras validar retención |

**Resumen:** puedes lanzar y crecer **con 0 € de presupuesto** usando ASO + loop viral + comunidades + contenido corto. El ads es opcional y solo cuando los datos lo justifiquen.

---

## 7. Plan de los próximos 14 días

1. **Semana 1**: integrar Analytics · cerrar AdMob (`app-ads.txt` + IDs reales) · política de privacidad · completar Play Console (listado, data safety, IARC).
2. **Semana 1-2**: reclutar 20+ testers (pisos reales) → *closed testing*.
3. **Semana 2**: pulir según feedback → *open testing*.
4. **Fin de semana 2**: publicar en 2-3 comunidades de nicho + primeros 3-4 TikTok/Reels.
5. **Cuando la retención sea sólida**: producción + decidir si meter ads.
