"""Check local JSON Schema references using only the Python standard library."""

from pathlib import Path
import json
import sys
from urllib.parse import unquote, urlparse

base = (
    Path(__file__).resolve().parents[1]
    / "openmetadata-spec/src/main/resources/json/schema"
)
schemas = {
    p.resolve(): json.loads(p.read_text(encoding="utf-8")) for p in base.rglob("*.json")
}
errors = []


def objects(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from objects(child)
    elif isinstance(value, list):
        for child in value:
            yield from objects(child)


for file, document in schemas.items():
    for node in objects(document):
        ref = node.get("$ref")
        if not isinstance(ref, str):
            continue
        parsed = urlparse(ref)
        if parsed.scheme and parsed.netloc not in (
            "open-metadata.org",
            "www.open-metadata.org",
        ):
            continue
        path = unquote(parsed.path)
        if path.startswith("/schema/"):
            target = base / path[len("/schema/") :]
        else:
            target = file.parent / path if path else file
        target = target.resolve()
        if target not in schemas:
            errors.append(f"{file.relative_to(base)}: missing {ref}")
            continue
        if parsed.fragment.startswith("/"):
            value = schemas[target]
            try:
                for part in parsed.fragment[1:].split("/"):
                    key = unquote(part).replace("~1", "/").replace("~0", "~")
                    value = value[int(key)] if isinstance(value, list) else value[key]
            except (KeyError, IndexError, TypeError, ValueError):
                errors.append(f"{file.relative_to(base)}: invalid pointer {ref}")
for error in errors:
    print(error, file=sys.stderr)
print(f"{len(schemas)} schemas checked; {len(errors)} reference errors")
raise SystemExit(bool(errors))
