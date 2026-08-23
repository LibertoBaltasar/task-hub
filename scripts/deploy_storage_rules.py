#!/usr/bin/env python3
"""Publica storage.rules creando el ruleset y el release de firebase.storage.

Equivalente a deploy_firestore_rules.py, pero para el release `firebase.storage`
en lugar de `cloud.firestore`.

Requisito previo: el bucket de Storage debe existir (habilitar Storage en
Firebase Console → Storage → Comenzar). Sin bucket, la subida de avatares
fallará con 404 aunque las reglas se publiquen correctamente.
"""

import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

SA = "/home/liberto/.hermes/firebase-service-account.json"
PROJECT = "task-hub-62f98"
RULES_FILE = "/home/liberto/task-hub/storage.rules"

c = service_account.Credentials.from_service_account_file(
    SA, scopes=["https://www.googleapis.com/auth/cloud-platform"])
c.refresh(Request())
H = {"Authorization": "Bearer " + c.token, "Content-Type": "application/json"}
BASE = "https://firebaserules.googleapis.com/v1"

content = open(RULES_FILE).read()

# 1) Crear ruleset
r = requests.post(
    f"{BASE}/projects/{PROJECT}/rulesets",
    headers=H,
    json={"source": {"files": [{"name": "storage.rules", "content": content}]}})
if r.status_code not in (200, 201):
    print("ERROR creando ruleset:", r.status_code, r.text)
    sys.exit(1)
ruleset = r.json()["name"]
print("ruleset:", ruleset)

# 2) Publicar el release de firebase.storage
r2 = requests.post(
    f"{BASE}/projects/{PROJECT}/releases",
    headers=H,
    json={
        "name": f"projects/{PROJECT}/releases/firebase.storage",
        "rulesetName": ruleset,
    })
print("publish status:", r2.status_code)
print(r2.text[:500])
