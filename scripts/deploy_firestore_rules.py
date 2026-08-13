#!/usr/bin/env python3
"""Publica firestore.rules creando el ruleset y el release de cloud.firestore."""

import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

SA = "/home/liberto/.hermes/firebase-service-account.json"
PROJECT = "task-hub-62f98"
RULES_FILE = "/home/liberto/task-hub/firestore.rules"

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
    json={"source": {"files": [{"name": "firestore.rules", "content": content}]}})
if r.status_code not in (200, 201):
    print("ERROR creando ruleset:", r.status_code, r.text)
    sys.exit(1)
ruleset = r.json()["name"]
print("ruleset:", ruleset)

# 2) Publicar el release de cloud.firestore
r2 = requests.post(
    f"{BASE}/projects/{PROJECT}/releases",
    headers=H,
    json={
        "name": f"projects/{PROJECT}/releases/cloud.firestore",
        "rulesetName": ruleset,
    })
print("publish status:", r2.status_code)
print(r2.text[:500])
