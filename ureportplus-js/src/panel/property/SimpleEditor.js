/**
 * SimpleEditor v4 — plain text content editor.
 * Supports multi-cell editing via PropertyState.
 */
import {setDirty} from '../../Utils.js';

export default class SimpleEditor {
    constructor(parentContainer, context) {
        this.context = context;
        this.container = $(`<div style="display:none"></div>`);
        parentContainer.append(this.container);
        this._init();
    }

    _init() {
        const self = this;
        const label = $(`<div style="font-size:11px;color:#666;margin-bottom:4px">文本内容</div>`);
        this.container.append(label);

        this.editor = $(`<textarea rows="4" style="width:100%;padding:6px 8px;border:1px solid #e5e5e5;border-radius:6px;font:12px inherit;resize:vertical;outline:none;transition:border-color .15s" placeholder="输入文本内容..."></textarea>`);
        this.editor.on('focus', function () {
            $(this).css('border-color', '#4c9aff');
        });
        this.editor.on('blur', function () {
            $(this).css('border-color', '#e5e5e5');
        });
        this.editor.on('input', function () {
            if (self.initialized) return;
            const val = this.value;
            self.cellDef.value.value = val;
            if (self.context.hot) {
                self.context.hot.setDataAtCell(self.rowIndex, self.colIndex, val, 'panel');
            }
            setDirty();
        });
        this.container.append(this.editor);
    }

    show(cellDef, rowIndex, colIndex, row2Index, col2Index) {
        this.initialized = true;
        this.cellDef = cellDef;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.container.show();
        this.editor.val(cellDef.value.value || '');
        this.initialized = false;
    }

    hide() {
        this.container.hide();
    }
}
