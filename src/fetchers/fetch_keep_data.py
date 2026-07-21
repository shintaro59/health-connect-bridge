#!/usr/bin/env python3
"""
Google Keep データ取得スクリプト
Health Database Sync - フェーズ 2 実装
"""

import os
import json
import logging
from datetime import datetime, timedelta
from pathlib import Path
from dotenv import load_dotenv

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from google.api_core.exceptions import GoogleAPIError
import googleapiclient.discovery as discovery

# ロギング設定
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 環境変数読み込み
load_dotenv()

# Google API スコープ
SCOPES = [
    'https://www.googleapis.com/auth/keep.readonly',
    'https://www.googleapis.com/auth/fitness.body.read',
    'https://www.googleapis.com/auth/fitness.activity.read',
]

PROJECT_ID = 'health-database-sync'
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
CONFIG_DIR = PROJECT_ROOT / 'config'
OBSIDIAN_VAULT = Path(os.environ.get('OBSIDIAN_VAULT_PATH', r'C:\Users\shint\Downloads\データベース'))

def authenticate():
    """Google API 認証"""
    creds = None
    token_path = CONFIG_DIR / 'keep_token.json'
    credentials_path = CONFIG_DIR / 'keep_credentials.json'

    logger.info("認証情報を確認中...")

    # 保存済み認証情報を読み込み
    if token_path.exists():
        logger.info("✅ 保存済みトークンを検出")
        creds = Credentials.from_authorized_user_file(str(token_path), SCOPES)

    # トークンが有効かチェック
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            logger.info("🔄 トークンをリフレッシュ中...")
            creds.refresh(Request())
        else:
            # credentials.json を探す
            if not credentials_path.exists():
                logger.error(f"❌ {credentials_path.name} が見つかりません")
                logger.info("以下の方法で認証情報を取得してください:")
                logger.info("1. Google Cloud Console で OAuth クライアント ID を作成")
                logger.info(f"2. {credentials_path.name} を {CONFIG_DIR} に配置")
                raise FileNotFoundError(f"{credentials_path} が必要です")

            logger.info("📝 OAuth 認証フロー開始...")
            flow = InstalledAppFlow.from_client_secrets_file(
                str(credentials_path), SCOPES)
            creds = flow.run_local_server(port=8080)

            logger.info("✅ 認証成功")
            with open(token_path, 'w') as token:
                token.write(creds.to_json())
            logger.info(f"💾 トークンを保存: {token_path}")

    return creds

def fetch_keep_data(creds):
    """Google Keep データ取得"""
    logger.info("Google Keep データ取得開始...")

    try:
        keep_service = discovery.build('keep', 'v1', credentials=creds)

        # 今日のメモを取得
        results = keep_service.notes().list(pageSize=100).execute()
        notes = results.get('notes', [])

        logger.info(f"✅ {len(notes)} 個のメモを取得しました")

        # 本日のメモをフィルタ
        today = datetime.now().date()
        today_notes = []

        for note in notes:
            # メモの更新日時を確認
            update_time = note.get('updateTime', '')
            if update_time:
                note_date = datetime.fromisoformat(update_time.replace('Z', '+00:00')).date()
                if note_date == today:
                    today_notes.append(note)

        logger.info(f"📋 本日のメモ: {len(today_notes)} 件")
        return today_notes

    except Exception as e:
        logger.error(f"❌ Keep データ取得エラー: {e}")
        return []

def fetch_fit_data(creds):
    """Google Fit データ取得（睡眠・運動）"""
    logger.info("Google Fit データ取得開始...")

    try:
        fit_service = discovery.build('fitness', 'v1', credentials=creds)

        # 過去 24 時間のデータを取得
        now_ms = int(datetime.now().timestamp() * 1000)
        start_time_ms = int((datetime.now() - timedelta(days=1)).timestamp() * 1000)

        # 睡眠データを取得
        body = {
            'aggregateBy': [{
                'dataTypeName': 'com.google.android.gms.sleep.segment',
            }],
            'bucketByTime': {
                'durationMillis': 86400000,  # 24時間
            },
            'startTimeMillis': start_time_ms,
            'endTimeMillis': now_ms,
        }

        result = fit_service.users().dataset().aggregate(
            userId='me', body=body).execute()

        logger.info(f"✅ Fit データを取得しました")
        return result

    except Exception as e:
        logger.error(f"❌ Fit データ取得エラー: {e}")
        return None

def save_to_obsidian(keep_notes, fit_data):
    """Obsidian に日次ログとして保存"""
    logger.info("Obsidian への保存開始...")

    daily_log_dir = OBSIDIAN_VAULT / 'personal_data' / 'daily_log'
    daily_log_dir.mkdir(parents=True, exist_ok=True)

    # 本日のファイル名
    today = datetime.now().date()
    log_file = daily_log_dir / f'{today}.md'

    # Markdown コンテンツ生成
    content = f"""---
date: {today}
tags: [health, daily_log, keep, fit]
---

# {today} 健康記録

## Keep データ（抽出予定）
[本日のメモ: {len(keep_notes)} 件]

## Fit データ（抽出予定）
[本日のデータ取得完了]

---
*自動生成: {datetime.now().isoformat()}*
"""

    # ファイルに保存
    log_file.write_text(content, encoding='utf-8')
    logger.info(f"✅ {log_file} に保存しました")

def main():
    """メイン処理"""
    logger.info("="*50)
    logger.info("Health Database Sync - データ取得スクリプト")
    logger.info("="*50)

    try:
        # Google API 認証
        logger.info("\n🔐 Google API 認証中...")
        creds = authenticate()
        logger.info("✅ 認証成功")

        # Keep データ取得
        logger.info("\n📝 Google Keep データ取得...")
        keep_notes = fetch_keep_data(creds)

        # Fit データ取得
        logger.info("\n🏃 Google Fit データ取得...")
        fit_data = fetch_fit_data(creds)

        # Obsidian に保存
        logger.info("\n📚 Obsidian に保存...")
        save_to_obsidian(keep_notes, fit_data)

        logger.info("\n✅ すべての処理が完了しました")
        logger.info("="*50)

    except Exception as e:
        logger.error(f"❌ エラーが発生しました: {e}")
        raise

if __name__ == '__main__':
    main()
