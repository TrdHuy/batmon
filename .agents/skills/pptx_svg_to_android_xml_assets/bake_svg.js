const fs = require('fs');
const cheerio = require('cheerio');
const svgpath = require('svgpath');

const inputPath = process.argv[2];
const outputPath = process.argv[3];

if (!inputPath || !outputPath) {
    console.error("[Lỗi] Vui lòng cung cấp đủ đường dẫn file nguồn và đích.");
    process.exit(1);
}

try {
    const svgCode = fs.readFileSync(inputPath, 'utf8');
    const $ = cheerio.load(svgCode, { xmlMode: true });

    // BƯỚC 1: CONVERT TẤT CẢ <rect> THÀNH <path> ĐỂ DỄ BAKE TỌA ĐỘ
    $('rect').each((i, el) => {
        const x = parseFloat($(el).attr('x') || 0);
        const y = parseFloat($(el).attr('y') || 0);
        const w = parseFloat($(el).attr('width') || 0);
        const h = parseFloat($(el).attr('height') || 0);
        let rx = parseFloat($(el).attr('rx') || $(el).attr('ry') || 0);
        let ry = parseFloat($(el).attr('ry') || $(el).attr('rx') || 0);

        if (rx > w / 2) rx = w / 2;
        if (ry > h / 2) ry = h / 2;

        let d = '';
        if (rx === 0 && ry === 0) {
            d = `M${x} ${y} H${x + w} V${y + h} H${x} Z`;
        } else {
            // Toán học để vẽ hình chữ nhật bo góc bằng Path
            d = `M${x + rx} ${y} ` +
                `H${x + w - rx} A${rx} ${ry} 0 0 1 ${x + w} ${y + ry} ` +
                `V${y + h - ry} A${rx} ${ry} 0 0 1 ${x + w - rx} ${y + h} ` +
                `H${x + rx} A${rx} ${ry} 0 0 1 ${x} ${y + h - ry} ` +
                `V${y + ry} A${rx} ${ry} 0 0 1 ${x + rx} ${y} Z`;
        }

        // Thay thế <rect> bằng <path> nhưng giữ nguyên mọi thuộc tính khác (fill, matrix...)
        const attribs = { ...$(el).attr(), d: d };
        delete attribs.x; delete attribs.y; delete attribs.width; 
        delete attribs.height; delete attribs.rx; delete attribs.ry;
        
        $(el).replaceWith($('<path>').attr(attribs));
    });

    // BƯỚC 2: BAKE TRANSFORM CỦA CHÍNH CÁC THẺ <path> (BAO GỒM CẢ MA TRẬN)
    $('path[transform]').each((i, el) => {
        const transform = $(el).attr('transform');
        const d = $(el).attr('d');
        if (d && transform) {
            // svgpath tự động hiểu matrix() và tính toán lại tọa độ tuyệt đối
            const newD = svgpath(d).transform(transform).round(3).toString();
            $(el).attr('d', newD);
            $(el).removeAttr('transform');
        }
    });

    // BƯỚC 3: ÉP TRANSFORM TỪ CÁC THẺ GROUP <g> XUỐNG CÁC THẺ CON
    let hasGroupTransform = true;
    while (hasGroupTransform) {
        hasGroupTransform = false;
        $('g[transform]').each((i, el) => {
            hasGroupTransform = true;
            const gTransform = $(el).attr('transform');
            
            // Ép vào các thẻ path con
            $(el).children('path').each((j, childPath) => {
                 const d = $(childPath).attr('d');
                 if(d) {
                     const newD = svgpath(d).transform(gTransform).round(3).toString();
                     $(childPath).attr('d', newD);
                 }
            });

            // Cộng dồn transform vào thẻ group con (nếu group bị lồng nhau)
            $(el).children('g').each((j, childG) => {
                 const cTrans = $(childG).attr('transform') || '';
                 $(childG).attr('transform', gTransform + ' ' + cTrans); 
            });

            $(el).removeAttr('transform');
        });
    }

    // BƯỚC 4: BẢO ĐẢM VIEWPORT
    const svgEl = $('svg');
    if (!svgEl.attr('viewBox')) {
        const w = parseFloat(svgEl.attr('width'));
        const h = parseFloat(svgEl.attr('height'));
        if (!isNaN(w) && !isNaN(h)) svgEl.attr('viewBox', `0 0 ${w} ${h}`);
    }

    fs.writeFileSync(outputPath, $.xml());
    console.log(`[Thành công] Đã Flatten toàn bộ hình học và ma trận. Lưu tại: ${outputPath}`);

} catch (error) {
    console.error(`[Lỗi]:`, error.message);
    process.exit(1);
}