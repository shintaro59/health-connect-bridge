#!/usr/bin/env python3
import json
from datetime import datetime
from collections import defaultdict

def analyze_monthly_spending(data_file='spending_data.json'):
    with open(data_file, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Group spending by category
    category_spending = defaultdict(float)

    for transaction in data['transactions']:
        category = transaction['category']
        amount = transaction['amount']
        category_spending[category] += amount

    # Calculate total
    total_spending = sum(category_spending.values())

    # Sort by amount (descending)
    sorted_categories = sorted(category_spending.items(), key=lambda x: x[1], reverse=True)

    # Display results
    print("=" * 60)
    print("【2026年7月 月間支出分析】")
    print("=" * 60)
    print()

    for category, amount in sorted_categories:
        percentage = (amount / total_spending) * 100 if total_spending > 0 else 0
        bar_length = int(percentage / 2)  # Scale percentage for bar display
        bar = "█" * bar_length
        print(f"{category:12} | {amount:>6,}円 | {percentage:>6.1f}% | {bar}")

    print("-" * 60)
    print(f"{'合計':12} | {total_spending:>6,.0f}円 | {'100.0%':>6}")
    print("=" * 60)

    # Return structured data
    return {
        'total': total_spending,
        'categories': [
            {
                'name': category,
                'amount': amount,
                'percentage': round((amount / total_spending) * 100, 1) if total_spending > 0 else 0
            }
            for category, amount in sorted_categories
        ]
    }

if __name__ == '__main__':
    result = analyze_monthly_spending()
    print()
    print("JSON形式の結果:")
    print(json.dumps(result, ensure_ascii=False, indent=2))
