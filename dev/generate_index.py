#!/usr/bin/env python3
# MIT License
# Copyright (c) 2026 Vladislav Sidorovich

"""Generate src/main/resources/delta-verification/index.json from src/main/resources/golden."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Any

COMMIT_RE = re.compile(r"^(\d{20})\.json$")
CHECKPOINT_RE = re.compile(r"^(\d{20})\.checkpoint(\..+)?\.(parquet|json)$")


def discover_fixtures(golden_root: Path) -> list[tuple[str, Path, bool]]:
    """Return sorted (relative_name, path, has_delta_log) for all fixtures under golden_root."""
    delta_tables: list[tuple[str, Path, bool]] = []

    def walk_delta(directory: Path) -> bool:
        has_any = False
        if (directory / "_delta_log").is_dir():
            rel = directory.relative_to(golden_root).as_posix()
            delta_tables.append((rel, directory, True))
            return True
        for child in sorted(directory.iterdir()):
            if child.is_dir():
                if walk_delta(child):
                    has_any = True
        return has_any

    for top in sorted(golden_root.iterdir()):
        if top.is_dir():
            if not walk_delta(top):
                rel = top.relative_to(golden_root).as_posix()
                delta_tables.append((rel, top, False))

    delta_tables.sort(key=lambda item: item[0])
    return delta_tables


def inspect_delta_log(delta_log_dir: Path) -> dict[str, Any]:
    latest_version: int | None = None
    has_checkpoint = False
    min_reader: int | None = None
    min_writer: int | None = None
    reader_features: list[str] = []
    writer_features: list[str] = []

    entries = sorted(p.name for p in delta_log_dir.iterdir() if p.is_file())
    commit_files: list[tuple[int, Path]] = []

    for name in entries:
        m_commit = COMMIT_RE.match(name)
        if m_commit:
            ver = int(m_commit.group(1))
            latest_version = ver if latest_version is None else max(latest_version, ver)
            commit_files.append((ver, delta_log_dir / name))
            continue
        m_chk = CHECKPOINT_RE.match(name)
        if m_chk:
            has_checkpoint = True
            ver = int(m_chk.group(1))
            latest_version = ver if latest_version is None else max(latest_version, ver)

    for _, commit_path in sorted(commit_files, key=lambda x: x[0]):
        try:
            with commit_path.open("r", encoding="utf-8") as fh:
                for line in fh:
                    line = line.strip()
                    if not line:
                        continue
                    obj = json.loads(line)
                    if isinstance(obj, dict) and "protocol" in obj and isinstance(obj["protocol"], dict):
                        proto = obj["protocol"]
                        if "minReaderVersion" in proto:
                            min_reader = proto["minReaderVersion"]
                        if "minWriterVersion" in proto:
                            min_writer = proto["minWriterVersion"]
                        reader_features = sorted(proto.get("readerFeatures") or [])
                        writer_features = sorted(proto.get("writerFeatures") or [])
        except (OSError, json.JSONDecodeError):
            pass

    return {
        "latestVersion": latest_version,
        "hasCheckpoint": has_checkpoint,
        "minReaderVersion": min_reader,
        "minWriterVersion": min_writer,
        "readerFeatures": reader_features,
        "writerFeatures": writer_features,
    }


def list_fixture_files(fixture_dir: Path) -> list[str]:
    files: list[str] = []
    for dp, _, filenames in os.walk(fixture_dir):
        for fn in filenames:
            if fn == ".DS_Store":
                continue
            rel = Path(dp, fn).relative_to(fixture_dir).as_posix()
            files.append(rel)
    files.sort()
    return files


def load_existing_notices(index_path: Path) -> dict[str, str]:
    if not index_path.is_file():
        return {}
    try:
        data = json.loads(index_path.read_text(encoding="utf-8"))
        notices: dict[str, str] = {}
        for item in data.get("tables", []):
            if isinstance(item, dict) and isinstance(item.get("name"), str):
                notice = item.get("notice")
                if isinstance(notice, str) and notice.strip():
                    notices[item["name"]] = notice
        return notices
    except (OSError, json.JSONDecodeError):
        return {}


def build_index(
    repo_root: Path,
    existing_notices: dict[str, str] | None = None,
    default_notice: str | None = None,
) -> dict[str, Any]:
    golden_root = repo_root / "src" / "main" / "resources" / "golden"
    if not golden_root.is_dir():
        raise FileNotFoundError(f"Golden root directory not found: {golden_root}")

    notices = existing_notices if existing_notices is not None else {}
    fixtures = discover_fixtures(golden_root)
    tables_out: list[dict[str, Any]] = []

    for name, path, has_delta_log in fixtures:
        files = list_fixture_files(path)
        entry: dict[str, Any] = {
            "name": name,
            "resourcePath": f"golden/{name}",
            "hasDeltaLog": has_delta_log,
        }
        if has_delta_log:
            meta = inspect_delta_log(path / "_delta_log")
            entry.update(meta)
        else:
            entry.update(
                {
                    "latestVersion": None,
                    "hasCheckpoint": False,
                    "minReaderVersion": None,
                    "minWriterVersion": None,
                    "readerFeatures": [],
                    "writerFeatures": [],
                }
            )
        notice = notices.get(name, default_notice)
        if notice:
            entry["notice"] = notice
        entry["files"] = files
        tables_out.append(entry)

    return {
        "schemaVersion": 1,
        "tables": tables_out,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--default-notice",
        default=None,
        help="Optional notice string to assign to fixtures that do not already carry a notice in index.json.",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    out_path = repo_root / "src" / "main" / "resources" / "delta-verification" / "index.json"
    existing_notices = load_existing_notices(out_path)
    index = build_index(repo_root, existing_notices=existing_notices, default_notice=args.default_notice)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(index, indent=2) + "\n", encoding="utf-8")
    delta_count = sum(1 for t in index["tables"] if t["hasDeltaLog"])
    other_count = len(index["tables"]) - delta_count
    print(f"Wrote {out_path} ({delta_count} Delta tables, {other_count} negative/auxiliary fixtures)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
