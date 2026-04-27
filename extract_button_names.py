#!/usr/bin/env python3
"""
Extract unique button names from Flipper IRDB .ir files.

Modes:
1) All categories (default):
   python3 extract_button_names.py

2) Single category:
   python3 extract_button_names.py <category_folder_path>

Examples:
   python3 extract_button_names.py
   python3 extract_button_names.py "/Users/martin/Projects/IRShark/app/src/main/assets/flipper_irdb/TVs"
"""

import sys
from pathlib import Path
from collections import Counter, defaultdict

DEFAULT_DB_ROOT = Path("app/src/main/assets/flipper_irdb")
MIN_OCCURRENCE = 2


def extract_button_names_from_ir_file(filepath: Path) -> set[str]:
    """Parse a .ir file and extract button command names from 'name:' lines."""
    buttons: set[str] = set()
    try:
        with filepath.open("r", encoding="utf-8", errors="ignore") as f:
            for line in f:
                line = line.strip()
                if not line.startswith("name:"):
                    continue
                name = line.split(":", 1)[1].strip()
                if name:
                    buttons.add(name)
    except Exception as e:
        print(f"Error reading {filepath}: {e}", file=sys.stderr)
    return buttons


def canonical_button_key(name: str) -> str:
    """Case-insensitive key used for deduplication and matching."""
    return name.strip().lower()


def build_category_report(category_path: Path, output_dir: Path) -> tuple[str, int, int, Path]:
    """Generate one output text file for a category."""
    ir_files = list(category_path.rglob("*.ir"))
    if not ir_files:
        return (category_path.name, 0, 0, output_dir / f"{category_path.name}_button_names.txt")

    # canonical -> number of files where this button appears
    canonical_file_count: dict[str, int] = defaultdict(int)
    # canonical -> counts of original variants, to pick representative display form
    canonical_variant_counts: dict[str, Counter[str]] = defaultdict(Counter)

    for ir_file in ir_files:
        raw_buttons = extract_button_names_from_ir_file(ir_file)

        # One canonical button contributes at most once per file.
        seen_canonical_in_file = set()
        for btn in raw_buttons:
            canonical = canonical_button_key(btn)
            if not canonical:
                continue
            canonical_variant_counts[canonical][btn] += 1
            seen_canonical_in_file.add(canonical)

        for canonical in seen_canonical_in_file:
            canonical_file_count[canonical] += 1

    def display_name_for(canonical: str) -> str:
        variants_counter = canonical_variant_counts[canonical]
        # Prefer most frequent original variant, tie-break alphabetically.
        return sorted(variants_counter.items(), key=lambda x: (-x[1], x[0]))[0][0]

    consolidated = [
        (display_name_for(canonical), canonical_file_count[canonical])
        for canonical in canonical_file_count.keys()
        if canonical_file_count[canonical] >= MIN_OCCURRENCE
    ]

    sorted_buttons = sorted(consolidated, key=lambda x: (-x[1], x[0].lower()))

    output_file = output_dir / f"{category_path.name}_button_names.txt"
    with output_file.open("w", encoding="utf-8") as f:
        f.write(f"# Button names extracted from {category_path.name} category\n")
        f.write(f"# Found {len(ir_files)} .ir files\n")
        f.write(f"# Total unique button names (case-insensitive, min count {MIN_OCCURRENCE}): {len(sorted_buttons)}\n")
        f.write("# Format: ButtonName (appears in N files)\n")
        f.write("# Note: Volume_up and volume_up are treated as the same button\n")
        f.write(f"# Note: names with occurrence < {MIN_OCCURRENCE} are filtered out\n\n")

        for btn, count in sorted_buttons:
            f.write(f"{btn:40} (appears in {count} files)\n")

    return (category_path.name, len(ir_files), len(sorted_buttons), output_file)


def discover_categories(db_root: Path) -> list[Path]:
    """Return top-level category folders that contain at least one .ir file."""
    categories: list[Path] = []
    for child in sorted(db_root.iterdir(), key=lambda p: p.name.lower()):
        if not child.is_dir():
            continue
        if child.name.startswith("."):
            continue
        # Keep only top-level dirs that actually contain IR files.
        if any(child.rglob("*.ir")):
            categories.append(child)
    return categories


def main() -> None:
    if len(sys.argv) > 2:
        print("Usage: python3 extract_button_names.py [category_folder_path]", file=sys.stderr)
        sys.exit(1)

    if len(sys.argv) == 2:
        category_paths = [Path(sys.argv[1]).resolve()]
    else:
        db_root = (Path.cwd() / DEFAULT_DB_ROOT).resolve()
        if not db_root.exists() or not db_root.is_dir():
            print(f"Error: default IRDB root not found: {db_root}", file=sys.stderr)
            sys.exit(1)
        category_paths = discover_categories(db_root)
        if not category_paths:
            print(f"Error: no categories with .ir files found under: {db_root}", file=sys.stderr)
            sys.exit(1)

    for category_path in category_paths:
        if not category_path.exists() or not category_path.is_dir():
            print(f"Skipping invalid category path: {category_path}", file=sys.stderr)
            continue

        output_dir = Path.cwd() / "out"
        output_dir.mkdir(parents=True, exist_ok=True)

        category_name, ir_count, button_count, out_file = build_category_report(category_path, output_dir)

        if ir_count == 0:
            print(f"{category_name}: no .ir files found, skipped")
            continue

        print(f"{category_name}: {ir_count} .ir files, {button_count} filtered button names -> {out_file}")


if __name__ == "__main__":
    main()
