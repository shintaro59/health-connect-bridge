#!/usr/bin/env python3
"""
アンケートサイト詳細デバッグ - ページ要素を詳しく調査
ECナビとアンとケイトのログイン問題を診断
"""

import asyncio
from playwright.async_api import async_playwright
import getpass

async def debug_ecnavi(email, password):
    """ECナビの詳細デバッグ"""
    print("\n" + "="*70)
    print("🔍 ECナビ - 詳細デバッグ（ページ要素の確認）")
    print("="*70)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        try:
            print("📍 ECナビ にアクセス中...")
            await page.goto("https://ecnavi.jp/research/", timeout=30000)
            await asyncio.sleep(2)

            print("\n✅ ページロード完了")
            print(f"📄 URL: {page.url}")

            # メール入力
            print("\n【メールアドレス入力】")
            try:
                await page.fill("input[type='email']", email, timeout=5000)
                print("✅ 入力完了")
            except Exception as e:
                print(f"❌ エラー: {e}")

            # パスワード入力
            print("\n【パスワード入力】")
            try:
                await page.fill("input[type='password']", password, timeout=5000)
                print("✅ 入力完了")
            except Exception as e:
                print(f"❌ エラー: {e}")

            await asyncio.sleep(1)

            # ページ上のすべてのボタンを調査
            print("\n【ページ上のボタン分析】")
            buttons = await page.query_selector_all("button, input[type='submit'], input[type='button']")
            print(f"検出されたボタン数: {len(buttons)}")

            for i, btn in enumerate(buttons):
                try:
                    text = await btn.text_content()
                    btn_type = await btn.get_attribute("type")
                    btn_class = await btn.get_attribute("class")
                    is_disabled = await btn.get_attribute("disabled")

                    print(f"\n  ボタン {i+1}:")
                    print(f"    テキスト: {text.strip() if text else '(なし)'}")
                    print(f"    type: {btn_type}")
                    print(f"    class: {btn_class}")
                    print(f"    disabled: {is_disabled if is_disabled else 'なし'}")
                except Exception as e:
                    print(f"    エラー: {e}")

            # チェックボックス等の確認
            print("\n【チェックボックス・その他の要素】")
            checkboxes = await page.query_selector_all("input[type='checkbox']")
            print(f"チェックボックス数: {len(checkboxes)}")
            for i, cb in enumerate(checkboxes):
                label_text = await page.evaluate(f'(el) => el.closest("label")?.textContent || el.nextElementSibling?.textContent || "unknown"', cb)
                is_checked = await cb.is_checked()
                print(f"  {i+1}. {label_text.strip()} (checked: {is_checked})")

            # 利用規約チェックボックスを探す
            print("\n【利用規約チェックボックスの確認】")
            terms_checkboxes = await page.query_selector_all("input[type='checkbox']")
            for cb in terms_checkboxes:
                label = await page.evaluate('(el) => el.closest("label")?.textContent || ""', cb)
                if "規約" in label or "同意" in label or "確認" in label:
                    print(f"📍 規約同意のチェックボックスを検出: {label.strip()}")
                    print(f"   現在の状態: {'☑️ チェック済み' if await cb.is_checked() else '☐ 未チェック'}")

                    if not await cb.is_checked():
                        print("   ✓ チェックボックスをチェック中...")
                        await cb.check()
                        print("   ✅ チェック完了")
                        await asyncio.sleep(1)

            # ボタン再確認
            print("\n【チェック後のボタン状態】")
            button_submit = await page.query_selector("button[type='submit']")
            if button_submit:
                is_disabled = await button_submit.get_attribute("disabled")
                print(f"ログインボタンの状態: {'❌ disabled' if is_disabled else '✅ enabled'}")

            print("\n📸 ページスクリーンショット: screenshot_ecnavi_debug.png に保存")
            await page.screenshot(path="screenshot_ecnavi_debug.png")

            await asyncio.sleep(3)

        except Exception as e:
            print(f"\n❌ エラー: {e}")
        finally:
            await browser.close()
            print("\n🔚 ブラウザ終了")


async def debug_annkate(email, password):
    """アンとケイトの詳細デバッグ"""
    print("\n" + "="*70)
    print("🔍 アンとケイト - 詳細デバッグ（ボタン要素の確認）")
    print("="*70)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        try:
            print("📍 アンとケイト にアクセス中...")
            await page.goto("https://www.ann-kate.jp/login", timeout=30000)
            await asyncio.sleep(2)

            print("\n✅ ページロード完了")
            print(f"📄 URL: {page.url}")

            # メール入力
            print("\n【メールアドレス入力】")
            try:
                # セレクタを試す
                selectors = [
                    "input[type='email']",
                    "input[name*='mail']",
                    "input[id*='mail']",
                    "input[placeholder*='mail'], input[placeholder*='Mail']"
                ]

                for selector in selectors:
                    try:
                        elem = await page.query_selector(selector)
                        if elem:
                            await elem.fill(email)
                            print(f"✅ 入力完了 (セレクタ: {selector})")
                            break
                    except:
                        pass
            except Exception as e:
                print(f"❌ エラー: {e}")

            # パスワード入力
            print("\n【パスワード入力】")
            try:
                await page.fill("input[type='password']", password, timeout=5000)
                print("✅ 入力完了")
            except Exception as e:
                print(f"❌ エラー: {e}")

            await asyncio.sleep(1)

            # ページ上のすべてのボタンを調査
            print("\n【ページ上のボタン分析】")
            buttons = await page.query_selector_all("button, input[type='submit'], input[type='button'], a[role='button']")
            print(f"検出されたボタン数: {len(buttons)}")

            for i, btn in enumerate(buttons):
                try:
                    text = await btn.text_content()
                    btn_tag = await page.evaluate('el => el.tagName', btn)
                    btn_type = await btn.get_attribute("type")
                    btn_class = await btn.get_attribute("class")
                    btn_id = await btn.get_attribute("id")

                    print(f"\n  ボタン {i+1}:")
                    print(f"    タグ: {btn_tag}")
                    print(f"    テキスト: {text.strip() if text else '(なし)'}")
                    print(f"    type: {btn_type if btn_type else '(なし)'}")
                    print(f"    id: {btn_id if btn_id else '(なし)'}")
                    print(f"    class: {btn_class if btn_class else '(なし)'}")
                except Exception as e:
                    print(f"    エラー: {e}")

            print("\n📸 ページスクリーンショット: screenshot_annkate_debug.png に保存")
            await page.screenshot(path="screenshot_annkate_debug.png")

            await asyncio.sleep(3)

        except Exception as e:
            print(f"\n❌ エラー: {e}")
        finally:
            await browser.close()
            print("\n🔚 ブラウザ終了")


async def main():
    print("\n🚀 アンケートサイト詳細デバッグツール起動")
    print("="*70)

    # ECナビのデバッグ
    print("\n【ECナビ】")
    ecnavi_email = input("📧 メールアドレス: ").strip()
    ecnavi_pass = getpass.getpass("🔐 パスワード: ")

    await debug_ecnavi(ecnavi_email, ecnavi_pass)

    # アンとケイトのデバッグ
    print("\n【アンとケイト】")
    annkate_email = input("📧 メールアドレス: ").strip()
    annkate_pass = getpass.getpass("🔐 パスワード: ")

    await debug_annkate(annkate_email, annkate_pass)

    print("\n" + "="*70)
    print("✅ デバッグ完了")
    print("=" * 70)
    print("\n📸 スクリーンショットが保存されました：")
    print("  - screenshot_ecnavi_debug.png")
    print("  - screenshot_annkate_debug.png")


if __name__ == "__main__":
    asyncio.run(main())
