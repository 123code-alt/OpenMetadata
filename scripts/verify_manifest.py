#!/usr/bin/env python3
"""Verify copied targets, optionally checking upstream Git objects when available."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--upstream-repo', type=Path, help='Optional checkout containing OpenMetadata 2.0.8 Git objects')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
manifest = json.loads((root / 'SOURCE_MANIFEST.json').read_text())
repo = args.upstream_repo or root
errors = []
checked = 0
for item in manifest['files']:
    target = root / item['target']
    if not target.is_file() or hashlib.sha256(target.read_bytes()).hexdigest() != item['targetSha256']:
        errors.append('Target hash mismatch: ' + item['target'])
    if shutil.which('git'):
        result = subprocess.run(['git', 'show', manifest['commit'] + ':' + item['source']], cwd=repo, capture_output=True, check=False)
        if result.returncode == 0:
            checked += 1
            if hashlib.sha256(result.stdout).hexdigest() != item['sha256']:
                errors.append('Upstream hash mismatch: ' + item['source'])
if errors:
    print('\n'.join(errors), file=sys.stderr)
    raise SystemExit(1)
print(f"Verified {len(manifest['files'])} target files; checked {checked} upstream objects.")
if checked != len(manifest['files']):
    print('Some upstream Git objects are absent in this minimal clone; target verification passed. Use --upstream-repo for a full source check.')
