#!/usr/bin/env python3
"""Аудит інтерфейсу Caravan Lean (правила як у Track Catcher, docs/UI_STYLE_CONTRACT.md там).

UI-01 ніяких стандартних сірих діалогів/кнопок/тостів Android; UI-02 кольори лише в Palette.kt;
UI-03 без галочок; UI-04 зона дотику кнопок-значків ≥ 48 dp; UI-06 орієнтацію не блокуємо.
Запуск: python3 scripts/audit_ui.py [--root ШЛЯХ]
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def audit(root: Path) -> list[tuple[str, str]]:
    problems: list[tuple[str, str]] = []
    sources = sorted((root / "android/app/src/main/java").rglob("*.kt"))
    if not sources:
        return [("UI-00", "не знайдено жодного .kt")]
    for path in sources:
        rel = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8")
        for pat in (r"AlertDialog", r"import android\.widget\.Button\b", r"ProgressDialog", r"Toast\.makeText"):
            if re.search(pat, text):
                problems.append(("UI-01", f"{rel}: стандартний елемент Android ({pat})"))
        if path.name != "Palette.kt":
            for m in re.finditer(r"0x[0-9A-Fa-f]{8}|Color\.parseColor|Color\.rgb\(|Color\.argb\(", text):
                problems.append(("UI-02", f"{rel}:{text.count(chr(10), 0, m.start()) + 1}: колір поза Palette.kt ({m.group(0)})"))
        for ch in ("✓", "✔", "☑"):
            if ch in text:
                problems.append(("UI-03", f"{rel}: галочка «{ch}»"))
        if "setRequestedOrientation" in text:
            problems.append(("UI-06", f"{rel}: блокування орієнтації"))
    kit = (root / "android/app/src/main/java/ua/faric/caravanlean/ui/Ui.kt").read_text(encoding="utf-8")
    m = re.search(r"fun iconButton\(.*?val size = dp\((\d+)\)", kit, re.S)
    if not m or int(m.group(1)) < 48:
        problems.append(("UI-04", "Ui.iconButton: зона дотику має бути ≥ 48 dp"))
    return problems


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=str(Path(__file__).resolve().parents[1]))
    problems = audit(Path(ap.parse_args().root))
    print("Аудит інтерфейсу Caravan Lean:", "PASS" if not problems else "FAIL")
    for rule, msg in problems:
        print(f"::error title={rule}::{msg}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
