# Skill: pptx_to_android_xml

## Description
Skill này được sử dụng khi User yêu cầu chuyển đổi các file UI/Icon được trích xuất từ PowerPoint (.pptx) sang định dạng Android Vector Drawable (.xml).
PowerPoint thường xuất SVG kèm theo thẻ `<g transform="translate(...)">` và thiếu `viewBox`. Skill này dùng script `bake_svg.js` để tính toán lại ma trận tọa độ, ép thẳng vào nét vẽ `<path>` (bake transform), sau đó dùng tool CLI để convert sang XML chuẩn cho Android.

## Prerequisites
Hệ thống phải được cài đặt sẵn:
1. Node.js packages: `cheerio`, `svgpath`
2. CLI tool: `svg2vectordrawable` (Cài đặt qua `npm install -g svg2vectordrawable`)

## How to Use (Dành cho Agent)
Khi nhận yêu cầu xử lý file SVG từ PowerPoint, hãy thực hiện tuần tự 2 bước shell command sau:

**Bước 1: Sửa lỗi tọa độ SVG (Bake Transform)**
Sử dụng script `bake_svg.js` nằm trong thư mục của skill này để tạo ra một file SVG đã được chuẩn hóa tọa độ.
```bash
node .agents/skills/pptx_to_android_xml/bake_svg.js <đường_dẫn_file_svg_gốc> <đường_dẫn_file_svg_đầu_ra>
```
*Ví dụ: `node .agents/skills/pptx_to_android_xml/bake_svg.js tmp/icon.svg tmp/icon_baked.svg`*

**Bước 2: Convert sang Android XML**
Sử dụng tool `svg2vectordrawable` lên file `_baked.svg` vừa được tạo ra ở Bước 1.
```bash
npx svg2vectordrawable -i <đường_dẫn_file_svg_đầu_ra_bước_1> -o <đường_dẫn_file_xml_đích>
```
*Ví dụ: `npx svg2vectordrawable -i tmp/icon_baked.svg -o app/src/main/res/drawable/icon.xml`*

**Bước 3 (Tùy chọn):** Xóa file `_baked.svg` trung gian nếu không còn cần thiết.

