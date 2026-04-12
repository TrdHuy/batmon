#!/usr/bin/env python3
"""
Export PPTX slide images with Samsung Sharp Sans font fidelity.

Workflow:
1) Extract OTF fonts from ppt/embeddings/oleObject*.bin
2) Install fonts to LibreOffice snap/user font directories
3) Export PPTX -> PDF with LibreOffice headless
4) Export selected PDF slide -> PNG with pdftoppm
"""

from __future__ import annotations

import argparse
import shutil
import struct
import subprocess
import zipfile
from pathlib import Path
from typing import Dict, List, Optional

EXPECTED_FONTS = [
    "samsungsharpsans.otf",
    "samsungsharpsans-bold.otf",
    "samsungsharpsans-medium.otf",
]


def run(cmd: List[str], check: bool = True, capture: bool = False) -> subprocess.CompletedProcess:
    result = subprocess.run(cmd, check=False, text=True, capture_output=capture)
    if check and result.returncode != 0:
        raise RuntimeError(f"Command failed ({result.returncode}): {' '.join(cmd)}\n{result.stderr}")
    return result


def detect_libo_binary() -> str:
    candidates = [
        "/snap/bin/libreoffice",
        shutil.which("libreoffice"),
        shutil.which("soffice"),
    ]
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return candidate
    raise RuntimeError("LibreOffice binary not found. Expected /snap/bin/libreoffice or libreoffice in PATH.")


def ensure_tool(name: str) -> str:
    tool = shutil.which(name)
    if not tool:
        raise RuntimeError(f"Required tool not found: {name}")
    return tool


def unzip_pptx(pptx: Path, dst: Path) -> None:
    with zipfile.ZipFile(pptx, "r") as zf:
        zf.extractall(dst)


def carve_otf_from_ole(blob: bytes) -> Optional[bytes]:
    start = blob.find(b"OTTO")
    if start < 0 or start + 12 > len(blob):
        return None

    num_tables = struct.unpack_from(">H", blob, start + 4)[0]
    table_dir_offset = start + 12
    max_end = 0

    for i in range(num_tables):
        rec_offset = table_dir_offset + i * 16
        if rec_offset + 16 > len(blob):
            return None
        _, _, offset, length = struct.unpack_from(">4sIII", blob, rec_offset)
        end = offset + length
        if end > max_end:
            max_end = end

    if max_end <= 0 or start + max_end > len(blob):
        return None
    return blob[start : start + max_end]


def hint_font_name(blob: bytes) -> Optional[str]:
    ascii_fragments = []
    current = bytearray()
    for b in blob:
        if 32 <= b <= 126:
            current.append(b)
        else:
            if len(current) >= 8:
                ascii_fragments.append(current.decode("latin1", errors="ignore"))
            current = bytearray()
    if len(current) >= 8:
        ascii_fragments.append(current.decode("latin1", errors="ignore"))

    for frag in ascii_fragments:
        lower = frag.lower()
        if "samsungsharpsans" in lower and ".otf" in lower:
            token = lower.split("\\")[-1].split("/")[-1].strip()
            if "samsungsharpsans" in token:
                token = token[token.index("samsungsharpsans") :]
            if ".otf" in token:
                token = token[: token.index(".otf") + 4]
                return token
    return None


def canonical_font_name(hint: Optional[str], index: int) -> Optional[str]:
    if hint:
        h = hint.lower()
        if "bold" in h:
            return "samsungsharpsans-bold.otf"
        if "medium" in h:
            return "samsungsharpsans-medium.otf"
        if "regular" in h or h == "samsungsharpsans.otf":
            return "samsungsharpsans.otf"

    # Common ordering in this repo's PPTX attachments
    fallback = {
        1: "samsungsharpsans.otf",
        2: "samsungsharpsans-bold.otf",
        3: "samsungsharpsans-medium.otf",
    }
    return fallback.get(index)


def extract_fonts_from_ole(unzipped_root: Path, fonts_out_dir: Path) -> Dict[str, Path]:
    ole_bins = sorted((unzipped_root / "ppt" / "embeddings").glob("oleObject*.bin"))
    if not ole_bins:
        raise RuntimeError("No oleObject*.bin found in ppt/embeddings")

    fonts_out_dir.mkdir(parents=True, exist_ok=True)
    captured: Dict[str, bytes] = {}
    unknown_payloads: List[bytes] = []

    for idx, bin_path in enumerate(ole_bins, start=1):
        blob = bin_path.read_bytes()
        otf = carve_otf_from_ole(blob)
        if not otf:
            continue

        hint = hint_font_name(blob)
        name = canonical_font_name(hint, idx)
        if name and name not in captured:
            captured[name] = otf
        else:
            unknown_payloads.append(otf)

    for missing in EXPECTED_FONTS:
        if missing not in captured and unknown_payloads:
            captured[missing] = unknown_payloads.pop(0)

    if not captured:
        raise RuntimeError("No OTF payload extracted from oleObject*.bin")

    out_paths: Dict[str, Path] = {}
    for name, payload in captured.items():
        out_path = fonts_out_dir / name
        out_path.write_bytes(payload)
        out_paths[name] = out_path

    return out_paths


def install_fonts_for_libo(font_paths: Dict[str, Path]) -> None:
    home = Path.home()
    font_roots = [
        home / "snap" / "libreoffice" / "current" / ".local" / "share" / "fonts" / "samsung-sharp-sans",
        home / "snap" / "libreoffice" / "common" / ".local" / "share" / "fonts" / "samsung-sharp-sans",
        home / ".local" / "share" / "fonts" / "samsung-sharp-sans",
    ]

    for root in font_roots:
        root.mkdir(parents=True, exist_ok=True)
        for src in font_paths.values():
            shutil.copy2(src, root / src.name)

    fc_cache = shutil.which("fc-cache")
    if fc_cache:
        for root in font_roots:
            run([fc_cache, "-f", str(root)], check=False)

    # Refresh font cache inside LibreOffice snap runtime, if snap is available.
    if shutil.which("snap"):
        run(
            [
                "snap",
                "run",
                "--shell",
                "libreoffice",
                "-c",
                "fc-cache -f ~/.local/share/fonts >/dev/null 2>&1 || true",
            ],
            check=False,
        )


def export_pdf(libo_bin: str, pptx: Path, out_dir: Path) -> Path:
    run(
        [
            libo_bin,
            "--headless",
            "--invisible",
            "--norestore",
            "--nolockcheck",
            "--convert-to",
            "pdf",
            "--outdir",
            str(out_dir),
            str(pptx),
        ]
    )
    pdf = out_dir / f"{pptx.stem}.pdf"
    if not pdf.exists():
        raise RuntimeError(f"PDF export failed. Expected output missing: {pdf}")
    return pdf


def export_slide_png(pdf: Path, slide: int, png_path: Path) -> Path:
    ensure_tool("pdftoppm")
    prefix = png_path.with_suffix("")
    run(["pdftoppm", "-f", str(slide), "-singlefile", "-png", str(pdf), str(prefix)])
    generated = Path(f"{prefix}.png")
    if not generated.exists():
        raise RuntimeError(f"PNG export failed. Expected output missing: {generated}")
    if generated != png_path:
        generated.replace(png_path)
    return png_path


def collect_pdffonts_report(pdf: Path) -> Optional[str]:
    if not shutil.which("pdffonts"):
        return None
    result = run(["pdffonts", str(pdf)], check=False, capture=True)
    if result.returncode != 0:
        return None
    return result.stdout


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export PPTX slide PNG with OLE-font fix")
    parser.add_argument("--pptx", required=True, type=Path, help="Input PPTX path")
    parser.add_argument("--out-dir", required=True, type=Path, help="Output directory")
    parser.add_argument("--slide", type=int, default=2, help="1-based slide index for PNG export")
    parser.add_argument(
        "--png-name",
        default=None,
        help="Output PNG filename. Default: <pptx-stem>_slide<slide>.png",
    )
    parser.add_argument(
        "--keep-workdir",
        action="store_true",
        help="Keep temporary extracted PPTX folder",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    pptx = args.pptx.resolve()
    if not pptx.exists():
        raise RuntimeError(f"PPTX not found: {pptx}")

    out_dir = args.out_dir.resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    work_dir = out_dir / "_work"
    unzip_dir = work_dir / "unzipped"
    if work_dir.exists():
        shutil.rmtree(work_dir)
    unzip_dir.mkdir(parents=True, exist_ok=True)

    print(f"[INFO] Unzipping PPTX: {pptx}")
    unzip_pptx(pptx, unzip_dir)

    print("[INFO] Extracting OTF fonts from OLE attachments")
    fonts_out = out_dir / "fonts_from_ole"
    font_paths = extract_fonts_from_ole(unzip_dir, fonts_out)
    for name, path in sorted(font_paths.items()):
        print(f"[OK] font: {name} -> {path}")

    print("[INFO] Installing fonts for LibreOffice")
    install_fonts_for_libo(font_paths)

    libo_bin = detect_libo_binary()
    print(f"[INFO] Using LibreOffice: {libo_bin}")

    print("[INFO] Exporting PDF")
    pdf_path = export_pdf(libo_bin, pptx, out_dir)

    png_name = args.png_name or f"{pptx.stem}_slide{args.slide}.png"
    png_path = out_dir / png_name

    print(f"[INFO] Exporting slide {args.slide} -> PNG")
    export_slide_png(pdf_path, args.slide, png_path)

    report = collect_pdffonts_report(pdf_path)
    if report:
        report_path = out_dir / f"{pdf_path.stem}.pdffonts.txt"
        report_path.write_text(report, encoding="utf-8")
        print(f"[OK] pdffonts report: {report_path}")

    if not args.keep_workdir and work_dir.exists():
        shutil.rmtree(work_dir)

    print("[DONE]")
    print(f"PDF: {pdf_path}")
    print(f"PNG: {png_path}")
    print(f"Fonts: {fonts_out}")


if __name__ == "__main__":
    main()
