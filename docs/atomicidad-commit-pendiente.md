# Atomicidad real de `completeTask`/`reassignTaskCompletion` vía `:commit` — evaluado y descartado (2026-08-30)

## Encargo

Sustituir las secuencias multi-escritura de `completeTask` y `reassignTaskCompletion`
(`network/FirestoreRepository.kt`) por una transacción atómica usando el endpoint
`:commit` de la API REST de Firestore con `fieldTransforms` (increment,
setToServerValue, etc.), documentando cada transform con KDoc.

## Decisión: NO implementado

Se revisó el código y se decidió **no** escribir el payload `:commit`. Motivo:
ni este repositorio ni ningún test de este proyecto han hecho nunca una llamada
al endpoint `:commit` de Firestore, y este entorno no tiene acceso a un proyecto
Firestore real (ni emulador) contra el que verificar el payload. El propio
encargo lo advierte explícitamente: *"si no puedes garantizar un `:commit`
correcto sin verificarlo contra la API, NO hagas un fix mal formado"*.

Esto coincide con la conclusión independiente de la primera auditoría
(`docs/audit-2026-08-30.md`, hallazgo A21 y nota 3 de "Deuda técnica
pendiente"), que evaluó lo mismo y lo descartó por el mismo motivo. Esta
segunda revisión confirma que nada ha cambiado desde entonces: sigue sin existir
en el repo ningún uso probado de `:commit`, `Write`, `DocumentTransform` ni
`Precondition` sobre el que apoyarse (solo existe el patrón PATCH +
`currentDocument.updateTime` como query param, usado en `addMemberPoints`/
`addMemberAchievement` — un mecanismo distinto: precondition de una escritura
REST individual, no de un batch `:commit`).

### Por qué no basta con "lo sé de memoria"

El formato de `:commit` es razonablemente conocido (`writes[]`, cada `Write`
con `update`/`transform`/`delete`, `currentDocument` como precondition,
`updateMask`, y dentro de `transform.fieldTransforms[]` las primitivas
`increment`, `setToServerValue`, `maximum`, `minimum`,
`appendMissingElements`, `removeAllFromArray`). Pero construir el payload
completo implica acertar, sin poder probarlo:

- La URL exacta: es `.../databases/(default)/documents:commit` — **hermano**
  de `.../documents`, no un sufijo de `baseUrl` (`baseUrl` en este repo ya
  incluye `/documents`, así que sería `baseUrl.removeSuffix("/documents") +
  ":commit"`, un detalle fácil de dejar mal si no se ejecuta nunca).
- La forma exacta de `increment` (`{"integerValue": "N"}` anidado bajo
  `increment`, no un entero plano).
- Si `transform` y `update` pueden ir en el mismo `Write` o requieren dos
  `Write` separados apuntando al mismo documento dentro del mismo `writes[]`.
- El comportamiento exacto de `updateMask` combinado con `transform` (si se
  omite mal, un `update` parcial mal enmascarado podría pisar campos que no
  debían tocarse — justo el tipo de fallo silencioso e irreversible que un
  sistema de puntos no puede permitirse).
- Los DTOs (`FirestoreValue`, `FirestoreDocument` en `FirestoreDtos.kt`) no
  tienen hoy representación para `Write`/`DocumentTransform`/`FieldTransform`;
  habría que añadirlos enteros de cero, sin ningún test existente que ejercite
  su (de)serialización contra la API real.

Ninguno de estos puntos es "inventar la primitiva" en el sentido de
imaginar una feature que no existe — son detalles de forma del payload que sí
existen mal documentados. Es exactamente el tipo de payload potencialmente mal
formado contra el que advierte el encargo, sobre un sistema de puntos en
producción.

## Qué haría falta para hacerlo con seguridad

1. **Firestore Emulator Suite** (`firebase emulators:start --only firestore`)
   corriendo en un entorno con acceso a él, para poder golpear `:commit` de
   verdad y ver la respuesta/errores reales antes de tocar producción.
2. Un test de integración (`commonTest` o un script standalone) que:
   - Cree un documento de miembro con `totalPoints` conocido.
   - Envíe un `:commit` con un `Write` de `transform` (`increment`) + un
     `Write` de `update` sobre el documento de tarea, en la misma petición.
   - Verifique el `totalPoints` resultante y que ambos documentos cambiaron
     atómicamente (o ninguno, forzando un error de precondition).
3. Con eso verificado, extender `FirestoreDtos.kt` con los DTOs de
   `Write`/`DocumentTransform`/`FieldTransform`/`Precondition`, y solo
   entonces reescribir `completeTask`/`reassignTaskCompletion` para usarlos.
4. Cada `fieldTransform` documentado con KDoc en español, como pide el
   encargo original.

## Estado actual (sin cambios funcionales en este encargo)

`completeTask` y `reassignTaskCompletion` **no se han tocado**. Siguen con las
mitigaciones ya existentes de las dos pasadas de auditoría previas:

- Guarda de reentrancia en `TaskScreenModel` (`A8`, evita doble-tap).
- Orden de escrituras invertido donde aplica para que un fallo a mitad de
  camino deje el "peor" estado recuperable en vez de puntos duplicados (`A3`,
  `A4`, `A5`).
- Concurrencia optimista (`currentDocument.updateTime` + reintento) dentro de
  `addMemberPoints`/`addMemberAchievement`, que sí protege contra la pérdida
  de un incremento concurrente en el documento del miembro — aunque no hace
  atómica la secuencia completa frente al resto de escrituras de
  `completeTask`/`reassignTaskCompletion`.

No hay atomicidad de extremo a extremo entre "marcar tarea completada",
"sumar puntos" y "guardar historial" (o, en `reassignTaskCompletion`, entre
las dos transferencias de puntos y la actualización de `completedBy`/
historial). Un fallo de red a mitad de secuencia sigue siendo posible y sigue
dejando estado parcial, mitigado pero no eliminado por lo anterior.
