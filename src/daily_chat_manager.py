#!/usr/bin/env python3
"""
Daily chat manager for Obsidian logging.
- Saves chat history to Markdown files by date
- Loads past conversations for context
"""

from pathlib import Path
from datetime import datetime, timedelta
import re

class DailyChatManager:
    def __init__(self, db_folder: str = "database"):
        self.db_folder = Path(db_folder)
        self.db_folder.mkdir(exist_ok=True)

    def get_today_file(self) -> Path:
        """Get today's chat file path"""
        today = datetime.now().strftime("%Y-%m-%d")
        return self.db_folder / f"{today}_chat.md"

    def get_yesterday_file(self) -> Path:
        """Get yesterday's chat file path"""
        yesterday = (datetime.now() - timedelta(days=1)).strftime("%Y-%m-%d")
        return self.db_folder / f"{yesterday}_chat.md"

    def prepare_yesterday_file(self) -> bool:
        """
        Prepare yesterday's file if it doesn't exist.
        Called by Routine at 4 AM JST.
        Returns True if preparation was done.
        """
        yesterday_file = self.get_yesterday_file()
        today_file = self.get_today_file()

        # If today's file exists but yesterday's doesn't, create it
        if today_file.exists() and not yesterday_file.exists():
            today_date = today_file.name.split("_")[0]
            yesterday_date = yesterday_file.name.split("_")[0]

            # Read today's content (which should be yesterday's)
            with open(today_file, "r", encoding="utf-8") as f:
                content = f.read()

            # Save as yesterday's file
            with open(yesterday_file, "w", encoding="utf-8") as f:
                f.write(content)

            # Clear today's file
            with open(today_file, "w", encoding="utf-8") as f:
                f.write(f"# {datetime.now().strftime('%Y-%m-%d')} - Daily Chat\n\n")

            return True

        # If neither file exists, create today's file
        if not today_file.exists():
            with open(today_file, "w", encoding="utf-8") as f:
                f.write(f"# {datetime.now().strftime('%Y-%m-%d')} - Daily Chat\n\n")
            return False

        return False

    def load_all_past_conversations(self) -> str:
        """Load all past conversations except today"""
        today_file = self.get_today_file()
        past_content = []

        # Get all chat files, sorted
        for file in sorted(self.db_folder.glob("*_chat.md")):
            if file == today_file:
                continue

            with open(file, "r", encoding="utf-8") as f:
                past_content.append(f.read())

        if not past_content:
            return ""

        return "\n\n---\n\n".join(past_content)

    def load_today_conversation(self) -> str:
        """Load today's conversation so far"""
        today_file = self.get_today_file()

        if today_file.exists():
            with open(today_file, "r", encoding="utf-8") as f:
                return f.read()

        return f"# {datetime.now().strftime('%Y-%m-%d')} - Daily Chat\n\n"

    def save_user_message(self, message: str) -> None:
        """Save user message to today's file"""
        today_file = self.get_today_file()

        with open(today_file, "a", encoding="utf-8") as f:
            f.write(f"**User:** {message}\n\n")

    def save_assistant_message(self, message: str) -> None:
        """Save assistant message to today's file"""
        today_file = self.get_today_file()

        with open(today_file, "a", encoding="utf-8") as f:
            f.write(f"**Claude:** {message}\n\n")

    def get_context_for_response(self) -> str:
        """
        Get context string with all past conversations for Claude prompt.
        Returns formatted string with past and today's conversations.
        """
        past = self.load_all_past_conversations()
        today = self.load_today_conversation()

        context = "## Past Conversations\n\n"
        if past:
            context += past
        else:
            context += "(This is the beginning of our conversations)\n"

        context += "\n\n## Today's Conversation\n\n"
        context += today

        return context


def initialize_database():
    """Initialize database folder and files"""
    manager = DailyChatManager()
    manager.prepare_yesterday_file()
    print(f"Database initialized. Today's file: {manager.get_today_file()}")


if __name__ == "__main__":
    initialize_database()
