#!/usr/bin/env python3
"""Generate the MkDocs configuration that publishes the docs/ directory.

Only `docs/` is published, so a stray markdown file elsewhere in the repository
never lands on the site. The nav is generated from what is found there, so a new
docs/*.md appears in the site's navigation without anyone editing CI.

Usage: python3 contrib/ci/build-docs.py [staging-dir]   (default build/docs-site)
"""

import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
BASE_CONFIG = Path(__file__).resolve().parent / "mkdocs.yml"

MD_SUFFIXES = {".md", ".markdown"}
INDEX = "index.md"


def markdown_docs() -> list:
    """Documentation paths relative to docs/, from git when it is available.

    An empty result is reported as such rather than falling back to a
    filesystem walk, so untracked scratch files in docs/ are never published.
    """
    try:
        out = subprocess.run(
            ["git", "-C", str(ROOT), "ls-files", "-z", "--", "docs"],
            capture_output=True, check=True, text=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError):
        return sorted(p.relative_to(DOCS) for p in DOCS.rglob("*") if p.suffix.lower() in MD_SUFFIXES)
    return sorted(
        Path(p).relative_to("docs")
        for p in out.split("\0")
        if p and Path(p).suffix.lower() in MD_SUFFIXES
    )


def title_of(path: Path) -> str:
    """First heading in the document, ATX or setext, else the file name."""
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    for i, line in enumerate(lines):
        heading = re.match(r"\s{0,3}#{1,6}\s+(.*)", line)
        if heading:
            return heading.group(1).strip().rstrip("#").strip()
        underlined = line.strip() and i + 1 < len(lines) and re.fullmatch(r"(=+|-{2,})\s*", lines[i + 1])
        if underlined and not line.lstrip().startswith(("-", "|", ">", "*")):
            return line.strip()
    stem = path.stem
    if stem.lower() in ("readme", "index"):
        return path.parent.name or "Home"
    return re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", stem.replace("-", " ").replace("_", " ")).strip().capitalize()


def q(text: str) -> str:
    return json.dumps(text, ensure_ascii=False)


def nav_lines(entries) -> list:
    """entries: list of (path relative to docs/, title). index.md leads as Home."""
    top, sections = [], {}
    for rel, title in entries:
        if rel.as_posix() == INDEX:
            continue
        (top if len(rel.parts) == 1 else sections.setdefault(rel.parts[0], [])).append((rel, title))

    lines = ["nav:"]
    if any(rel.as_posix() == INDEX for rel, _ in entries):
        lines.append(f"  - Home: {INDEX}")
    for rel, title in sorted(top, key=lambda e: e[1].lower()):
        lines.append(f"  - {q(title)}: {rel.as_posix()}")
    for section in sorted(sections):
        lines.append(f"  - {q(section)}:")
        for rel, title in sorted(sections[section], key=lambda e: e[1].lower()):
            lines.append(f"      - {q(title)}: {rel.as_posix()}")
    return lines


def main() -> int:
    staging = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build" / "docs-site"

    entries = [(rel, title_of(DOCS / rel)) for rel in markdown_docs() if (DOCS / rel).is_file()]
    if not entries:
        print("No markdown found under docs/ to publish", file=sys.stderr)
        return 1

    # docs_dir points at the repository's own docs/, so images and other assets
    # beside a document are picked up without staging a copy of the tree.
    config = [f"docs_dir: {q(str(DOCS))}"]
    pages_url = os.environ.get("CI_PAGES_URL")
    if pages_url:
        config.append(f"site_url: {q(pages_url.rstrip('/') + '/')}")
    config.append(BASE_CONFIG.read_text(encoding="utf-8"))
    config.extend(nav_lines(entries))

    staging.mkdir(parents=True, exist_ok=True)
    (staging / "mkdocs.yml").write_text("\n".join(config) + "\n", encoding="utf-8")

    print(f"Publishing {len(entries)} document(s) from {DOCS} via {staging / 'mkdocs.yml'}")
    for rel, title in entries:
        print(f"  {title} <- docs/{rel.as_posix()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
