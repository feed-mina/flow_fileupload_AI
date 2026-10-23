"""Run on the Hostinger VPS. Preserve existing origins and unrelated .env values."""
import argparse
import os
import re
import tempfile
from pathlib import Path

ORIGIN = "https://flow-fileupload-ai.vercel.app"
KEY = "APP_ALLOWED_ORIGINS"

def updated(text):
    lines = text.splitlines()
    indices = [i for i, line in enumerate(lines) if re.match(r"^\s*(?:export\s+)?" + KEY + r"\s*=", line)]
    if len(indices) > 1:
        raise ValueError("Duplicate APP_ALLOWED_ORIGINS: resolve explicitly before continuing")
    value = lines[indices[0]].split("=", 1)[1].strip() if indices else ""
    if value.startswith(("'", '"')):
        if len(value) < 2 or value[-1] != value[0]:
            raise ValueError("Unsupported quoted value; review .env without printing secrets")
        value = value[1:-1]
    elif "#" in value or "$" in value:
        raise ValueError("Inline comments/interpolation require explicit review")
    origins = list(dict.fromkeys(x.strip() for x in value.split(",") if x.strip()))
    if ORIGIN not in origins:
        origins.append(ORIGIN)
    line = KEY + "=" + ",".join(origins)
    if indices:
        lines[indices[0]] = line
    else:
        lines.append(line)
    return "\n".join(lines) + "\n"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file", default=".env")
    parser.add_argument("--apply", action="store_true", help="Write atomically; default validates only")
    args = parser.parse_args()
    path = Path(args.file)
    if path.is_symlink() or not path.is_file():
        raise SystemExit("Expected an existing regular .env file")
    original = path.read_text(encoding="utf-8")
    result = updated(original)
    if not args.apply:
        print("VALID: allowed origin can be configured; no values printed; no changes written")
        return
    fd, name = tempfile.mkstemp(prefix=".flow-env-", dir=path.resolve().parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as out:
            out.write(result)
            out.flush()
            os.fsync(out.fileno())
        os.chmod(name, 0o600)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)
    print("APPLIED: Vercel allowed origin present; environment values not printed")

if __name__ == "__main__":
    main()
