---
name: pptx-export-image-fontfix
description: Export slide images from PPTX while preserving Samsung Sharp Sans by extracting OTF fonts from ppt/embeddings/oleObject*.bin, installing them into LibreOffice Snap font path, exporting PDF, then rendering PNG. Use this when LibreOffice falls back to Noto due embedded .fntdata/EOT issues.
---

# PPTX Export Image Font Fix

## Use When
- Need to export slide image from a PPTX in this repo.
- Slide uses Samsung Sharp Sans and normal LibreOffice export falls back to Noto.
- PPTX contains `ppt/embeddings/oleObject*.bin` with packaged `.otf` files.

## Quick Command
```bash
python3 .agents/skills/pptx-export-image-fontfix/scripts/export_pptx_slide_png.py \
  --pptx docs/ui/[AOS16]BatMonUI_v1.3.pptx \
  --out-dir tmp/pptx_v13_export \
  --slide 2
```

## What This Skill Does
- Unzips PPTX to a temp workspace.
- Reads `ppt/embeddings/oleObject*.bin`.
- Carves valid OTF blocks from `OTTO` signature using sfnt table lengths.
- Maps and writes fonts as:
  - `samsungsharpsans.otf`
  - `samsungsharpsans-bold.otf`
  - `samsungsharpsans-medium.otf`
- Installs fonts into LibreOffice Snap user font dir.
- Runs headless LibreOffice export PPTX -> PDF.
- Runs `pdftoppm` to export selected slide to PNG.
- Prints output paths and `pdffonts` report.

## Requirements
- `python3`
- LibreOffice CLI (`/snap/bin/libreoffice` or `libreoffice`)
- `pdftoppm`
- Optional but recommended: `pdffonts`

## Notes
- Script prefers Snap font path: `~/snap/libreoffice/current/.local/share/fonts/samsung-sharp-sans/`.
- It also syncs to `~/snap/libreoffice/common/.local/share/fonts/samsung-sharp-sans/` and `~/.local/share/fonts/samsung-sharp-sans/`.
- If the PPTX has no OLE-packaged OTF attachments, the script exits with an explicit error.
