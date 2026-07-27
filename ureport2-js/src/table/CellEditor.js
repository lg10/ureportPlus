/**
 * Inline cell editor. Simple cells: double-click to edit + auto-save.
 * Non-simple cells: blocked with notification.
 */
import {setDirty} from '../Utils.js';
import {alert} from '../MsgBox.js';

const TYPE_NAMES = {
    expression: '表达式', dataset: '数据集', image: '图片',
    slash: '斜线表头', zxing: '条码/二维码', chart: '图表'
};

export default class CellEditor {
    constructor(reportTable) {
        this.reportTable = reportTable;
        this.hot = reportTable.hot;
    }

    init() {
        const _this = this;
        const hot = this.hot;

        // Enable the default text editor explicitly
        hot.updateSettings({ editor: 'text' });

        // ---- afterChange: sync value from Handsontable data back to cellDef ----
        hot.addHook('afterChange', function(changes, source) {
            if (source === 'loadData') return;
            if (!changes) return;
            const cellsMap = _this.reportTable.cellsMap;
            if (!cellsMap) return;

            for (let change of changes) {
                const [row, col, oldVal, newVal] = change;
                if (oldVal === newVal) continue;
                const key = (row + 1) + ',' + (col + 1);
                const cellDef = cellsMap.get(key);
                if (!cellDef) continue;
                if (cellDef.value.type !== 'simple') continue;
                cellDef.value.value = newVal != null ? String(newVal) : '';
                setDirty();
            }
            hot.render();
        });

        // ---- beforeChange: block editing of non-simple cells ----
        hot.addHook('beforeChange', function(changes, source) {
            if (source === 'loadData') return;
            if (!changes) return;
            const cellsMap = _this.reportTable.cellsMap;
            if (!cellsMap) return;

            for (let i = changes.length - 1; i >= 0; i--) {
                const [row, col] = changes[i];
                const key = (row + 1) + ',' + (col + 1);
                const cellDef = cellsMap.get(key);
                if (cellDef && cellDef.value.type !== 'simple') {
                    const name = TYPE_NAMES[cellDef.value.type] || cellDef.value.type;
                    _this._blockMsg(cellDef, name);
                    return false;
                }
            }
        });
    }

    _blockMsg(cellDef, typeName) {
        alert(
            '该单元格包含<strong>' + typeName + '</strong>内容，不能直接输入文本。' +
            '<br><br>请通过<strong>右侧属性面板</strong>修改。'
        );
    }
}
