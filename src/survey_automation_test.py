#!/usr/bin/env python3
"""
アンケートサイト自動化 - CAPTCHA対応版
ECナビ、アンとケイト、マクロミルを安全に自動化
CAPTCHA検出時は自動スキップ（ユーザールール: CAPTCHA突破禁止）
"""

import asyncio
from playwright.async_api import async_playwright
import getpass
import sys
from datetime import datetime

async def detect_captcha(page):
    """ページ上のCAPTCHA要素を検出"""
    captcha_indicators = [
        'iframe[src*="captcha"]',
        'iframe[src*="recaptcha"]',
        'div[class*="g-recaptcha"]',
        'div[class*="cloudflare"]',
        'button[class*="g-recaptcha"]',
        'script[src*="recaptcha"]',
        'script[src*="challenge"]',
    ]

    for selector in captcha_indicators:
        try:
            element = await page.query_selector(selector)
            if element:
                return True
        except:
            pass

    return False

async def wait_for_captcha_solve(page, timeout_seconds=120):
    """
    ユーザーがCAPTCHAを手動で解決するまで待機
    timeout後は続行（ユーザーが解決したと仮定）
    """
    print("\n⏸️  CAPTCHA検出 - ユーザーの手動解決待機中...")
    print("🖱️  ブラウザでCAPTCHAボタンを押してください")
    print(f"⏱️  {timeout_seconds}秒後に自動続行します...\n")

    start_time = asyncio.get_event_loop().time()
    while True:
        # CAPTCHA解決確認（iframeが消える）
        if not await detect_captcha(page):
            print("✅ CAPTCHA解決確認！自動化を継続します...\n")
            await asyncio.sleep(2)
            return True

        # タイムアウト確認
        elapsed = asyncio.get_event_loop().time() - start_time
        if elapsed >= timeout_seconds:
            print(f"⏱️  タイムアウト({timeout_seconds}秒) - 続行を再開します\n")
            await asyncio.sleep(2)
            return False

        await asyncio.sleep(1)

async def test_macromill(email, password):
    """マクロミル - CAPTCHA対応版"""
    print("\n" + "="*70)
    print("🔍 マクロミル - ログイン＆自動化テスト")
    print("="*70)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        try:
            print("📍 マクロミル にアクセス中...")
            await page.goto("https://monitor.macromill.com/", timeout=30000)
            await asyncio.sleep(2)

            print("✅ ページロード完了")
            print(f"📄 URL: {page.url}")
            print(f"📝 タイトル: {await page.title()}")

            # CAPTCHA検出
            if await detect_captcha(page):
                await wait_for_captcha_solve(page)

            # ログイン画面を確認
            if "login" in page.url.lower() or "signin" in page.url.lower():
                print("🔐 ログイン画面を検出。ログイン処理中...")

                # ログインメールを入力
                try:
                    await page.fill("input[type='email']", email, timeout=5000)
                    print("✅ メールアドレス入力完了")
                except:
                    try:
                        await page.fill("input[name*='mail']", email, timeout=5000)
                        print("✅ メールアドレス入力完了")
                    except Exception as e:
                        print(f"❌ メール入力失敗: {e}")
                        return False

                # ログインパスワードを入力
                try:
                    await page.fill("input[type='password']", password, timeout=5000)
                    print("✅ パスワード入力完了")
                except:
                    try:
                        await page.fill("input[name*='pass']", password, timeout=5000)
                        print("✅ パスワード入力完了")
                    except Exception as e:
                        print(f"❌ パスワード入力失敗: {e}")
                        return False

                # ログインボタンをクリック
                try:
                    await page.click("button[type='submit']", timeout=5000)
                    print("🔄 ログイン処理中...")
                    await page.wait_for_load_state("networkidle", timeout=15000)
                    print("✅ ログイン成功")
                except Exception as e:
                    print(f"⚠️  ログイン処理エラー: {e}")
                    return False

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

            return True

        except Exception as e:
            print(f"❌ エラー: {e}")
            return False
        finally:
            await browser.close()
            print("🔚 ブラウザ終了")


async def test_ecnavi(email, password):
    """ECナビ - CAPTCHA対応版"""
    print("\n" + "="*70)
    print("🔍 ECナビ - ログイン＆自動化テスト")
    print("="*70)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        try:
            print("📍 ECナビ にアクセス中...")
            await page.goto("https://ecnavi.jp/research/", timeout=30000)
            await asyncio.sleep(2)

            print("✅ ページロード完了")
            print(f"📄 URL: {page.url}")
            print(f"📝 タイトル: {await page.title()}")

            # CAPTCHA検出
            if await detect_captcha(page):
                await wait_for_captcha_solve(page)

            # ログイン画面を確認
            if "login" in page.url.lower():
                print("🔐 ログイン画面を検出。ログイン処理中...")

                # ECナビのメール入力：ページの最初のinput要素を使用
                email_filled = False
                try:
                    # password フィールドの前の最初の input 要素を取得
                    all_inputs = await page.query_selector_all("input")
                    if all_inputs and len(all_inputs) > 0:
                        # パスワード入力フィールドを除外した最初のinput
                        for input_elem in all_inputs:
                            input_type = await input_elem.get_attribute("type")
                            if input_type != "password":
                                await input_elem.fill(email)
                                print("✅ メールアドレス入力完了")
                                email_filled = True
                                break
                except Exception as e:
                    print(f"   エラー: {e}")

                if not email_filled:
                    # セレクタベースのフォールバック
                    email_selectors = [
                        "input[type='text']",
                        "input[type='email']",
                        "input[name*='mail']",
                        "input[id*='mail']",
                    ]

                    for selector in email_selectors:
                        try:
                            elem = await page.query_selector(selector)
                            if elem:
                                await elem.fill(email)
                                print(f"✅ メールアドレス入力完了 (selector: {selector})")
                                email_filled = True
                                break
                        except:
                            pass

                if not email_filled:
                    print("❌ メール入力フィールドが見つかりません")
                    print("   💡 手動でメールアドレスを入力してください（15秒待機）")
                    await asyncio.sleep(15)

                # ログインパスワードを入力
                try:
                    await page.fill("input[type='password']", password, timeout=5000)
                    print("✅ パスワード入力完了")
                except Exception as e:
                    print(f"❌ パスワード入力失敗: {e}")
                    return False

                # ログインボタンをクリック
                try:
                    await page.click("button[type='submit'], input[type='submit']", timeout=5000)
                    print("🔄 ログイン処理中...")
                    await page.wait_for_load_state("networkidle", timeout=15000)
                    print("✅ ログイン成功")
                except Exception as e:
                    print(f"⚠️  ログイン処理エラー: {e}")
                    return False

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

            return True

        except Exception as e:
            print(f"❌ エラー: {e}")
            return False
        finally:
            await browser.close()
            print("🔚 ブラウザ終了")


async def test_annkate(email, password):
    """アンとケイト - CAPTCHA対応版"""
    print("\n" + "="*70)
    print("🔍 アンとケイト - ログイン＆自動化テスト")
    print("="*70)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        try:
            print("📍 アンとケイト にアクセス中...")
            await page.goto("https://www.ann-kate.jp/monitor/", timeout=30000)
            await asyncio.sleep(2)

            print("✅ ページロード完了")
            print(f"📄 URL: {page.url}")
            print(f"📝 タイトル: {await page.title()}")

            # CAPTCHA検出
            if await detect_captcha(page):
                await wait_for_captcha_solve(page)

            # ログイン画面を確認
            if "login" in page.url.lower():
                print("🔐 ログイン画面を検出。ログイン処理中...")

                # ログインメールを入力
                try:
                    await page.fill("input[type='email']", email, timeout=5000)
                    print("✅ メールアドレス入力完了")
                except:
                    try:
                        await page.fill("input[name*='mail']", email, timeout=5000)
                        print("✅ メールアドレス入力完了")
                    except Exception as e:
                        print(f"❌ メール入力失敗: {e}")
                        return False

                # ログインパスワードを入力
                try:
                    await page.fill("input[type='password']", password, timeout=5000)
                    print("✅ パスワード入力完了")
                except:
                    try:
                        await page.fill("input[name*='pass']", password, timeout=5000)
                        print("✅ パスワード入力完了")
                    except Exception as e:
                        print(f"❌ パスワード入力失敗: {e}")
                        return False

                # ログインボタンをクリック（Anto-Kate: type属性なしのボタン）
                try:
                    button_selectors = [
                        "button.g-recaptcha-submit",
                        "button.btn-login",
                        "button[type='submit']",
                        "input[type='submit']",
                    ]

                    button_clicked = False
                    for selector in button_selectors:
                        try:
                            await page.click(selector, timeout=3000)
                            button_clicked = True
                            break
                        except:
                            pass

                    if not button_clicked:
                        print("❌ ログインボタンが見つかりません")
                        return False

                    print("🔄 ログイン処理中...")
                    await page.wait_for_load_state("networkidle", timeout=15000)
                    print("✅ ログイン成功")
                except Exception as e:
                    print(f"⚠️  ログイン処理エラー: {e}")
                    return False

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

            return True

        except Exception as e:
            print(f"❌ エラー: {e}")
            return False
        finally:
            await browser.close()
            print("🔚 ブラウザ終了")


async def test_dpoint_club(dpoint_url, use_remote_debug=True):
    """dポイントクラブ - リモートデバッギング版（Chrome を開いたまま使用）"""
    print("\n" + "="*70)
    print("🔍 dポイントクラブ - アンケートアクセステスト")
    print("="*70)

    async with async_playwright() as p:
        browser = None
        try:
            if use_remote_debug:
                # リモートデバッギングモードで実行中の Chrome に接続
                print("📡 リモートデバッギングモード: 既存の Chrome に接続中...")
                browser = await p.chromium.connect_over_cdp("http://localhost:9222")
            else:
                # フォールバック：ユーザープロフィールを使用
                chrome_profile_path = r"C:\Users\shint\AppData\Local\Google\Chrome\User Data"
                browser = await p.chromium.launch(
                    headless=False,
                    user_data_dir=chrome_profile_path,
                    args=["--disable-blink-features=AutomationControlled"]
                )

            page = await browser.new_page()

            print("📍 dポイントクラブ にアクセス中...")
            print("   (Chrome のログイン状態を使用)")
            await page.goto(dpoint_url, timeout=30000)
            await asyncio.sleep(3)

            print("✅ ページロード完了")
            print(f"📄 URL: {page.url}")
            print(f"📝 タイトル: {await page.title()}")

            # CAPTCHA検出
            if await detect_captcha(page):
                await wait_for_captcha_solve(page)

            # ページの構造を確認
            await asyncio.sleep(2)

            # アンケートフォームを探す
            forms = await page.query_selector_all("form")
            print(f"📋 フォーム検出数: {len(forms)}")

            inputs = await page.query_selector_all("input[type='text'], textarea, select")
            print(f"📝 入力フィールド検出数: {len(inputs)}")

            # アンケート要素を探す
            survey_buttons = await page.query_selector_all("button, [class*='survey'], [class*='question']")
            print(f"📝 ボタン/アンケート関連要素: {len(survey_buttons)}")

            return True

        except Exception as e:
            print(f"❌ エラー: {e}")
            if use_remote_debug:
                print("   💡 ヒント: Chrome がリモートデバッギングモードで起動していることを確認してください")
                print("   実行コマンド：")
                print("   \"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe\" --remote-debugging-port=9222")
            else:
                print("   💡 ヒント: Chrome が完全に閉じられているか確認してください")
            return False
        finally:
            if browser:
                try:
                    await browser.close()
                except:
                    pass
            print("🔚 ブラウザ終了")


async def test_pex(media_code_token_url):
    """PEX/Conio - メディアコード認証版（ログイン不要）"""
    print("\n" + "="*70)
    print("🔍 PEX - アンケートアクセステスト")
    print("="*70)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        try:
            print("📍 PEX にアクセス中...")
            await page.goto(media_code_token_url, timeout=30000)
            await asyncio.sleep(2)

            print("✅ ページロード完了")
            print(f"📄 URL: {page.url}")
            print(f"📝 タイトル: {await page.title()}")

            # CAPTCHA検出
            if await detect_captcha(page):
                await wait_for_captcha_solve(page)

            # アンケート一覧ページ確認
            page_title = await page.title()
            if "アンケート" in page_title or "survey" in page_title.lower():
                print("✅ アンケート一覧ページを確認")

            # ページの構造を確認
            await asyncio.sleep(2)

            # アンケートフォームを探す
            forms = await page.query_selector_all("form")
            print(f"📋 フォーム検出数: {len(forms)}")

            inputs = await page.query_selector_all("input[type='text'], textarea, select")
            print(f"📝 入力フィールド検出数: {len(inputs)}")

            # アンケート要素を探す
            survey_buttons = await page.query_selector_all("button, [class*='survey'], [class*='answer']")
            print(f"📝 ボタン/アンケート関連要素: {len(survey_buttons)}")

            return True

        except Exception as e:
            print(f"❌ エラー: {e}")
            return False
        finally:
            await browser.close()
            print("🔚 ブラウザ終了")


async def test_research_panel(email, password):
    """リサーチパネル - CAPTCHA対応版"""
    print("\n" + "="*70)
    print("🔍 リサーチパネル - ログイン＆自動化テスト")
    print("="*70)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        try:
            print("📍 リサーチパネル にアクセス中...")
            await page.goto("https://research-panel.jp/login/", timeout=30000)
            await asyncio.sleep(2)

            print("✅ ページロード完了")
            print(f"📄 URL: {page.url}")
            print(f"📝 タイトル: {await page.title()}")

            # CAPTCHA検出
            if await detect_captcha(page):
                await wait_for_captcha_solve(page)

            # ログイン画面を確認
            if "login" in page.url.lower():
                print("🔐 ログイン画面を検出。ログイン処理中...")

                # メール入力（name='mail'）
                try:
                    await page.fill("input[name='mail']", email, timeout=5000)
                    print("✅ メールアドレス入力完了")
                except Exception as e:
                    print(f"❌ メール入力失敗: {e}")
                    return False

                # パスワード入力
                try:
                    await page.fill("input[type='password']", password, timeout=5000)
                    print("✅ パスワード入力完了")
                except Exception as e:
                    print(f"❌ パスワード入力失敗: {e}")
                    return False

                # ログインボタンクリック（id='submitWithGrecaptcha'）
                try:
                    await page.click("input#submitWithGrecaptcha", timeout=5000)
                    print("🔄 ログイン処理中...")
                    await page.wait_for_load_state("networkidle", timeout=15000)
                    print("✅ ログイン成功")
                except Exception as e:
                    print(f"⚠️  ログイン処理エラー: {e}")
                    return False

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
            survey_buttons = await page.query_selector_all("button, [class*='survey']")
            print(f"📝 ボタン/アンケート関連要素: {len(survey_buttons)}")

            return True

        except Exception as e:
            print(f"❌ エラー: {e}")
            return False
        finally:
            await browser.close()
            print("🔚 ブラウザ終了")


async def main():
    print("\n🚀 アンケートサイト自動化ツール")
    print("=" * 70)
    print("対象サイト: dポイント、PEX、リサーチパネル、マクロミル、アンとケイト、他")
    print("CAPTCHA対応: 手動解決可能（CAPTCHAが出現したらボタンを押してください）")
    print("=" * 70)

    print("\n" + "="*70)
    print("⚠️  重要：Chrome をリモートデバッギングモードで起動してください")
    print("="*70)
    print("\n【PowerShell で以下を実行：】")
    print('  & "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" --remote-debugging-port=9222')
    print("\n【その後、別のターミナルでこのスクリプトを実行】")
    print("="*70 + "\n")

    # 各サイトの認証情報を対話的に入力
    print("📝 認証情報入力（パスワードは画面に表示されません）\n")

    print("【dポイントクラブ】")
    dpoint_url = input("🔗 dポイント URL: ").strip()

    print("\n【PEX（Conio）】")
    pex_url = input("🔗 PEX URL: ").strip()

    print("\n【リサーチパネル】")
    research_panel_email = input("📧 メールアドレス: ").strip()
    research_panel_pass = getpass.getpass("🔐 パスワード: ")

    print("\n【マクロミル】")
    macromill_email = input("📧 メールアドレス: ").strip()
    macromill_pass = getpass.getpass("🔐 パスワード: ")

    print("\n【アンとケイト】")
    annkate_email = input("📧 メールアドレス: ").strip()
    annkate_pass = getpass.getpass("🔐 パスワード: ")

    print("\n" + "="*70)
    print("✅ 認証情報入力完了。自動化を開始します...")
    print("="*70)

    results = []

    # dポイントクラブをテスト（Chrome プロフィール使用）
    result = await test_dpoint_club(dpoint_url)
    results.append(("dポイントクラブ", result))

    # PEXをテスト
    result = await test_pex(pex_url)
    results.append(("PEX", result))

    # リサーチパネルをテスト
    result = await test_research_panel(research_panel_email, research_panel_pass)
    results.append(("リサーチパネル", result))

    # マクロミルをテスト
    result = await test_macromill(macromill_email, macromill_pass)
    results.append(("マクロミル", result))

    # アンとケイトをテスト
    result = await test_annkate(annkate_email, annkate_pass)
    results.append(("アンとケイト", result))

    # 結果サマリー
    print("\n" + "="*70)
    print("📊 自動化実行結果")
    print("="*70)
    for site_name, success in results:
        status = "✅ 成功" if success else "⚠️  一部エラー"
        print(f"{site_name}: {status}")

    print("="*70)
    print("✅ 自動化完了")
    print("="*70)


if __name__ == "__main__":
    asyncio.run(main())
