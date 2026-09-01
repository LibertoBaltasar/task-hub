#!/usr/bin/env python3
"""Consulta el ruleset activo (live) de cloud.firestore en producción."""
import json
import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

SA = "/home/liberto/.hermes/firebase-service-account.json"
PROJECT = "task-hub-62f98"

c = service_account.Credentials.from_service_account_file(
    SA, scopes=["https://www.googleapis.com/auth/cloud-platform"])
c.refresh(Request())
H = {"Authorization": "Bearer " + c.token}

r = requests.get(
    f"https://firebaserules.googleapis.com/v1/projects/{PROJECT}/releases/cloud.firestore",
    headers=H)
print("GET release status:", r.status_code)
d = r.json()
print("live rulesetName:", d.get("rulesetName"))
print("updateTime:      ", d.get("updateTime"))
print("name:            ", d.get("name"))
