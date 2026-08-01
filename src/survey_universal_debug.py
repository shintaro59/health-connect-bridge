#!/usr/bin/env python3
"""
汎用アンケートサイト詳細デバッグ - 任意のサイトをテスト
各サイトのログインフォーム要素をスキャンしてセレクタを特定
"""

import asyncio
from playwright.async_api import async_playwright
import getpass
import sys

async def debug_survey_site(site_name, site_url, email, password):
    """汎用的なサイトデバッグ"""
    print("\n" + "="*80)
    print(f"🔍 {site_name} - 詳細デバッグ")
    print("="*80)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        try:
            print(f"📍 {site_name} にアクセス中...")
            print(f"   URL: {site_url}")
            await page.goto(site_url, timeout=30000)
            await asyncio.sleep(3)

            print("\n✅ ページロード完了")
            print(f"📄 現在のURL: {page.url}")
            print(f"📝 タイトル: {await page.title()}")

            # CAPTCHA検出
            print("\n【CAPTCHA検出】")
            captcha_selectors = [
                'iframe[src*="captcha"]',
                'iframe[src*="recaptcha"]',
                'div[class*="g-recaptcha"]',
                'div[class*="cloudflare"]',
                'button[class*="g-recaptcha"]',
            ]
            captcha_found = False
            for selector in captcha_selectors:
                try:
                    elem = await page.query_selector(selector)
                    if elem:
                        print(f"  ⚠️  CAPTCHA検出: {selector}")
                        captcha_found = True
                except:
                    pass

            if not captcha_found:
                print("  ✅ CAPTCHA検出なし")

            # すべてのinput要素をスキャン
            print("\n【入力フィールド分析】")
            inputs = await page.query_selector_all("input")
            print(f"入力フィールド数: {len(inputs)}")

            for i, inp in enumerate(inputs):
                try:
                    inp_type = await inp.get_attribute("type")
                    inp_name = await inp.get_attribute("name")
                    inp_id = await inp.get_attribute("id")
                    inp_placeholder = await inp.get_attribute("placeholder")
                    inp_class = await inp.get_attribute("class")

                    print(f"\n  フィールド {i+1}:")
                    print(f"    type: {inp_type if inp_type else '(なし)'}")
                    print(f"    name: {inp_name if inp_name else '(なし)'}")
                    print(f"    id: {inp_id if inp_id else '(なし)'}")
                    print(f"    placeholder: {inp_placeholder if inp_placeholder else '(なし)'}")
                    print(f"    class: {inp_class if inp_class else '(なし)'}")

                    # 推奨セレクタを提案
                    if inp_type == "email":
                        print(f"    💡 メール入力セレクタ: input[type='email']")
                    elif inp_type == "password":
                        print(f"    💡 パスワード入力セレクタ: input[type='password']")
                    elif inp_type == "text" and inp_placeholder and "mail" in inp_placeholder.lower():
                        print(f"    💡 メール入力セレクタ: input[placeholder='{inp_placeholder}']")
                    if inp_name:
                        print(f"    💡 代替セレクタ: input[name='{inp_name}']")
                    if inp_id:
                        print(f"    💡 代替セレクタ: input#{inp_id}")

                except Exception as e:
                    print(f"    エラー: {e}")

            # すべてのボタンをスキャン
            print("\n【ボタン分析】")
            buttons = await page.query_selector_all("button, input[type='submit'], input[type='button'], a[role='button']")
            print(f"ボタン数: {len(buttons)}")

            for i, btn in enumerate(buttons):
                try:
                    btn_tag = await page.evaluate('el => el.tagName', btn)
                    btn_text = await btn.text_content()
                    btn_type = await btn.get_attribute("type")
                    btn_class = await btn.get_attribute("class")
                    btn_id = await btn.get_attribute("id")
                    btn_name = await btn.get_attribute("name")

                    print(f"\n  ボタン {i+1}:")
                    print(f"    タグ: {btn_tag}")
                    print(f"    テキスト: {btn_text.strip() if btn_text else '(なし)'}")
                    print(f"    type: {btn_type if btn_type else '(なし)'}")
                    print(f"    class: {btn_class if btn_class else '(なし)'}")
                    print(f"    id: {btn_id if btn_id else '(なし)'}")
                    print(f"    name: {btn_name if btn_name else '(なし)'}")

                    # 推奨セレクタを提案
                    if "ログイン" in (btn_text or "") or "login" in (btn_text or "").lower():
                        if btn_class:
                            print(f"    💡 ログインボタンセレクタ: button.{btn_class.split()[0]}")
                        if btn_id:
                            print(f"    💡 ログインボタンセレクタ: #{btn_id}")
                        if btn_type:
                            print(f"    💡 ログインボタンセレクタ: button[type='{btn_type}']")
                        else:
                            print(f"    💡 ログインボタンセレクタ: button:has-text('ログイン')")

                except Exception as e:
                    print(f"    エラー: {e}")

            # チェックボックス検出
            print("\n【チェックボックス】")
            checkboxes = await page.query_selector_all("input[type='checkbox']")
            print(f"チェックボックス数: {len(checkboxes)}")
            for i, cb in enumerate(checkboxes):
                try:
                    cb_id = await cb.get_attribute("id")
                    cb_name = await cb.get_attribute("name")
                    label = await page.evaluate('(el) => el.closest("label")?.textContent || ""', cb)
                    print(f"  {i+1}. {label.strip() if label else '(ラベルなし)'}")
                    if cb_id:
                        print(f"     セレクタ: input#{cb_id}")
                    if cb_name:
                        print(f"     セレクタ: input[name='{cb_name}']")
                except:
                    pass

            # フォーム検出
            print("\n【フォーム】")
            forms = await page.query_selector_all("form")
            print(f"フォーム数: {len(forms)}")
            for i, form in enumerate(forms):
                form_id = await form.get_attribute("id")
                form_name = await form.get_attribute("name")
                form_action = await form.get_attribute("action")
                print(f"  フォーム {i+1}:")
                if form_id:
                    print(f"    id: {form_id}")
                if form_name:
                    print(f"    name: {form_name}")
                if form_action:
                    print(f"    action: {form_action}")

            # スクリーンショット保存
            screenshot_name = f"screenshot_{site_name}_debug.png"
            print(f"\n📸 ページスクリーンショット: {screenshot_name} に保存")
            await page.screenshot(path=screenshot_name)

            await asyncio.sleep(2)

        except Exception as e:
            print(f"\n❌ エラー: {e}")
        finally:
            await browser.close()
            print("\n🔚 ブラウザ終了")


async def main():
    print("\n🚀 汎用アンケートサイト詳細デバッグツール")
    print("="*80)

    # サイト情報入力
    print("\n【サイト情報入力】")
    site_name = input("📌 サイト名: ").strip()
    site_url = input("🔗 ログインページURL: ").strip()
    email = input("📧 メールアドレス: ").strip()
    password = getpass.getpass("🔐 パスワード: ")

    print("\n" + "="*80)
    print("✅ 入力完了。デバッグを開始します...")
    print("="*80)

    await debug_survey_site(site_name, site_url, email, password)

    print("\n" + "="*80)
    print("✅ デバッグ完了")
    print("="*80)
    print("\n📋 上記の情報から、以下のセレクタを確認してください：")
    print("  1. メール入力フィールドセレクタ")
    print("  2. パスワード入力フィールドセレクタ")
    print("  3. ログインボタンセレクタ")
    print("  4. CAPTCHA有無")
    print("\nこれらを記録して、自動化スクリプトに追加します。")


if __name__ == "__main__":
    asyncio.run(main())
