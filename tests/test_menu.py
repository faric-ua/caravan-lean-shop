"""Меню телефона: пункти 1–14 є, кожен веде на наявний скрипт, номери не зсуваються."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MENU = (ROOT / "menu.sh").read_text(encoding="utf-8")

# Номер → скрипт. Номери 1–13 — як у Track Catcher (однаково в усіх репо faric-ua). Нове — лише 15, 16…
FIXED = {
    2: "cl-install-latest.sh", 3: "cl-status.sh", 4: "cl-open-folder.sh", 5: "cl-install-phone-build.sh",
    7: "cl-signing-setup.sh", 8: "cl-signing-backup.sh", 9: "cl-widget.sh", 10: "cl-reopen-install.sh",
    11: "cl-local-build.sh", 13: "cl-open-apk-folder.sh", 14: "cl-catalog.sh",
}


class MenuTest(unittest.TestCase):
    def test_numbers_shown_and_handled(self):
        for n in range(1, 15):
            self.assertRegex(MENU, rf'echo " +{n} — ', f"пункт {n} не показано")
            self.assertRegex(MENU, rf"\n\s+{n}\)", f"пункт {n} не обробляється")

    def test_numbers_point_to_scripts(self):
        for n, script in FIXED.items():
            self.assertRegex(MENU, rf"\n\s+{n}\) run_tool {re.escape(script)} ;;", f"пункт {n} → {script}")
            self.assertTrue((ROOT / "tools/termux" / script).is_file(), script)

    def test_scripts_use_this_repo(self):
        common = (ROOT / "tools/termux/cl-common.sh").read_text(encoding="utf-8")
        self.assertIn('CL_SLUG="faric-ua/caravan-lean-shop"', common)
        for f in (ROOT / "tools/termux").glob("*.sh"):
            text = f.read_text(encoding="utf-8")
            self.assertNotIn("track-catcher", text.lower(), f.name)
            self.assertNotIn("reset --hard", text, f.name)

    def test_widget_matches_repo_folder(self):
        w = (ROOT / "termux-widget/Caravan Lean").read_text(encoding="utf-8")
        self.assertIn('PROJECT="$HOME/caravan-lean-shop"', w)


if __name__ == "__main__":
    unittest.main()
