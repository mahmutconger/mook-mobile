#!/usr/bin/env python3
"""
Eksik çeviri anahtarlarını dil dosyalarına otomatik ekleyen betik.

Ne yapar?
  1. Varsayılan dil dosyasını (`values/strings.xml`, İngilizce) okur.
  2. Kapsamdaki anahtarları seçer (varsayılan: Satın Alma / Paywall ve Limit ekranları).
  3. Türkçe (`values-tr`) dışındaki her dil klasöründe eksik olan anahtarları bulur.
  4. Eksikleri İngilizce yedek değerle, dosyanın sonuna işaretli bir blok içinde ekler:
         <!-- needs-translation:start ... -->
         ...
         <!-- needs-translation:end -->
     Böylece çevirmenler/araçlar çevrilmeyi bekleyen metinleri tek aramayla bulur.

Özellikler:
  - İdempotenttir: zaten var olan anahtarlara dokunmaz, tekrar çalıştırmak güvenlidir.
  - XML yapısı ve mevcut metinler aynen korunur (yalnızca `</resources>` öncesine ekleme yapılır).
  - `--check` yalnızca rapor verir, dosya yazmaz (CI'da eksik çeviri kontrolü için).

Kullanım:
  python3 scripts/l10n/sync_missing_translations.py            # ekle
  python3 scripts/l10n/sync_missing_translations.py --check    # yalnızca raporla
  python3 scripts/l10n/sync_missing_translations.py --scope all
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "shared" / "src" / "commonMain" / "composeResources"
DEFAULT_DIR = "values"
# Türkçe elle çevrilir ve her zaman tamdır; otomatik yedek EKLENMEZ.
EXCLUDED_DIRS = {"values", "values-tr"}

STRING_ELEMENT = re.compile(r'<string\s+name="(?P<name>[^"]+)"[^>]*>.*?</string>', re.DOTALL)

# Satın Alma (Paywall), abonelik, ödüllü reklam ve limit ekranlarıyla ilgili anahtarlar.
SCOPES: dict[str, re.Pattern[str]] = {
    "monetization": re.compile(
        r"^(paywall_|billing_|limit_)"
        r"|(_limit|_limit_|quota|subscription|purchase|_trial|premium|boost|ads_reward)"
    ),
    "all": re.compile(r".*"),
}

START_MARKER = (
    "    <!-- needs-translation:start | Otomatik İngilizce yedek. Bu metinler henüz bu dile "
    "çevrilmedi; scripts/l10n/sync_missing_translations.py tarafından eklendi. -->"
)
END_MARKER = "    <!-- needs-translation:end -->"


def parse_strings(path: Path) -> dict[str, str]:
    """Dosyadaki `<string>` öğelerini, ham XML hâlleriyle birlikte sıralı olarak döner."""
    text = path.read_text(encoding="utf-8")
    return {match.group("name"): match.group(0) for match in STRING_ELEMENT.finditer(text)}


def inject(path: Path, elements: list[str]) -> None:
    """Eksik öğeleri işaretli bir blok olarak `</resources>` etiketinden hemen önce ekler."""
    text = path.read_text(encoding="utf-8")
    closing = text.rfind("</resources>")
    if closing == -1:
        raise ValueError(f"{path}: </resources> bulunamadı")
    block = "\n".join([START_MARKER, *(f"    {element}" for element in elements), END_MARKER])
    head = text[:closing].rstrip("\n")
    path.write_text(f"{head}\n{block}\n{text[closing:]}", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Eksik çeviri anahtarlarını İngilizce yedekle ekler.")
    parser.add_argument("--scope", choices=sorted(SCOPES), default="monetization")
    parser.add_argument("--check", action="store_true", help="Dosya yazmadan yalnızca raporla")
    args = parser.parse_args()

    defaults = parse_strings(RESOURCES / DEFAULT_DIR / "strings.xml")
    scope = SCOPES[args.scope]
    scoped_keys = [name for name in defaults if scope.search(name)]

    locale_dirs = sorted(
        directory for directory in RESOURCES.iterdir()
        if directory.is_dir() and directory.name.startswith("values") and directory.name not in EXCLUDED_DIRS
    )

    total_missing = 0
    touched_locales = 0
    for directory in locale_dirs:
        strings_file = directory / "strings.xml"
        if not strings_file.exists():
            continue
        present = parse_strings(strings_file)
        missing = [name for name in scoped_keys if name not in present]
        if not missing:
            continue
        total_missing += len(missing)
        touched_locales += 1
        if not args.check:
            inject(strings_file, [defaults[name] for name in missing])

    action = "eksik (rapor)" if args.check else "eklendi"
    print(
        f"kapsam={args.scope} anahtar={len(scoped_keys)} dil={len(locale_dirs)} "
        f"etkilenen_dil={touched_locales} toplam_{action}={total_missing}"
    )
    return 1 if args.check and total_missing > 0 else 0


if __name__ == "__main__":
    sys.exit(main())
