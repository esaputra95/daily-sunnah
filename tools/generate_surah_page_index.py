#!/usr/bin/env python3
"""Build the bundled surah start-page index from the CDN's own layout files."""

import argparse
import json
from pathlib import Path

SURAH_NAMES = [
    "Al-Fatihah", "Al-Baqarah", "Ali 'Imran", "An-Nisa'", "Al-Ma'idah", "Al-An'am", "Al-A'raf", "Al-Anfal", "At-Tawbah", "Yunus",
    "Hud", "Yusuf", "Ar-Ra'd", "Ibrahim", "Al-Hijr", "An-Nahl", "Al-Isra'", "Al-Kahf", "Maryam", "Ta-Ha",
    "Al-Anbiya'", "Al-Hajj", "Al-Mu'minun", "An-Nur", "Al-Furqan", "Ash-Shu'ara'", "An-Naml", "Al-Qasas", "Al-'Ankabut", "Ar-Rum",
    "Luqman", "As-Sajdah", "Al-Ahzab", "Saba'", "Fatir", "Ya-Sin", "As-Saffat", "Sad", "Az-Zumar", "Ghafir",
    "Fussilat", "Ash-Shura", "Az-Zukhruf", "Ad-Dukhan", "Al-Jathiyah", "Al-Ahqaf", "Muhammad", "Al-Fath", "Al-Hujurat", "Qaf",
    "Adh-Dhariyat", "At-Tur", "An-Najm", "Al-Qamar", "Ar-Rahman", "Al-Waqi'ah", "Al-Hadid", "Al-Mujadilah", "Al-Hashr", "Al-Mumtahanah",
    "As-Saff", "Al-Jumu'ah", "Al-Munafiqun", "At-Taghabun", "At-Talaq", "At-Tahrim", "Al-Mulk", "Al-Qalam", "Al-Haqqah", "Al-Ma'arij",
    "Nuh", "Al-Jinn", "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyamah", "Al-Insan", "Al-Mursalat", "An-Naba'", "An-Nazi'at", "'Abasa",
    "At-Takwir", "Al-Infitar", "Al-Mutaffifin", "Al-Inshiqaq", "Al-Buruj", "At-Tariq", "Al-A'la", "Al-Ghashiyah", "Al-Fajr", "Al-Balad",
    "Ash-Shams", "Al-Layl", "Ad-Duha", "Ash-Sharh", "At-Tin", "Al-'Alaq", "Al-Qadr", "Al-Bayyinah", "Az-Zalzalah", "Al-'Adiyat",
    "Al-Qari'ah", "At-Takathur", "Al-'Asr", "Al-Humazah", "Al-Fil", "Quraysh", "Al-Ma'un", "Al-Kawthar", "Al-Kafirun", "An-Nasr",
    "Al-Masad", "Al-Ikhlas", "Al-Falaq", "An-Nas",
]


def build_index(layout_dir: Path) -> dict:
    page_files = sorted(layout_dir.glob("*.json"))
    if len(page_files) != 604:
        raise ValueError(f"Expected 604 layout files, found {len(page_files)}")

    first_page_by_surah: dict[int, int] = {}
    for expected_page, path in enumerate(page_files, start=1):
        page_layout = json.loads(path.read_text(encoding="utf-8"))
        page = int(page_layout["page"])
        if page != expected_page:
            raise ValueError(f"{path.name}: expected page {expected_page}, found {page}")
        if int(page_layout["schemaVersion"]) != 1:
            raise ValueError(f"{path.name}: unsupported schemaVersion")
        for box in page_layout["verseBoxes"]:
            surah_text, _ayah_text = box["verseKey"].split(":", 1)
            surah = int(surah_text)
            if not 1 <= surah <= 114:
                raise ValueError(f"{path.name}: invalid surah number {surah}")
            first_page_by_surah.setdefault(surah, page)

    missing = sorted(set(range(1, 115)) - first_page_by_surah.keys())
    if missing:
        raise ValueError(f"No page found for surah(s): {missing}")
    if len(SURAH_NAMES) != 114:
        raise ValueError(f"Expected 114 bundled surah names, found {len(SURAH_NAMES)}")

    return {
        "schemaVersion": 2,
        "assetVersion": "v1",
        "pageCount": 604,
        "source": "Start pages derived from CDN layout v1; display names bundled with the app",
        "startPages": [first_page_by_surah[surah] for surah in range(1, 115)],
        "surahs": [
            {"number": surah, "name": SURAH_NAMES[surah - 1], "startPage": first_page_by_surah[surah]}
            for surah in range(1, 115)
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("layout_dir", type=Path, help="Directory containing 001.json through 604.json")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    payload = build_index(args.layout_dir)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(payload['startPages'])} surah start pages to {args.output}")


if __name__ == "__main__":
    main()
