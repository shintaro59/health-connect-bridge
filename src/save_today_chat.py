#!/usr/bin/env python3
"""
Save today's chat session to Markdown file.
Run this before closing the session.
"""

import sys
from datetime import datetime
from pathlib import Path
from daily_chat_manager import DailyChatManager


def main():
    """
    Interactive script to save chat session to file.
    User can paste session chat and it will be saved to database/.
    """
    manager = DailyChatManager()
    today_file = manager.get_today_file()

    print("=" * 60)
    print("Daily Chat Saver")
    print("=" * 60)
    print()
    print(f"Today's file: {today_file}")
    print()
    print("Paste your session chat below.")
    print("Press Ctrl+D (or Ctrl+Z on Windows) when done.")
    print()
    print("-" * 60)

    try:
        # Read from stdin until EOF
        lines = []
        while True:
            try:
                line = input()
                lines.append(line)
            except EOFError:
                break

        if not lines:
            print("-" * 60)
            print("No input received. Exiting without saving.")
            return

        chat_content = "\n".join(lines)

        # Save to file
        with open(today_file, "a", encoding="utf-8") as f:
            f.write(chat_content)
            f.write("\n\n")

        print("-" * 60)
        print("✓ Chat session saved successfully!")
        print(f"Saved to: {today_file}")
        print()

        # Show file size
        file_size = today_file.stat().st_size
        print(f"File size: {file_size} bytes")
        print()

    except KeyboardInterrupt:
        print()
        print("Cancelled by user.")
        return
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
