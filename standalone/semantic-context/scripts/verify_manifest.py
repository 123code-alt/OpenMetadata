#!/usr/bin/env python3
"""Check copied source hashes without requiring a build or network access."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
manifest = json.loads((root / "SOURCE_MANIFEST.json").read_text())
repo = root.parent.parent
errors = []
upstream_checked = 0
for item in manifest["files"]:
    target = root / item["target"]
    if not target.is_file() or hashlib.sha256(target.read_bytes()).hexdigest() != item["targetSha256"]:
        errors.append("Target hash mismatch: " + item["target"])
    result = subprocess.run(
        ["git", "show", manifest["commit"] + ":" + item["source"]],
        cwd=repo, capture_output=True, check=False,
    )
    if result.returncode == 0:
        upstream_checked += 1
        if hashlib.sha256(result.stdout).hexdigest() != item["sha256"]:
            errors.append("Upstream hash mismatch: " + item["source"])
if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print(f"Verified {len(manifest['files'])} target files and {upstream_checked} upstream Git objects.")
