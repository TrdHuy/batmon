// .agents/skills/pptx_to_android_xml/bake_svg.js
const fs = require('fs');
const cheerio = require('cheerio');
const svgpath = require('svgpath');

const inputPath = process.argv[2];
const outputPath = process.argv[3];

if (!inputPath || !outputPath) {
    console.error("[Lỗi] Vui lòng cung cấp đủ đường dẫn file nguồn và file đích.");
    process.exit(1);
}

try {
    const svgCode = fs.readFileSync(inputPath, 'utf8');
    const $ = cheerio.load(svgCode, { xmlMode: true });

    // 1. Tìm tất cả các thẻ <g> có chứa transform translate
    $('g[transform]').each((i, el) => {
        const transform = $(el).attr('transform');
        const match = transform.match(/translate\(([^, ]+)[\s,]+([^)]+)\)/);
        
        if (match) {
            const tx = parseFloat(match[1]);
            const ty = parseFloat(match[2]);

            // 2. CHỈ DUYỆT CÁC THẺ CON TRỰC TIẾP (tránh đâm xuyên làm hỏng nested group)
            $(el).children().each((j, child) => {
                const tagName = child.name; // Tên thẻ (path, rect, g...)

                if (tagName === 'path') {
                    // Cập nhật path
                    const originalD = $(child).attr('d');
                    if (originalD) {
                        const newD = svgpath(originalD).translate(tx, ty).round(3).toString();
                        $(child).attr('d', newD);
                    }
                } 
                else if (tagName === 'rect') {
                    // Cập nhật tọa độ x, y cho hình chữ nhật
                    const x = parseFloat($(child).attr('x') || 0);
                    const y = parseFloat($(child).attr('y') || 0);
                    $(child).attr('x', +(x + tx).toFixed(3));
                    $(child).attr('y', +(y + ty).toFixed(3));
                }
                else if (tagName === 'circle' || tagName === 'ellipse') {
                    // Cập nhật tâm cx, cy cho hình tròn/elip
                    const cx = parseFloat($(child).attr('cx') || 0);
                    const cy = parseFloat($(child).attr('cy') || 0);
                    $(child).attr('cx', +(cx + tx).toFixed(3));
                    $(child).attr('cy', +(cy + ty).toFixed(3));
                }
                else if (tagName === 'g') {
                    // Nếu gặp group lồng bên trong (Nested Group)
                    const childTransform = $(child).attr('transform');
                    
                    if (childTransform && childTransform.startsWith('matrix')) {
                        // Ép phép tịnh tiến (translate) vào thẳng matrix(a,b,c,d,e,f)
                        // Công thức: e' = e + tx, f' = f + ty
                        const matrixMatch = childTransform.match(/matrix\(([^)]+)\)/);
                        if (matrixMatch) {
                            const vals = matrixMatch[1].split(/[\s,]+/).map(parseFloat);
                            if (vals.length === 6) {
                                vals[4] = +(vals[4] + tx).toFixed(3);
                                vals[5] = +(vals[5] + ty).toFixed(3);
                                $(child).attr('transform', `matrix(${vals.join(' ')})`);
                            }
                        }
                    } else if (childTransform && childTransform.startsWith('translate')) {
                        // Cộng dồn 2 translate
                        const tMatch = childTransform.match(/translate\(([^, ]+)[\s,]+([^)]+)\)/);
                        if (tMatch) {
                            const ctx = parseFloat(tMatch[1]);
                            const cty = parseFloat(tMatch[2]);
                            $(child).attr('transform', `translate(${ctx + tx} ${cty + ty})`);
                        }
                    } else {
                        // Nếu thẻ <g> lồng bên trong chưa có transform, gán cho nó translate của cha
                        $(child).attr('transform', `translate(${tx} ${ty})`);
                    }
                }
            });

            // 3. Xóa thuộc tính transform của thẻ <g> gốc vì mọi thứ đã được ép vào con
            $(el).removeAttr('transform');
        }
    });

    // 4. Bổ sung viewBox nếu PPTX chỉ xuất width/height
    const svgEl = $('svg');
    if (!svgEl.attr('viewBox')) {
        const w = parseFloat(svgEl.attr('width'));
        const h = parseFloat(svgEl.attr('height'));
        if (!isNaN(w) && !isNaN(h)) {
            svgEl.attr('viewBox', `0 0 ${w} ${h}`);
        }
    }

    fs.writeFileSync(outputPath, $.xml());
    console.log(`[Thành công] Đã chuẩn hóa tọa độ (Bake Transform) toàn bộ cấu trúc SVG và lưu tại: ${outputPath}`);

} catch (error) {
    console.error(`[Lỗi] Không thể xử lý file ${inputPath}:`, error.message);
    process.exit(1);
}