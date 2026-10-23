import importlib.util
import tempfile
import unittest
from pathlib import Path
from subprocess import run
import sys

spec = importlib.util.spec_from_file_location("origins", Path(__file__).with_name("configure-live-origin.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class OriginConfigurationTest(unittest.TestCase):
    def test_preserves_other_values_and_deduplicates(self):
        text = "SECRET=never-print-me\nAPP_ALLOWED_ORIGINS=https://old.invalid,https://old.invalid\nAPP_PORT=18081\n"
        result = module.updated(text)
        self.assertIn("SECRET=never-print-me\n", result)
        self.assertIn("APP_PORT=18081\n", result)
        self.assertIn("APP_ALLOWED_ORIGINS=https://old.invalid," + module.ORIGIN + "\n", result)
        self.assertEqual(result, module.updated(result))

    def test_quoted_and_missing_key(self):
        self.assertIn("APP_ALLOWED_ORIGINS=https://old.invalid," + module.ORIGIN,
                      module.updated('export APP_ALLOWED_ORIGINS="https://old.invalid"\n'))
        self.assertEqual(module.updated("A=1\n"), "A=1\nAPP_ALLOWED_ORIGINS="+module.ORIGIN+"\n")

    def test_ambiguous_duplicate_and_comment_are_rejected(self):
        for text in ["APP_ALLOWED_ORIGINS=a\nAPP_ALLOWED_ORIGINS=b\n",
                     "APP_ALLOWED_ORIGINS=a # comment\n", "APP_ALLOWED_ORIGINS=${OTHER}\n"]:
            with self.assertRaises(ValueError):
                module.updated(text)

    def test_dry_run_never_writes_and_apply_preserves_secret(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parent) as temp:
            path=Path(temp)/".env"
            original="SECRET=never-print-me\nAPP_ALLOWED_ORIGINS=https://old.invalid\n"
            path.write_text(original,encoding="utf-8")
            command=[sys.executable,str(Path(__file__).with_name("configure-live-origin.py")),"--file",str(path)]
            checked=run(command,capture_output=True,text=True)
            self.assertEqual(checked.returncode,0,checked.stderr)
            self.assertEqual(path.read_text(),original)
            applied=run(command+["--apply"],capture_output=True,text=True)
            self.assertEqual(applied.returncode,0,applied.stderr)
            self.assertNotIn("never-print-me",checked.stdout+applied.stdout)
            self.assertEqual(path.read_text(),module.updated(original))

if __name__=="__main__":
    unittest.main()
