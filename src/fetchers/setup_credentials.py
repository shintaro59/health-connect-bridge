#!/usr/bin/env python3
"""config/keep_credentials.json の雛形を生成する（実際のclient_secretは手動で差し替えること）"""
import json
from pathlib import Path

CONFIG_DIR = Path(__file__).resolve().parent.parent.parent / 'config'

credentials = {
    "installed": {
        "client_id": "878048780015-34rb137p5kpcp6mbhaoi2pmsdq13hba3.apps.googleusercontent.com",
        "project_id": "health-database-sync",
        "auth_uri": "https://accounts.google.com/o/oauth2/auth",
        "token_uri": "https://oauth2.googleapis.com/token",
        "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
        "client_secret": "GOCSPX-placeholder",
        "redirect_uris": ["http://localhost:8080/"]
    }
}

CONFIG_DIR.mkdir(parents=True, exist_ok=True)
out_path = CONFIG_DIR / 'keep_credentials.json'
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(credentials, f, indent=2, ensure_ascii=False)

print(f"✅ {out_path} を生成しました")
