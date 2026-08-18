# Task Hub — Guía de publicación

> Última revisión: 18-ago-2026 · Estado verificado: Android **0.7.13** (versionCode 120)

Guía paso a paso para llevar Task Hub de *internal testing* a **producción pública** en Google Play, con lo que ya tienes y lo que falta. Asume el estado real del repo, no la spec antigua (`docs/specs.md` aún describe un backend Ktor que ya no existe: la app usa Firestore REST directo).

---

## 0. Estado real (verificado)

| Pieza | Estado |
|---|---|
| Android (CMP) | ✅ Compila y publica. `org.taskhub`, minSdk 26, targetSdk 35 |
| Pipeline release | ✅ `.github/workflows/release.yml` → AAB firmado → track `internal`/`alpha`/`production` |
| Firestore rules | ✅ `firestore.rules` v2 (acceso por miembro/propietario, auth obligatoria) |
| Auth | ✅ Anónima + Google Sign-In |
| AdMob | ⚠️ Integrado, con **IDs de test** (`ca-app-pub-3940256099942544…`). Correcto para closed testing; **cambiar a IDs reales antes de producción** |
| Crashlytics | ✅ Integrado |
| Analytics | ✅ **Integrado** (Firebase Analytics). Eventos custom: `household_created`, `household_joined`, `invite_code_shared`, `task_completed` |
| iOS | ⚠️ Targets en código, **no publicable** (necesitas macOS + cuenta Apple) |
| Desktop | ⚠️ Compila (JVM), distribución secundaria opcional |
| Política de privacidad | ❌ No existe. **Requisito obligatorio** de Play Console |
| Listado Play (textos, icono, capturas) | ❓ A completar en Play Console |

**Conclusión:** el motor de publicación ya funciona. Lo que falta no es técnico, es **el papeleo de Play Console** (listado + privacidad + clasificación) y **cerrar los flecos de producción** (IDs AdMob reales, Analytics).

---

## 1. Decisión estratégica: Android primero

- **Android** → publicable **ya**. Es tu mercado principal (España, cultura de piso compartido).
- **iOS** → requiere macOS (no tienes iMac) + cuenta Apple Developer (**99 $/año**). Opciones si lo quieres a medio plazo:
  - GitHub Actions con *runner* macOS (≈ 0,08 $/min, un build ~5-10 $).
  - Mac mini de segunda mano (~400-600 €).
  - **Decisión recomendada:** lanzar Android, validar tracción, y solo entonces invertir en iOS.
- **Desktop** → opcional. Genera `.msi`/`.deb`/`.dmg` y distribuye por GitHub Releases si algún usuario lo pide. No inviertas en esto ahora.

---

## 2. Play Console — checklist de publicación a producción

### 2.1 Cuenta y acceso (ya lo tienes)
Ya publicas a `internal`, así que asumo: cuenta de desarrollador (25 $ una vez), *service account* con permisos de publicación, y keystore de subida guardados como secrets en GitHub. ✅

### 2.2 Contenido del listado (lo que se ve en la tienda)
Obligatorio y de mayor impacto en conversión:

- **Título** (máx 30 chars): `Task Hub – Tareas y puntos` *(propuesta; ver guía de marketing)*
- **Descripción corta** (máx 80 chars) y **descripción larga** (máx 4000).
- **Icono** 512×512 PNG (32-bit).
- **Feature graphic** 1024×500.
- **Capturas** (mín 2, ideal 8): teléfono en vertical, 1080 px min. Añade capturas de tablet solo si el diseño tablet es decente.
- **Categoría**: *Productividad* (o *Estilo de vida*).
- **Video promocional** (opcional, enlace YouTube).

### 2.3 Privacidad y Data Safety (obligatorio, sin esto no sale a producción)
1. **Política de privacidad pública** (URL). No la tienes → créala y publícala. Opciones:
   - **Firebase Hosting** (ya tienes el proyecto Firebase) — gratis, un HTML.
   - GitHub Pages (el repo es público) — gratis.
   - Contenido mínimo: qué datos se recogen (email de Google, tareas del hogar, ID de publicidad), para qué, y cómo borrarlos.
2. **Formulario Data Safety** en Play Console. Declara:
   - *Email* (Google Sign-In) — no compartido.
   - *Identificador de dispositivo* (AdMob / publicidad).
   - *Datos de actividad en la app* (tareas, puntos).
   - *Logs de crash* (Crashlytics).
3. **Clasificación de contenido (IARC)** → responde el cuestionario (2 min). Resultado esperado: **Todas las edades / Everyone**.

> ⚠️ **Aviso importante — perfiles infantiles.** La app tiene "perfil hijo/a". NO marques la app como *"Diseñada para familias"* ni *"dirigida a niños"* en Play Console. Marca *"Todas las edades (Everyone)"* y deja la casilla de "para menores de 13" sin marcar. Marcar "familias" dispara la **Families Policy** (requisitos extra de privacidad infantil y revisión adicional) que no necesitas para el lanzamiento. El "perfil infantil" es una función *dentro* de la app gestionada por el adulto, no que la app esté dirigida a niños.

### 2.4 Precios y distribución
- Precio: **Gratis** (monetizas con AdMob).
- Países: España primero (o ES + LATAM si quieres volumen); inglés ya está, así que puedes añadir US/UK/Global con cero esfuerzo de código.
- Email de contacto y web de soporte.

### 2.5 Anuncios (AdMob) — cerrar antes de producción
1. **Verificar IDs**: los IDs de banner/interstitial actuales ¿son de test (`ca-app-pub-3940256099942544/...`) o reales? En producción deben ser IDs reales de tu cuenta AdMob.
2. **`app-ads.txt`**: AdMob te dará un archivo `app-ads.txt` que debes publicar en tu dominio (el mismo que uses para la política de privacidad). Sin él, algunos anuncios no se sirven y AdMob te lo marca como pendiente.
3. **Política de anuncios**: no mostrar anuncios a perfiles infantiles (cumple la regla de menores). Revisa que el banner/interstitial no se muestre en la vista simplificada de niño.

### 2.6 Secuencia de lanzamiento (la que ya soporta tu pipeline)
1. **Closed testing** (alpha): 20+ testers reales durante 14 días mínimo *(Play exige un testing cerrado con al menos 20 testers para poder pasar a producción en cuentas nuevas)*.
2. **Open testing** (opcional): público sin invitación.
3. **Producción**: `workflow_dispatch` con `track: production`, o tag semántico.

Tu `release.yml` ya soporta los tres tracks (`internal`, `alpha`, `production`). Para producción:
```bash
# en ~/task-hub
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain   # verificación rápida
git tag v1.0.0 && git push origin main && git push origin v1.0.0  # dispara release
```
O manualmente desde GitHub → Actions → *Release — Build & Deploy* → `track: production`.

---

## 3. Firebase — seguridad y producción

- **Firestore rules v2** ✅ ya en el repo (`firestore.rules`). Dos observaciones antes de producción:
  1. `households/{hid}` permite `get` a **cualquier usuario autenticado** (el ID es aleatorio → no enumerable, aceptable). Está bien, pero sé consciente.
  2. Sin rate-limiting: un usuario malicioso podría spamear lecturas/escrituras. Para una app pequeña es aceptable; si crece, considera App Check (gratis) o cuotas.
- **Desplegar las rules** (si no están ya aplicadas en el proyecto `task-hub-62f98`):
  ```bash
  firebase deploy --only firestore:rules
  ```
- **Firebase Analytics**: añádelo ahora (`com.google.firebase:firebase-analytics-ktx`). Es gratis y es *la* fuente de datos para el marketing (instalaciones, retención, embudos). Sin él, volarás a ciegas.

---

## 4. Checklist final paso a paso

- [ ] IDs AdMob de producción (no test) + `app-ads.txt` publicado
- [ ] Firebase Analytics integrado
- [ ] Política de privacidad publicada (Firebase Hosting o GitHub Pages)
- [ ] Formulario Data Safety completado
- [ ] Cuestionario IARC completado
- [ ] Listado: título, descripciones, icono 512, feature graphic, capturas
- [ ] Precio gratuito + países seleccionados
- [ ] Closed testing con ≥20 testers (14 días)
- [ ] Opcional: open testing
- [ ] Revisar en dispositivo real el flujo completo (ya lo haces manualmente)
- [ ] `track: production`

---

## 5. Riesgos y pitfalls

- **Pitfall conocido** (ya documentado en la skill `task-hub`): `google-services.json` redacta `DEFAULT_API_KEY`; si lo regeneras, restáurala o romperás auth.
- **Play rechaza por targetSdk antiguo**: llevas 35, correcto (mínimo exigido 34+).
- **Rechazo por anuncios sin `app-ads.txt`** o por anuncios visibles a menores: cubierto en §2.5.
- **Perfiles infantiles** → no marques "diseñada para familias" (§2.3).
- **Versión duplicada**: el `release.yml` usa `versionCode = date +%s` (epoch). Asegúrate de no re-publicar la misma `versionName` con un `versionCode` que ya subiste, o Play lo rechazará. Sube el `versionCode` en cada release (ya lo haces con el bump manual).
