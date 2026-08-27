/**
 * Inline cell editor. Allows editing of all cell types.
 * Dataset/expression cells are synced back to the cell definition.
 */
import {setDirty,undoManager} from '../Utils.js';

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

                if (!cellDef.value || !cellDef.value.type || cellDef.value.type === 'simple') {
                    // Simple cell: sync display text back to the model
                    if (!cellDef.value) cellDef.value = { type: 'simple' };
                    cellDef.value.type = 'simple';
                    cellDef.value.value = newVal != null ? String(newVal) : '';
                    setDirty();
                } else if (source === 'edit') {
                    // Direct input replaces a non-simple value: convert the cell
                    // to simple text so model and canvas never diverge (undoable)
                    const oldValue = cellDef.value;
                    const oldExpand = cellDef.expand;
                    const newSimple = { type: 'simple', value: newVal != null ? String(newVal) : '' };
                    cellDef.value = newSimple;
                    cellDef.expand = 'None';
                    undoManager.add({
                        redo() {
                            cellDef.value = { type: 'simple', value: newVal != null ? String(newVal) : '' };
                            cellDef.expand = 'None';
                            hot.setDataAtCell(row, col, newVal, 'panel');
                            setDirty();
                        },
                        undo() {
                            cellDef.value = oldValue;
                            cellDef.expand = oldExpand;
                            hot.setDataAtCell(row, col, oldVal, 'panel');
                            setDirty();
                        }
                    });
                    setDirty();
                }
                // Other sources (copy/paste, autofill) on non-simple cells:
                // keep the typed value — the property panel owns complex types
            }
            hot.render();
        });

        // beforeChange: no longer block any edits.
        // Users can freely type in any cell; the property panel handles complex types.
    }
}
