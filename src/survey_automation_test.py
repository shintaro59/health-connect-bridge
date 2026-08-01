#!/usr/bin/env python3
"""
アンケートサイト自動化テスト - Playwright使用
ECナビ、アンとケイト、マクロミルのハング問題を診断
"""

import asyncio
from playwright.async_api import async_playwright
import sys

async def test_macromill():
    """マクロミルのループ問題を確認"""
    print("\n" + "="*60)
    print("🔍 マクロミル - ループ問題診断")
    print("="*60)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        try:
            print("📍 マクロミル にアクセス中...")
            await page.goto("https://monitor.macromill.com/", timeout=30000)

            print("✅ ページロード完了")
            print(f"📄 URL: {page.url}")
            print(f"📝 タイトル: {await page.title()}")

            # ログイン画面を確認
            if "login" in page.url.lower() or "signin" in page.url.lower():
                print("🔐 ログイン画面が表示されています")
                print("⚠️  ログイン認証情報が必要です（スキップ）")
            else:
                print("✨ ログイン済み or メインページ")

            await asyncio.sleep(3)

        except Exception as e:
            print(f"❌ エラー: {e}")
        finally:
            await browser.close()
            print("🔚 ブラウザ終了")


async def test_ecnavi():
    """ECナビの入力フォームハング診断"""
    print("\n" + "="*60)
    print("🔍 ECナビ - 入力フォームハング診断")
    print("="*60)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        try:
            print("📍 ECナビ にアクセス中...")
            await page.goto("https://ecnavi.jp/research/", timeout=30000)

            print("✅ ページロード完了")
            print(f"📄 URL: {page.url}")
            print(f"📝 タイトル: {await page.title()}")

            # ページの構造を確認
            await asyncio.sleep(2)

            # アンケートフォームを探す
            forms = await page.query_selector_all("form")
            print(f"📋 フォーム検出数: {len(forms)}")

            inputs = await page.query_selector_all("input[type='text'], textarea, select")
            print(f"📝 入力フィールド検出数: {len(inputs)}")

        except Exception as e:
            print(f"❌ エラー: {e}")
        finally:
            await browser.close()
            print("🔚 ブラウザ終了")


async def test_annkate():
    """アンとケイトの入力フォームハング診断"""
    print("\n" + "="*60)
    print("🔍 アンとケイト - 入力フォームハング診断")
    print("="*60)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        try:
            print("📍 アンとケイト にアクセス中...")
            await page.goto("https://www.ann-kate.jp/monitor/", timeout=30000)

            print("✅ ページロード完了")
            print(f"📄 URL: {page.url}")
            print(f"📝 タイトル: {await page.title()}")

            # ページの構造を確認
            await asyncio.sleep(2)

            # アンケートフォームを探す
            forms = await page.query_selector_all("form")
            print(f"📋 フォーム検出数: {len(forms)}")

            inputs = await page.query_selector_all("input[type='text'], textarea, select")
            print(f"📝 入力フィールド検出数: {len(inputs)}")

        except Exception as e:
            print(f"❌ エラー: {e}")
        finally:
            await browser.close()
            print("🔚 ブラウザ終了")


async def main():
    print("\n🚀 アンケートサイト自動化診断ツール起動")
    print("=" * 60)
    print("対象サイト: マクロミル、ECナビ、アンとケイト")
    print("=" * 60)

    # マクロミルを最初にテスト
    await test_macromill()

    # 他のサイトもテスト
    await test_ecnavi()
    await test_annkate()

    print("\n" + "="*60)
    print("✅ 診断完了")
    print("="*60)


if __name__ == "__main__":
    asyncio.run(main())
