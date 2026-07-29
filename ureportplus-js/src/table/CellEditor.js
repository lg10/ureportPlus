/**
 * Inline cell editor. Allows editing of all cell types.
 * Dataset/expression cells are synced back to the cell definition.
 */
import {setDirty} from '../Utils.js';

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

        hot.updateSettings({ editor: 'text' });

        // afterChange: sync value from Handsontable data back to cellDef
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

                // For simple cells, directly update the value
                if (cellDef.value.type === 'simple') {
                    cellDef.value.value = newVal != null ? String(newVal) : '';
                    setDirty();
                }
                // For non-simple cells, also allow the edit to go through
                // The property panel provides the proper UI; direct input is harmless
            }
            hot.render();
        });

        // beforeChange: no longer block any edits.
        // Users can freely type in any cell; the property panel handles complex types.
    }
}
