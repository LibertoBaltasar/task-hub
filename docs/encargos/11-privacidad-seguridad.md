---
workdir: /home/liberto/task-hub
max_turns: 350
allowed_tools: Read,Edit,Write,Bash,Grep,Glob
---

# Encargo: privacidad/RGPD + seguridad (decisión EXPLÍCITA del usuario)

## Contexto
El panel v3 (`docs/review-panel-expertos-v3-2026-09-01.md`) dejó varios hallazgos
de privacidad y seguridad como PROPUESTA. El usuario ha decidido aplicarlos.
Versión actual 0.7.25 (HEAD `c8d11e1`). Lee el informe antes de empezar, en
especial los expertos 9 (Seguridad) y 10 (Privacidad/RGPD).

## Cambios a aplicar (decisión del usuario — no pedir confirmación)

### 1. `firestore.rules`: validar límites en la rama owner/admin de `create` en `members/{mid}` (M2)
- La rama `isOwner`/`isAdminMember` de `create` en `members/{mid}` no valida los
  mismos límites (`totalPoints==0`, UID legítimo, etc.) que la rama de auto-alta
  por invitación. Un owner/admin con cliente modificado podría crear un miembro
  con UID arbitrario y puntos arbitrarios.
- Añade la validación simétrica en `firestore.rules`. **NO despliegues tú**: solo
  deja el archivo editado y reporta al final "REGLAS LISTAS" (el despliegue lo
  hace el orquestador con PATCH). Verifica que la regla nueva no rompe el alta de
  miembro por invitación ni el alta inicial del propio owner.

### 2. Borrado REAL de datos (cascade-delete)
- Hoy `deleteHousehold` no hace cascade-delete (comentario explícito en el código)
  y `deleteMember` es soft-delete (`leftAt`). Esto contradice `docs/privacy.html`
  ("puedes eliminar tus hogares... desde la app").
- Implementa el borrado real: al borrar un hogar, borrar sus subcolecciones
  (tasks, taskHistory, members, messages, rewards, notifications, etc.) y sus
  datos; al eliminar un miembro, borrar (o anonimizar) sus datos personales.
  Evalúa la opción más limpia y fiable (borrado en cascada desde el cliente con
  orden correcto y reintentos, o un helper de backend si el repo lo permite).
  Documenta qué subcolecciones quedan cubiertas.
- Ajusta `firestore.rules` si el borrado necesita permisos nuevos. Reporta
  "REGLAS LISTAS / NO LISTAS".

### 3. Flujo de "eliminar cuenta" en la app
- Hoy `privacy.html` remite a email manual; no hay flujo en la app. Añade una
  acción "Eliminar cuenta" (en Ajustes) que borre los datos del usuario (o lo
  guíe correctamente), con confirmación y su clave i18n ES+EN. Si requiere
  backend del que el repo no dispone, implementa la parte cliente y documenta
  qué falta en el servidor.

### 4. Cifrar refresh tokens (hallazgo B1 de Seguridad)
- Los refresh tokens se guardan en texto plano vía `multiplatform-settings`
  (SharedPreferences/NSUserDefaults sin cifrar).
- Migra a almacenamiento cifrado: `EncryptedSharedPreferences` en Android y
  Keychain en iOS (y lo equivalente en JVM si aplica), manteniendo la exclusión
  de backups (`backup_rules.xml`). Hazlo de forma aditiva/migrativa sin romper a
  usuarios ya autenticados si es posible.

## Criterios
- APLICA YA: decisión explícita del usuario.
- NO despliegues reglas: solo edita `firestore.rules` y reporta "REGLAS LISTAS /
  NO LISTAS" al final.
- i18n ES+EN sin texto hardcodeado. Solo `material-icons-core`.
- No toques diseño salvo consistencia.

## Verificación (OBLIGATORIO)
```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` obligatorio y tests en verde.

## Convenciones
- Comentarios/KDoc en español. NO hagas commit, push ni bump.

## Entrega (resumen final obligatorio)
1. Por cada punto: qué hiciste, con `archivo:línea`, y si quedó REGLAS LISTAS.
2. Resultado de compilación y tests.
3. Qué dejaste a medias (p. ej. si algo requiere backend) con motivo.
