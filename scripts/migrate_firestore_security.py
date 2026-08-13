#!/usr/bin/env python3
"""
Migración de Task Hub para las nuevas reglas de seguridad de Firestore.

Convierte el esquema antiguo (miembros con ID auto-generado, hogares sin ownerId,
join por query de inviteCode) al nuevo esquema que exigen firestore.rules:

  1. households/{id}.ownerId  ← UID del creador (se deduce del miembro con userId).
  2. members/{uid}            ← los miembros vinculados a una cuenta se re-keyan
                                 con su UID como ID de documento.
  3. Referencias memberId     ← se actualizan en assignments, taskHistory,
                                 notifications y assignmentRotation.
  4. invites/{code}           ← mapa código → householdId (solo hogares compartidos).

Uso:  python3 migrate_firestore_security.py [--apply]
Sin --apply hace dry-run (solo muestra qué haría).
"""

import json
import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

SA = "/home/liberto/.hermes/firebase-service-account.json"
PROJECT = "task-hub-62f98"
BASE = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents"

APPLY = "--apply" in sys.argv


def creds():
    c = service_account.Credentials.from_service_account_file(
        SA, scopes=["https://www.googleapis.com/auth/cloud-platform"])
    c.refresh(Request())
    return c.token


H = {"Authorization": "Bearer " + creds()}


def get(path):
    r = requests.get(BASE + path, headers=H)
    r.raise_for_status()
    return r.json()


def post(path, body, params=None):
    if APPLY:
        r = requests.post(BASE + path, headers=H, json=body, params=params)
        r.raise_for_status()
        return r.json()


def patch(path, fields, mask):
    if APPLY:
        r = requests.patch(
            BASE + path,
            headers=H,
            json={"fields": fields},
            params={"updateMask.fieldPaths": mask})
        r.raise_for_status()
        return r.json()


def delete(path):
    if APPLY:
        requests.delete(BASE + path, headers=H).raise_for_status()


def sv(v):
    return {"stringValue": v}


def main():
    households = get("/households").get("documents", [])
    log = []
    total = {"ownerId": 0, "members_rekeyed": 0, "refs": 0, "invites": 0}

    for h in households:
        hid = h["name"].split("/")[-1]
        hf = h.get("fields", {})
        name = hf.get("name", {}).get("stringValue", "?")
        is_personal = hf.get("isPersonal", {}).get("booleanValue", False)
        invite = hf.get("inviteCode", {}).get("stringValue", "")

        members = get(f"/households/{hid}/members").get("documents", [])

        # 1) ownerId = primer miembro con userId no nulo.
        owner = next(
            (m.get("fields", {}).get("userId", {}).get("stringValue")
             for m in members
             if m.get("fields", {}).get("userId", {}).get("stringValue")),
            None)

        if owner and hf.get("ownerId", {}).get("stringValue") is None:
            patch(f"/households/{hid}", {"ownerId": sv(owner)}, "ownerId")
            total["ownerId"] += 1
            log.append(f"ownerId={owner}  <- {name} ({hid})")

        # 2) Re-key de miembros con userId: ID automático -> members/{uid}.
        old_to_new = {}
        for m in members:
            mid = m["name"].split("/")[-1]
            mf = m.get("fields", {})
            uid = mf.get("userId", {}).get("stringValue")
            if uid and mid != uid:
                body = {"fields": mf}
                post(f"/households/{hid}/members", body, params={"documentId": uid})
                delete(f"/households/{hid}/members/{mid}")
                old_to_new[mid] = uid
                total["members_rekeyed"] += 1
                log.append(f"member {mid} -> {uid}  ({name})")

        # 3) Actualizar referencias memberId en subcolecciones.
        def fix_ref(sub, id_field="memberId"):
            for d in get(f"/households/{hid}/{sub}").get("documents", []):
                did = d["name"].split("/")[-1]
                df = d.get("fields", {})
                old = df.get(id_field, {}).get("stringValue")
                if old in old_to_new:
                    patch(f"/households/{hid}/{sub}/{did}",
                          {id_field: sv(old_to_new[old])}, id_field)
                    total["refs"] += 1

        fix_ref("assignments")
        fix_ref("taskHistory")
        fix_ref("notifications")
        fix_ref("rewardRedemptions")

        # assignmentRotation (array de {dayOfWeek, memberId}) dentro de tasks.
        for t in get(f"/households/{hid}/tasks").get("documents", []):
            tid = t["name"].split("/")[-1]
            tf = t.get("fields", {})
            rot = tf.get("assignmentRotation", {}).get("arrayValue", {}).get("values")
            if not rot:
                continue
            changed = False
            new_rot = []
            for slot in rot:
                fields = slot.get("mapValue", {}).get("fields", {})
                mid = fields.get("memberId", {}).get("stringValue")
                if mid in old_to_new:
                    fields = dict(fields)
                    fields["memberId"] = sv(old_to_new[mid])
                    changed = True
                new_rot.append({"mapValue": {"fields": fields}})
            if changed:
                patch(f"/households/{hid}/tasks/{tid}",
                      {"assignmentRotation": {"arrayValue": {"values": new_rot}}},
                      "assignmentRotation")
                total["refs"] += 1

        # 4) invites/{code} para hogares compartidos.
        if not is_personal and invite and invite != "PERSONAL":
            try:
                existing = get(f"/invites/{invite}").get("fields", {})
            except requests.HTTPError:
                existing = {}
            if existing.get("householdId", {}).get("stringValue") != hid:
                post("/invites", {"fields": {"householdId": sv(hid)}},
                     params={"documentId": invite})
                total["invites"] += 1
                log.append(f"invite {invite} -> {hid}  ({name})")

    print("MODO:", "APPLY" if APPLY else "DRY-RUN")
    print(json.dumps(total, indent=2))
    print(f"households procesados: {len(households)}")
    for line in log:
        print("  ", line)


if __name__ == "__main__":
    main()
