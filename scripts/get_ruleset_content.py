#!/usr/bin/env python3
"""Descarga el contenido del ruleset indicado y lo vuelca a stdout."""
import sys
import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

SA = "/home/liberto/.hermes/firebase-service-account.json"
PROJECT = "task-hub-62f98"

ruleset = sys.argv[1]  # nombre completo del ruleset

c = service_account.Credentials.from_service_account_file(
    SA, scopes=["https://www.googleapis.com/auth/cloud-platform"])
c.refresh(Request())
H = {"Authorization": "Bearer " + c.token}

# GET del ruleset (devuelve source.files[])
r = requests.get(f"https://firebaserules.googleapis.com/v1/{ruleset}", headers=H)
print("status:", r.status_code)
d = r.json()
files = d.get("source", {}).get("files", [])
for f in files:
    if f.get("name") == "firestore.rules":
        print(f.get("content", ""))
