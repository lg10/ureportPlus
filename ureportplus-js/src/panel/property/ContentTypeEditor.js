/**
 * ContentTypeEditor — replaces the 8 type pills with a single dropdown.
 * Switches the content value type and shows the corresponding value editor.
 * Also handles multi-cell mixed-type display.
 */
import {setDirty,undoManager} from '../../Utils.js';
import CrossTabWidget from '../../widget/CrossTabWidget.js';

const CONTENT_TYPES = [
    { value: 'simple',     label: '简单文本', icon: 'Aa' },
    { value: 'expression', label: '表达式',   icon: 'ƒ' },
    { value: 'dataset',    label: '数据集',   icon: '⊞' },
    { value: 'image',      label: '图片',     icon: '🖼' },
    { value: 'chart',      label: '图表',     icon: '📊' },
    { value: 'slash',      label: '斜线',     icon: '/' },
    { value: 'zxing',      label: '条码/二维码', icon: '∥' },
];

export default class ContentTypeEditor {
    constructor(parentContainer, context) {
        this.context = context;
        this.container = $(`<div class="ud-prop-v4-content-type"></div>`);
        parentContainer.append(this.container);
        this.editors = new Map();
        this.chartEditors = new Map();
        this.currentEditor = null;
    }

    /**
     * Register a value editor instance.
     */
    registerEditor(type, editor) {
        this.editors.set(type, editor);
    }

    registerChartEditor(chartType, editor) {
        this.chartEditors.set(chartType, editor);
    }

    /**
     * Build the content type UI.
     */
    build() {
        const self = this;
        const row = $(`<div class="ud-prop-v4-field"></div>`);
        row.append(`<label>内容类型</label>`);

        this.typeSelect = $(`<select class="field-input"></select>`);
        CONTENT_TYPES.forEach(ct => {
            this.typeSelect.append(`<option value="${ct.value}">${ct.icon} ${ct.label}</option>`);
        });

        // The "mixed" option is added/removed dynamically in show()
        this.typeSelect.on('change', function () {
            const newType = self.typeSelect.val();
            if (!newType) return; // mixed option
            self._switchType(newType);
        });

        row.append(this.typeSelect);
        this.container.append(row);

        // Editor area — dynamically filled
        this.editorArea = $(`<div class="ud-prop-v4-editor-area" style="margin-top:4px"></div>`);
        this.container.append(this.editorArea);
    }

    /**
     * Show the editor for the current cell's content type.
     * @param {PropertyState} state
     */
    show(state) {
        this.state = state;
        const currentType = state.currentValueType;
        const isMixed = currentType === null;

        // Add/remove the mixed option dynamically so it never lingers
        this.typeSelect.find('option[value=""]').remove();
        if (isMixed) {
            this.typeSelect.append(`<option value="" disabled>混合</option>`);
            this.typeSelect.val('');
        } else {
            this.typeSelect.val(currentType);
        }

        // Show the corresponding editor
        this._hideAll();
        // Remove the "mixed" message if present
        this.editorArea.find('.ud-ct-mixed-msg').remove();

        if (isMixed) {
            // Show a mixed-state message without destroying editor DOM
            if (!this.editorArea.find('.ud-ct-mixed-msg').length) {
                this.editorArea.append(`<div class="ud-ct-mixed-msg" style="color:#999;font-size:11px;padding:8px 0">选定单元格内容类型不一致</div>`);
            }
        } else {
            this._showEditor(currentType, state);
        }
    }

    hide() {
        this._hideAll();
        this.editorArea.empty();
    }

    // ---- Internal ----

    _hideAll() {
        this.editors.forEach(e => { if (e.hide) e.hide(); });
        this.chartEditors.forEach(e => { if (e.hide) e.hide(); });
        this.currentEditor = null;
    }

    _showEditor(type, state) {
        // Don't empty editorArea — editors' containers are already DOM children.
        // Just hide all and show the target one.
        if (type === 'chart') {
            const chartType = state.currentChartType || 'pie';
            const ce = this.chartEditors.get(chartType);
            if (ce) {
                // Re-append chart editor container to editorArea (in case it was removed)
                if (ce.container && !ce.container.parent().length) {
                    this.editorArea.append(ce.container);
                }
                ce.show(state.anchorCellDef, state.rowIndex, state.colIndex, state.row2Index, state.col2Index);
                this.currentEditor = ce;
            }
        } else {
            // Map 'qrcode'/'barcode' → 'zxing'
            const editorKey = (type === 'qrcode' || type === 'barcode') ? 'zxing' : type;
            const e = this.editors.get(editorKey);
            if (e) {
                // Re-append editor container to editorArea
                if (e.container && !e.container.parent().length) {
                    this.editorArea.append(e.container);
                }
                e.show(state.anchorCellDef, state.rowIndex, state.colIndex, state.row2Index, state.col2Index);
                this.currentEditor = e;
            }
        }
    }

    _switchType(newType) {
        const state = this.state;
        if (!state || !state.cellDefs.length) return;

        const self = this;
        const ri = state.rowIndex;
        const ci = state.colIndex;
        const r2 = state.row2Index;
        const c2 = state.col2Index;
        const hot = this.context.hot;

        // Snapshot old values of ALL selected cells for undo
        const defs = state.cellDefs.slice();
        const undoEntries = defs.map(cd => ({
            cd,
            oldValue: cd.value,
            oldExpand: cd.expand,
            oldWidget: cd.crossTabWidget
        }));

        // Apply the type switch to every selected cell
        defs.forEach(cd => self._applyType(cd, newType, ri, ci));

        if (undoManager) {
            undoManager.add({
                redo() {
                    defs.forEach(cd => self._applyType(cd, newType, ri, ci));
                    state._recomputeAndNotify();
                },
                undo() {
                    undoEntries.forEach(entry => {
                        entry.cd.value = entry.oldValue;
                        entry.cd.expand = entry.oldExpand;
                        if (entry.oldWidget !== undefined) {
                            entry.cd.crossTabWidget = entry.oldWidget;
                        }
                    });
                    state._recomputeAndNotify();
                }
            });
        }

        // Clear cell display text
        if (hot) {
            for (let i = ri; i <= r2; i++) {
                for (let j = ci; j <= c2; j++) {
                    hot.setDataAtCell(i, j, '', 'panel');
                }
            }
            hot.render();
        }

        // Update state and refresh UI
        state._recomputeAndNotify();

        // Trigger Handsontable selection hook so toolbar refreshes
        if (hot && hot.addHook) {
            try { hot.runHooks ? hot.runHooks('afterSelectionEnd', ri, ci, r2, c2) : null; } catch(e) {}
        }

        setDirty();
    }

    /**
     * Apply a content type to one cell definition.
     * Preserves the text payload when switching into 'simple'.
     */
    _applyType(cd, newType, ri, ci) {
        if (!cd.value) cd.value = { type: 'simple' };
        if (cd.value.type === newType) return;
        const hot = this.context.hot;
        switch (newType) {
            case 'simple': {
                const text = cd.value.type === 'simple' ? (cd.value.value || '') : '';
                cd.value = { type: 'simple', value: text };
                cd.expand = 'None';
                break;
            }
            case 'expression':
                cd.value = { type: 'expression', value: '' };
                cd.expand = 'None';
                break;
            case 'dataset':
                cd.value = { type: 'dataset', datasetName: '', property: '', aggregate: 'select', conditions: [], order: 'none' };
                cd.expand = 'Down';
                break;
            case 'image':
                cd.value = { type: 'image', source: 'text' };
                cd.expand = 'None';
                break;
            case 'zxing': {
                const td = hot ? hot.getCell(ri, ci) : null;
                cd.value = {
                    type: 'zxing', source: 'text', category: 'qrcode',
                    width: td ? this._cellW(ci, td.colSpan) : 100,
                    height: td ? this._cellH(ri, td.rowSpan) : 100,
                    data: '', format: ''
                };
                cd.expand = 'None';
                break;
            }
            case 'slash':
                cd.value = { type: 'slash' };
                cd.crossTabWidget = new CrossTabWidget(this.context, ri, ci);
                cd.expand = 'None';
                break;
            case 'chart': {
                const td = hot ? hot.getCell(ri, ci) : null;
                cd.value = {
                    type: 'chart',
                    width: td ? this._cellW(ci, td.colSpan) : 100,
                    height: td ? this._cellH(ri, td.rowSpan) : 100,
                    chart: { dataset: { type: 'pie' } }
                };
                cd.expand = 'None';
                break;
            }
        }
    }

    _cellW(ci, cs) {
        const hot = this.context.hot;
        if (!hot) return 100;
        let w = hot.getColWidth(ci) - 3;
        if (cs >= 2) for (let i = ci + 1; i < ci + cs; i++) w += hot.getColWidth(i);
        return w;
    }

    _cellH(ri, rs) {
        const hot = this.context.hot;
        if (!hot) return 100;
        let h = hot.getRowHeight(ri) - 3;
        if (rs >= 2) for (let i = ri + 1; i < ri + rs; i++) h += hot.getRowHeight(i);
        return h;
    }
}
