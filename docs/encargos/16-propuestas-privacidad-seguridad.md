---
workdir: /home/liberto/task-hub
max_turns: 300
allowed_tools: Read,Edit,Write,Bash,Grep,Glob,Task
---

# Encargo: PROPUESTAS del panel v4 — Privacidad / seguridad

El panel de expertos v4 (`docs/review-panel-expertos-v4.md`) dejó 7 PROPUESTAs
de privacidad/seguridad. El usuario ha ACEPTADO todas. Decisiones YA TOMADAS:

1. **deleteHousehold no borra el hogar si fallaron subcolecciones** (Exp. 8 #2).
   Acumula los fallos de borrado de subcolecciones; si hubo alguno, NO borres
   el documento del hogar (queda para un reintento idempotente posterior).

2. **deleteAccount no borra la cuenta Auth si el cascade falló** (Exp. 12 #2).
   Acumula los fallos del bucle leaveHousehold/deleteHousehold; si hubo alguno,
   NO borres la cuenta Auth (paso irreversible) y repórtalo al usuario.

3. **authorName no anonimizado** (Exp. 10 #4). En el cascade-delete, reescribe
   `authorName` de los mensajes/comentarios del miembro borrado a "Miembro
   eliminado" (clave i18n ES/EN), en vez de dejar el nombre real visible.

4. **Revocar OAuth Google Calendar al eliminar cuenta** (Exp. 10 #5). Al
   eliminar cuenta, revoca el token/vínculo OAuth de Google Calendar.

5. **Reautenticación reciente antes de eliminar cuenta** (Exp. 9). Exige
   reautenticación reciente antes de la acción de borrado irreversible.

6. **Doble confirmación para eliminar cuenta** (Exp. 3/5). Añade una segunda
   confirmación, como ya tiene "eliminar hogar".

7. **Regla `members/{mid}` create: validar campos** (Exp. 9 #2). En
   `firestore.rules`, valida `appreciationGiven`/`appreciationWeekStart`/
   `leftAt` en las ramas create de `members/{mid}` (campos que el cliente no
   debería poder fijar arbitrariamente en el alta).

## Contexto técnico
- `network/FirestoreRepository.kt` (leaveHousehold, deleteHousehold,
  deleteAccount), `firestore.rules`, `ui/components/SettingsSheet.kt`,
  `ui/i18n/AppStrings.kt`.
- La regla de delete de `members/{mid}` YA ESTÁ desplegada (ruleset
  29319b00-f081-48db-bf41-20d0e431afc4); no la toques salvo para añadir la
  validación del punto 7 (que es un cambio de reglas nuevo).

## Verificación OBLIGATORIA
```
cd /home/liberto/task-hub && ./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
cd /home/liberto/task-hub && ./gradlew :composeApp:jvmTest --console=plain
```
`BUILD SUCCESSFUL` obligatorio y tests en verde. Si cambias `firestore.rules`,
NO lo despliegues (el despliegue lo hace el coordinador Hermes); déjalo
documentado en el resumen.

## Convenciones
- Comentarios/KDoc en español. NO hagas commit, push ni bump. i18n ES+EN.

## Entrega (resumen final obligatorio)
1. Propuestas aplicadas y cómo.
2. Archivos tocados.
3. Resultado de build/tests.
4. Si cambiaste firestore.rules, resúmelo (no despliegues).
5. Lo que no pudieras aplicar y por qué.
