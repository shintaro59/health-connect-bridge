#!/usr/bin/env python3
"""
アンケートサイト自動化テスト - Playwright使用
ECナビ、アンとケイト、マクロミルのハング問題を診断
対話的にパスワード入力（セキュア）
"""

import asyncio
from playwright.async_api import async_playwright
import getpass
import sys

async def test_macromill(email, password):
    """マクロミルのループ問題を確認"""
    print("\n" + "="*60)
    print("🔍 マクロミル - ログイン＆ハング診断")
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
                print("🔐 ログイン画面を検出。ログイン処理中...")

                # ログインメールを入力
                try:
                    await page.fill("input[type='email']", email, timeout=5000)
                    print("✅ メールアドレス入力完了")
                except:
                    await page.fill("input[name*='mail']", email, timeout=5000)
                    print("✅ メールアドレス入力完了")

                # ログインパスワードを入力
                try:
                    await page.fill("input[type='password']", password, timeout=5000)
                    print("✅ パスワード入力完了")
                except:
                    await page.fill("input[name*='pass']", password, timeout=5000)
                    print("✅ パスワード入力完了")

                # ログインボタンをクリック
                try:
                    await page.click("button[type='submit']", timeout=5000)
                    print("🔄 ログイン処理中...")
                    await page.wait_for_load_state("networkidle", timeout=15000)
                    print("✅ ログイン成功")
                except Exception as e:
                    print(f"⚠️  ログイン処理: {e}")

                await asyncio.sleep(2)
                print(f"📄 現在のURL: {page.url}")
            else:
                print("✨ 既にログイン済み")

            # アンケート一覧を探す
            surveys = await page.query_selector_all("button, a[href*='survey'], [class*='survey']")
            print(f"📋 アンケート関連要素検出数: {len(surveys)}")

            # フォームを確認
            forms = await page.query_selector_all("form")
            print(f"📝 フォーム数: {len(forms)}")

            await asyncio.sleep(3)

        except Exception as e:
            print(f"❌ エラー: {e}")
        finally:
            await browser.close()
            print("🔚 ブラウザ終了")


async def test_ecnavi(email, password):
    """ECナビの入力フォームハング診断"""
    print("\n" + "="*60)
    print("🔍 ECナビ - ログイン＆ハング診断")
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

            # ログイン画面を確認
            if "login" in page.url.lower():
                print("🔐 ログイン画面を検出。ログイン処理中...")

                # ログインメールを入力
                try:
                    await page.fill("input[type='email']", email, timeout=5000)
                    print("✅ メールアドレス入力完了")
                except:
                    await page.fill("input[name*='mail'], input[id*='mail']", email, timeout=5000)
                    print("✅ メールアドレス入力完了")

                # ログインパスワードを入力
                try:
                    await page.fill("input[type='password']", password, timeout=5000)
                    print("✅ パスワード入力完了")
                except:
                    await page.fill("input[name*='pass'], input[id*='pass']", password, timeout=5000)
                    print("✅ パスワード入力完了")

                # ログインボタンをクリック
                try:
                    await page.click("button[type='submit'], input[type='submit']", timeout=5000)
                    print("🔄 ログイン処理中...")
                    await page.wait_for_load_state("networkidle", timeout=15000)
                    print("✅ ログイン成功")
                except Exception as e:
                    print(f"⚠️  ログイン処理: {e}")

                await asyncio.sleep(2)
                print(f"📄 現在のURL: {page.url}")

            # ページの構造を確認
            await asyncio.sleep(2)

            # アンケートフォームを探す
            forms = await page.query_selector_all("form")
            print(f"📋 フォーム検出数: {len(forms)}")

            inputs = await page.query_selector_all("input[type='text'], textarea, select")
            print(f"📝 入力フィールド検出数: {len(inputs)}")

            # アンケート要素を探す
            survey_buttons = await page.query_selector_all("[class*='answer'], [class*='survey'], button")
            print(f"📝 ボタン/アンケート関連要素: {len(survey_buttons)}")

        except Exception as e:
            print(f"❌ エラー: {e}")
        finally:
            await browser.close()
            print("🔚 ブラウザ終了")


async def test_annkate(email, password):
    """アンとケイトの入力フォームハング診断"""
    print("\n" + "="*60)
    print("🔍 アンとケイト - ログイン＆ハング診断")
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

            # ログイン画面を確認
            if "login" in page.url.lower():
                print("🔐 ログイン画面を検出。ログイン処理中...")

                # ログインメールを入力
                try:
                    await page.fill("input[type='email']", email, timeout=5000)
                    print("✅ メールアドレス入力完了")
                except:
                    await page.fill("input[name*='mail'], input[id*='mail']", email, timeout=5000)
                    print("✅ メールアドレス入力完了")

                # ログインパスワードを入力
                try:
                    await page.fill("input[type='password']", password, timeout=5000)
                    print("✅ パスワード入力完了")
                except:
                    await page.fill("input[name*='pass'], input[id*='pass']", password, timeout=5000)
                    print("✅ パスワード入力完了")

                # ログインボタンをクリック
                try:
                    await page.click("button[type='submit'], input[type='submit']", timeout=5000)
                    print("🔄 ログイン処理中...")
                    await page.wait_for_load_state("networkidle", timeout=15000)
                    print("✅ ログイン成功")
                except Exception as e:
                    print(f"⚠️  ログイン処理: {e}")

                await asyncio.sleep(2)
                print(f"📄 現在のURL: {page.url}")

            # ページの構造を確認
            await asyncio.sleep(2)

            # アンケートフォームを探す
            forms = await page.query_selector_all("form")
            print(f"📋 フォーム検出数: {len(forms)}")

            inputs = await page.query_selector_all("input[type='text'], textarea, select")
            print(f"📝 入力フィールド検出数: {len(inputs)}")

            # アンケート要素を探す
            survey_buttons = await page.query_selector_all("[class*='answer'], [class*='survey'], button")
            print(f"📝 ボタン/アンケート関連要素: {len(survey_buttons)}")

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

    # 各サイトの認証情報を対話的に入力
    print("\n📝 認証情報入力（パスワードは画面に表示されません）\n")

    print("【マクロミル】")
    macromill_email = input("📧 メールアドレス: ").strip()
    macromill_pass = getpass.getpass("🔐 パスワード: ")

    print("\n【ECナビ】")
    ecnavi_email = input("📧 メールアドレス: ").strip()
    ecnavi_pass = getpass.getpass("🔐 パスワード: ")

    print("\n【アンとケイト】")
    annkate_email = input("📧 メールアドレス: ").strip()
    annkate_pass = getpass.getpass("🔐 パスワード: ")

    print("\n" + "="*60)
    print("✅ 認証情報入力完了。診断を開始します...")
    print("="*60)

    # マクロミルを最初にテスト
    await test_macromill(macromill_email, macromill_pass)

    # 他のサイトもテスト
    await test_ecnavi(ecnavi_email, ecnavi_pass)
    await test_annkate(annkate_email, annkate_pass)

    print("\n" + "="*60)
    print("✅ 診断完了")
    print("="*60)


if __name__ == "__main__":
    asyncio.run(main())
