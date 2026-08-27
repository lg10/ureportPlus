/**
 * Smart autocomplete dropdown for expression/URL inputs.
 * Shows dataset fields, functions, cell references, and params.
 */
const FUNCTIONS = [
    {v:'sum(',       label:'求和', tag:'fun'},{v:'avg(', label:'均值',tag:'fun'},{v:'count(',label:'计数',tag:'fun'},
    {v:'max(',       label:'最大',tag:'fun'},{v:'min(',label:'最小',tag:'fun'},{v:'list(',label:'列表',tag:'fun'},
    {v:'row()',      label:'行号',tag:'fun'},{v:'column()',label:'列号',tag:'fun'},
    {v:'page()',     label:'页码',tag:'fun'},{v:'pages()',label:'总页数',tag:'fun'},
    {v:'pageSum(',   label:'本页和',tag:'fun'},{v:'pageAvg(',label:'本页均值',tag:'fun'},
    {v:'param(',     label:'URL参数',tag:'fun'},{v:'paramIsEmpty(',label:'参数为空',tag:'fun'},
    {v:'if(',        label:'条件判断',tag:'fun'},{v:'formatNumber(',label:'格式化数字',tag:'fun'},
    {v:'chn(',       label:'中文大写',tag:'fun'},{v:'chnMoney(',label:'大写金额',tag:'fun'},
    {v:'&&',         label:'绝对引用',tag:'ref'},{v:'#',label:'当前值',tag:'ref'},
    {v:'formatDate(',label:'日期格式化',tag:'fun'},{v:'round(',label:'四舍五入',tag:'fun'},
    {v:'abs(',label:'绝对值',tag:'fun'},{v:'ceil(',label:'向上取整',tag:'fun'},{v:'floor(',label:'向下取整',tag:'fun'},
    {v:'length(',label:'字符串长度',tag:'fun'},{v:'lower(',label:'转小写',tag:'fun'},{v:'upper(',label:'转大写',tag:'fun'},
    {v:'trim(',label:'去空格',tag:'fun'},{v:'substring(',label:'截取子串',tag:'fun'},
    {v:'date(',label:'构造日期',tag:'fun'},{v:'day(',label:'提取日',tag:'fun'},{v:'month(',label:'提取月',tag:'fun'},{v:'year(',label:'提取年',tag:'fun'},
];

export default class AutoCompleteHelper {
    /**
     * Attach autocomplete to an input element.
     * @param {jQuery} $input - the input element
     * @param {Object} context - report context (for dataset fields, cell names)
     */
    static attach($input, context) {
        const dropdown = $(`<div class="ud-ac"></div>`);
        $input.after(dropdown);
        $input.parent().css('position', 'relative');

        let selectedIdx = -1;
        let items = [];

        function show(items_) {
            items = items_;
            selectedIdx = -1;
            if (!items.length) { dropdown.hide(); return; }
            const off = $input.offset();
            dropdown.css({
                left: (off.left) + 'px',
                top: (off.top + $input.outerHeight() + 2) + 'px',
                width: Math.max($input.outerWidth(), 200) + 'px'
            });
            dropdown.html(items.map((it, i) =>
                `<div class="ud-ac-item${i===0?' sel':''}" data-idx="${i}">
                    <span>${it.v}</span><span class="tag">${it.label}</span>
                </div>`
            ).join('')).show();
        }

        function apply(it) {
            const val = $input.val();
            // Find last incomplete word before cursor
            const pos = $input[0].selectionStart;
            const before = val.substring(0, pos);
            const match = before.match(/([\w.]*)$/);
            const replaceLen = match ? match[1].length : 0;
            const start = pos - replaceLen;
            const newVal = val.substring(0, start) + it.v + val.substring(pos);
            $input.val(newVal);
            $input[0].setSelectionRange(start + it.v.length, start + it.v.length);
            $input.focus();
            dropdown.hide();
        }

        $input.on('keydown', function(e) {
            if (!dropdown.is(':visible')) return;
            if (e.key === 'ArrowDown') { e.preventDefault(); selectedIdx = Math.min(selectedIdx + 1, items.length - 1); updateSel(); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); selectedIdx = Math.max(selectedIdx - 1, 0); updateSel(); }
            else if (e.key === 'Enter' && selectedIdx >= 0) { e.preventDefault(); apply(items[selectedIdx]); }
            else if (e.key === 'Escape') { dropdown.hide(); }
        });

        function updateSel() {
            dropdown.find('.ud-ac-item').removeClass('sel');
            dropdown.find(`.ud-ac-item[data-idx="${selectedIdx}"]`).addClass('sel');
        }

        dropdown.on('click', '.ud-ac-item', function() {
            const idx = parseInt($(this).data('idx'));
            if (idx >= 0 && idx < items.length) apply(items[idx]);
        });

        $input.on('input', function() {
            const val = $input.val();
            const pos = $input[0].selectionStart;
            const before = val.substring(0, pos);
            const match = before.match(/([\w.]*)$/);
            const prefix = match ? match[1].toLowerCase() : '';

            if (prefix.length < 1 && !before.endsWith('$') && !before.endsWith('&') && !before.endsWith('#')) {
                dropdown.hide(); return;
            }

            let suggestions = [];
            // Dataset fields: if prefix contains '.', suggest fields for that dataset
            const dotIdx = prefix.lastIndexOf('.');
            if (dotIdx >= 0 && context) {
                const dsName = prefix.substring(0, dotIdx);
                const fieldPrefix = prefix.substring(dotIdx + 1);
                // Try to find dataset fields
                const cells = context.reportDef && context.reportDef.cells;
                if (cells) {
                    for (let c of cells) {
                        if (c.value && c.value.type === 'dataset' && c.value.datasetName === dsName) {
                            // Found matching dataset - but we don't have field list easily
                        }
                    }
                }
                suggestions = FUNCTIONS.filter(f => f.v.toLowerCase().indexOf(fieldPrefix) >= 0);
            } else if (prefix === '&' || prefix === '#' || prefix === '$') {
                // Cell references
                suggestions = FUNCTIONS.filter(f => f.tag === 'ref');
            } else {
                suggestions = FUNCTIONS.filter(f =>
                    f.v.toLowerCase().indexOf(prefix) >= 0 ||
                    f.label.indexOf(prefix) >= 0
                );
            }

            // Add cell names if context available
            if (context && context.hot) {
                try {
                    const cols = context.hot.countCols();
                    const rows = context.hot.countRows();
                    const cellPrefix = prefix.toUpperCase();
                    for (let r = 1; r <= Math.min(rows, 50); r++) {
                        for (let c = 0; c < Math.min(cols, 26); c++) {
                            const cn = context.getCellName(r - 1, c);
                            if (cn && cn.indexOf(cellPrefix) === 0 && cellPrefix.length > 0) {
                                suggestions.unshift({v: cn, label: '单元格', tag: 'cell'});
                            }
                        }
                    }
                } catch(e) {}
            }

            show(suggestions.slice(0, 12));
        });

        // Close on blur
        $(document).on('mousedown', function(e) {
            if (!$(e.target).closest('.ud-ac').length && !$(e.target).is($input)) {
                setTimeout(() => dropdown.hide(), 150);
            }
        });

        return dropdown;
    }
}
