#!/usr/bin/env python3
# MIT License
# Copyright (c) 2026 Vladislav Sidorovich

"""Validate that src/main/resources/delta-verification/index.json matches src/main/resources/golden."""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

from generate_index import build_index, load_existing_notices


def main() -> int:
    repo_root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
    index_path = repo_root / "src" / "main" / "resources" / "delta-verification" / "index.json"
    golden_root = repo_root / "src" / "main" / "resources" / "golden"

    if not index_path.is_file():
        print(f"FAILED: index file missing: {index_path}", file=sys.stderr)
        return 1

    actual = json.loads(index_path.read_text(encoding="utf-8"))
    if "source" in actual:
        print("FAILED: top-level 'source' block is not permitted in index.json", file=sys.stderr)
        return 1

    for item in actual.get("tables", []):
        if "notice" in item:
            if not isinstance(item["notice"], str) or not item["notice"].strip():
                print(
                    f"FAILED: optional 'notice' on table '{item.get('name')}' must be a non-empty string",
                    file=sys.stderr,
                )
                return 1

    existing_notices = load_existing_notices(index_path)
    expected = build_index(repo_root, existing_notices=existing_notices)

    if actual != expected:
        print(
            "FAILED: index.json is out of sync with src/main/resources/golden. "
            "Run `python3 dev/generate_index.py` to update.",
            file=sys.stderr,
        )
        return 1

    indexed_files = {t["name"] + "/" + f for t in actual["tables"] for f in t["files"]}
    disk_files = {
        Path(dp, fn).relative_to(golden_root).as_posix()
        for dp, _, fns in os.walk(golden_root)
        for fn in fns
        if fn != ".DS_Store"
    }
    if indexed_files != disk_files:
        missing = sorted(disk_files - indexed_files)
        extra = sorted(indexed_files - disk_files)
        print(f"FAILED: file coverage mismatch (missing={missing}, extra={extra})", file=sys.stderr)
        return 1

    print(
        f"OK: validated {len(actual['tables'])} fixtures ({len(indexed_files)} files) "
        f"in {index_path.relative_to(repo_root)}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
