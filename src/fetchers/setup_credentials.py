#!/usr/bin/env python3
"""config/keep_credentials.json を生成する。

client_id / client_secret はコードに書かず、環境変数（または .env）から読む。
  GOOGLE_CLIENT_ID=...
  GOOGLE_CLIENT_SECRET=...
"""
import json
import os
import sys
from pathlib import Path

from dotenv import load_dotenv

ROOT_DIR = Path(__file__).resolve().parent.parent.parent
CONFIG_DIR = ROOT_DIR / 'config'

load_dotenv(ROOT_DIR / '.env')

client_id = os.environ.get("GOOGLE_CLIENT_ID")
client_secret = os.environ.get("GOOGLE_CLIENT_SECRET")
if not client_id or not client_secret:
    sys.exit("GOOGLE_CLIENT_ID と GOOGLE_CLIENT_SECRET を環境変数か .env に設定してください")

credentials = {
    "installed": {
        "client_id": client_id,
        "project_id": "health-database-sync",
        "auth_uri": "https://accounts.google.com/o/oauth2/auth",
        "token_uri": "https://oauth2.googleapis.com/token",
        "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
        "client_secret": client_secret,
        "redirect_uris": ["http://localhost:8080/"]
    }
}

CONFIG_DIR.mkdir(parents=True, exist_ok=True)
out_path = CONFIG_DIR / 'keep_credentials.json'
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(credentials, f, indent=2, ensure_ascii=False)

print(f"✅ {out_path} を生成しました")
