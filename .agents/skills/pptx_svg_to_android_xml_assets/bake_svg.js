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

    $('g[transform]').each((i, el) => {
        const transform = $(el).attr('transform');
        const match = transform.match(/translate\(([^, ]+)[\s,]+([^)]+)\)/);
        
        if (match) {
            const tx = parseFloat(match[1]);
            const ty = parseFloat(match[2]);

            $(el).children().each((j, child) => {
                const tagName = child.name;
                const childTransform = $(child).attr('transform');

                // TRƯỜNG HỢP 1: THẺ CON CÓ SẴN TRANSFORM (Ví dụ: rect có matrix)
                if (childTransform) {
                    if (childTransform.startsWith('matrix')) {
                        // Cấu trúc matrix(a, b, c, d, e, f)
                        // Cộng tx vào e, cộng ty vào f. Tuyệt đối không sửa x, y.
                        const matrixMatch = childTransform.match(/matrix\(([^)]+)\)/);
                        if (matrixMatch) {
                            const vals = matrixMatch[1].split(/[\s,]+/).map(parseFloat);
                            if (vals.length === 6) {
                                vals[4] = +(vals[4] + tx).toFixed(3);
                                vals[5] = +(vals[5] + ty).toFixed(3);
                                $(child).attr('transform', `matrix(${vals.join(' ')})`);
                            }
                        }
                    } else if (childTransform.startsWith('translate')) {
                        // Cộng dồn 2 cái translate lại với nhau
                        const tMatch = childTransform.match(/translate\(([^, ]+)[\s,]+([^)]+)\)/);
                        if (tMatch) {
                            const ctx = parseFloat(tMatch[1]);
                            const cty = parseFloat(tMatch[2]);
                            $(child).attr('transform', `translate(${ctx + tx} ${cty + ty})`);
                        }
                    } else {
                        // Nếu là rotate, scale... thì nhét translate của cha lên đầu
                        $(child).attr('transform', `translate(${tx} ${ty}) ${childTransform}`);
                    }
                } 
                // TRƯỜNG HỢP 2: THẺ CON KHÔNG CÓ TRANSFORM (Sửa thẳng vào tọa độ vật lý)
                else {
                    if (tagName === 'path') {
                        const originalD = $(child).attr('d');
                        if (originalD) {
                            const newD = svgpath(originalD).translate(tx, ty).round(3).toString();
                            $(child).attr('d', newD);
                        }
                    } else if (tagName === 'rect') {
                        const x = parseFloat($(child).attr('x') || 0);
                        const y = parseFloat($(child).attr('y') || 0);
                        $(child).attr('x', +(x + tx).toFixed(3));
                        $(child).attr('y', +(y + ty).toFixed(3));
                    } else if (tagName === 'circle' || tagName === 'ellipse') {
                        const cx = parseFloat($(child).attr('cx') || 0);
                        const cy = parseFloat($(child).attr('cy') || 0);
                        $(child).attr('cx', +(cx + tx).toFixed(3));
                        $(child).attr('cy', +(cy + ty).toFixed(3));
                    } else if (tagName === 'g') {
                        $(child).attr('transform', `translate(${tx} ${ty})`);
                    }
                }
            });

            // Xóa transform của thẻ cha sau khi đã "nhồi" hết xuống thẻ con
            $(el).removeAttr('transform');
        }
    });

    const svgEl = $('svg');
    if (!svgEl.attr('viewBox')) {
        const w = parseFloat(svgEl.attr('width'));
        const h = parseFloat(svgEl.attr('height'));
        if (!isNaN(w) && !isNaN(h)) {
            svgEl.attr('viewBox', `0 0 ${w} ${h}`);
        }
    }

    fs.writeFileSync(outputPath, $.xml());
    console.log(`[Thành công] Đã xử lý (Bake Transform) và lưu tại: ${outputPath}`);

} catch (error) {
    console.error(`[Lỗi] Không thể xử lý:`, error.message);
    process.exit(1);
}