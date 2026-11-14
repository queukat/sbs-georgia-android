#!/usr/bin/env python3
"""Reject dependencies on Android adapters from the pure domain layer."""
from pathlib import Path
import re

DOMAIN = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/com/queukat/sbsgeorgia/domain"
)
FORBIDDEN_IMPORT = re.compile(
    r"^\s*import\s+com\.queukat\.sbsgeorgia\.(?:data|ui|worker|widget)(?:\.|$)"
)


def main() -> int:
    violations = []
    for source in sorted(DOMAIN.rglob("*.kt")):
        for lineno, line in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
            if FORBIDDEN_IMPORT.match(line):
                violations.append(f"{source.relative_to(DOMAIN)}:{lineno}: {line.strip()}")
    if violations:
        print("Domain layer imports forbidden adapters:")
        print("\n".join(violations))
        return 1
    print("Domain dependency boundaries: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
