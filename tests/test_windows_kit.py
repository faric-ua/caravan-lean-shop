"""Меню Windows для нового сайту (docs/new-site/windows-kit): номери не зсуваються, BOM для PowerShell 5.1, без секретів.
Сама інструкція (NEW_SITE_GUIDE.md) — не в цьому публічному репо: її віддано файлом (містить опис слабких місць живого сайту)."""
import re
import unittest
from pathlib import Path

KIT = Path(__file__).resolve().parent.parent / "docs" / "new-site" / "windows-kit"


class WindowsKitTest(unittest.TestCase):
    def test_menu_numbers(self):
        menu = (KIT / "menu.ps1").read_text(encoding="utf-8-sig")
        shown = set(re.findall(r"Write-Host ['\"] +(\d+) — ", menu)) - {"0"}
        handled = set(re.findall(r"\n\s+'(\d+)' \{", menu))
        expected = {str(n) for n in range(1, 18)}
        self.assertEqual(shown, expected)
        self.assertEqual(handled, expected)
        self.assertIn("$Valid = 1..17", menu)

    def test_tools_exist(self):
        menu = (KIT / "menu.ps1").read_text(encoding="utf-8-sig")
        for t in set(re.findall(r"Tool '([a-z-]+\.ps1)'", menu)):
            self.assertTrue((KIT / "tools" / t).is_file(), t)

    def test_bom_for_windows_powershell(self):
        for p in list(KIT.rglob("*.ps1")) + list(KIT.rglob("*.psd1")):
            self.assertTrue(p.read_bytes().startswith(b"\xef\xbb\xbf"), f"{p.name}: потрібен UTF-8 BOM")
        self.assertFalse((KIT / "menu.cmd").read_bytes().startswith(b"\xef\xbb\xbf"), "menu.cmd — без BOM")

    def test_backup_requires_done_marker(self):
        backup = (KIT / "tools" / "backup.ps1").read_text(encoding="utf-8-sig")
        deploy = (KIT / "tools" / "deploy.ps1").read_text(encoding="utf-8-sig")
        self.assertIn("DONE.txt", backup)
        self.assertIn("Dump completed", backup)
        self.assertIn("DONE.txt", deploy)

    def test_no_secrets_in_kit(self):
        for p in KIT.rglob("*"):
            if p.is_file():
                t = p.read_text(encoding="utf-8-sig", errors="ignore")
                self.assertNotRegex(t, r"(?i)password\s*=\s*['\"][^'\"]{6,}['\"]", p.name)


if __name__ == "__main__":
    unittest.main()
