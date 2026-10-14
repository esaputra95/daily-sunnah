#!/usr/bin/env python3
"""Validate all CDN image/layout pairs and emit their pinned integrity manifest."""

import argparse
import hashlib
import json
import math
import struct
from pathlib import Path


def webp_dimensions(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if len(data) < 30 or data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        raise ValueError(f"{path}: invalid RIFF/WebP header")
    offset = 12
    while offset + 8 <= len(data):
        chunk_type = data[offset : offset + 4]
        chunk_size = struct.unpack_from("<I", data, offset + 4)[0]
        payload = data[offset + 8 : offset + 8 + chunk_size]
        if chunk_type == b"VP8X" and len(payload) >= 10:
            width = 1 + int.from_bytes(payload[4:7], "little")
            height = 1 + int.from_bytes(payload[7:10], "little")
            return width, height
        if chunk_type == b"VP8 " and len(payload) >= 10:
            if payload[3:6] != b"\x9d\x01\x2a":
                raise ValueError(f"{path}: invalid VP8 frame header")
            width = struct.unpack_from("<H", payload, 6)[0] & 0x3FFF
            height = struct.unpack_from("<H", payload, 8)[0] & 0x3FFF
            return width, height
        if chunk_type == b"VP8L" and len(payload) >= 5 and payload[0] == 0x2F:
            bits = int.from_bytes(payload[1:5], "little")
            return 1 + (bits & 0x3FFF), 1 + ((bits >> 14) & 0x3FFF)
        offset += 8 + chunk_size + (chunk_size & 1)
    raise ValueError(f"{path}: unsupported WebP image encoding")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_and_build(mushaf_dir: Path) -> dict:
    image_dir = mushaf_dir / "webp"
    layout_dir = mushaf_dir / "layouts"
    entries = []
    expected_names = {f"{page:03}.webp" for page in range(1, 605)}
    if {path.name for path in image_dir.glob("*.webp")} != expected_names:
        raise ValueError("WebP directory must contain exactly 001.webp through 604.webp")
    if {path.name for path in layout_dir.glob("*.json")} != {name.replace(".webp", ".json") for name in expected_names}:
        raise ValueError("Layout directory must contain exactly 001.json through 604.json")

    total_regions = 0
    seen_surahs = set()
    for page in range(1, 605):
        image = image_dir / f"{page:03}.webp"
        layout_file = layout_dir / f"{page:03}.json"
        layout = json.loads(layout_file.read_text(encoding="utf-8"))
        if layout.get("schemaVersion") != 1 or layout.get("page") != page:
            raise ValueError(f"{layout_file.name}: schema/page mismatch")
        view_box = layout.get("renderViewBox", {})
        x, y, width, height = (float(view_box[key]) for key in ("x", "y", "width", "height"))
        if not all(math.isfinite(value) for value in (x, y, width, height)) or width <= 0 or height <= 0:
            raise ValueError(f"{layout_file.name}: invalid renderViewBox")
        regions = layout.get("verseBoxes", [])
        if not regions:
            raise ValueError(f"{layout_file.name}: no verse regions")
        for region in regions:
            try:
                surah_text, ayah_text = region["verseKey"].split(":", 1)
                surah, ayah = int(surah_text), int(ayah_text)
                rx, ry = float(region["x"]), float(region["y"])
                rw, rh = float(region["width"]), float(region["height"])
                line = int(region["lineNumber"])
            except (KeyError, TypeError, ValueError) as error:
                raise ValueError(f"{layout_file.name}: invalid verse region") from error
            if not (1 <= surah <= 114 and 1 <= ayah <= 286 and line > 0 and rw > 0 and rh > 0):
                raise ValueError(f"{layout_file.name}: invalid Quran reference/rectangle")
            if not all(math.isfinite(value) for value in (rx, ry, rw, rh)):
                raise ValueError(f"{layout_file.name}: non-finite verse rectangle")
            if rx < x - 0.1 or ry < y - 0.1 or rx + rw > x + width + 0.1 or ry + rh > y + height + 0.1:
                raise ValueError(f"{layout_file.name}: verse rectangle outside renderViewBox")
            seen_surahs.add(surah)

        image_width, image_height = webp_dimensions(image)
        image_aspect = image_width / image_height
        layout_aspect = width / height
        if image_width <= 0 or image_height <= 0 or abs(image_aspect - layout_aspect) / layout_aspect >= 0.02:
            raise ValueError(f"page {page}: WebP and layout aspect ratios differ")
        total_regions += len(regions)
        entries.append({
            "page": page,
            "imageWidth": image_width,
            "imageHeight": image_height,
            "imageBytes": image.stat().st_size,
            "imageSha256": sha256(image),
            "layoutBytes": layout_file.stat().st_size,
            "layoutSha256": sha256(layout_file),
        })

    missing = sorted(set(range(1, 115)) - seen_surahs)
    if missing:
        raise ValueError(f"No verse region found for surah(s): {missing}")
    return {
        "schemaVersion": 1,
        "assetVersion": "v1",
        "pageCount": 604,
        "imageFormat": "webp",
        "layoutSchemaVersion": 1,
        "coordinateSpace": "renderViewBox",
        "totalVerseRegions": total_regions,
        "pages": entries,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mushaf_dir", type=Path, help="CDN output directory containing webp/ and layouts/")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    manifest = validate_and_build(args.mushaf_dir)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Validated {manifest['pageCount']} pages and {manifest['totalVerseRegions']} verse regions")


if __name__ == "__main__":
    main()
