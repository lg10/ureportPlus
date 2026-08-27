/**
 * ImageEditor v4 — image content editor.
 * Supports text/expression sources, width/height, and preview.
 */
import {setDirty} from '../../Utils.js';
import CodeMirror from 'codemirror';

export default class ImageEditor {
    constructor(parentContainer, context) {
        this.context = context;
        this.container = $(`<div style="display:none"></div>`);
        parentContainer.append(this.container);
        this.codeMirror = null;
        this._init();
    }

    _init() {
        const self = this;

        // Source type
        const srcRow = $(`<div class="ud-prop-v4-field"><label>图片来源</label></div>`);
        this.sourceSelect = $(`<select class="field-input">
            <option value="text">文本路径</option>
            <option value="expression">表达式</option>
        </select>`);
        this.sourceSelect.on('change', function () {
            self._toggleSourceType($(this).val());
            if (!self.initialized) self._setSource($(this).val());
        });
        srcRow.append(this.sourceSelect);
        this.container.append(srcRow);

        // Text path input
        this.pathRow = $(`<div></div>`);
        this.pathInput = $(`<input type="text" placeholder="如 /images/logo.png 或 http://...">`);
        this.pathInput.on('change', function () {
            if (!self.initialized) {
                self.cellDef.value.path = this.value;
                setDirty();
            }
        });
        this.pathRow.append(this.pathInput);
        this.container.append(this.pathRow);

        // Expression editor container
        this.exprRow = $(`<div style="display:none"></div>`);
        this.cmContainer = $(`<div style="border:1px solid #e5e5e5;border-radius:6px;overflow:hidden;min-height:50px"></div>`);
        this.exprRow.append(this.cmContainer);
        this.container.append(this.exprRow);

        // Size
        const sizeRow = $(`<div style="display:flex;gap:8px;margin-top:6px"></div>`);
        const wGrp = $(`<div style="flex:1"><label style="font-size:11px;color:#666">宽度</label></div>`);
        this.widthInput = $(`<input type="number" min="0" placeholder="自动">`);
        this.widthInput.on('change', function () {
            if (!self.initialized) { self.cellDef.value.width = parseInt(this.value, 10) || 0; setDirty(); }
        });
        wGrp.append(this.widthInput);
        sizeRow.append(wGrp);

        const hGrp = $(`<div style="flex:1"><label style="font-size:11px;color:#666">高度</label></div>`);
        this.heightInput = $(`<input type="number" min="0" placeholder="自动">`);
        this.heightInput.on('change', function () {
            if (!self.initialized) { self.cellDef.value.height = parseInt(this.value, 10) || 0; setDirty(); }
        });
        hGrp.append(this.heightInput);
        sizeRow.append(hGrp);
        this.container.append(sizeRow);
    }

    _initCodeMirror() {
        if (this.codeMirror) return;
        const self = this;
        this.codeMirror = CodeMirror(this.cmContainer.get(0), {
            value: '',
            mode: 'javascript',
            lineNumbers: false,
            lineWrapping: true
        });
        this.codeMirror.setSize(null, 50);
        this.codeMirror.on('change', function () {
            if (self.initialized) return;
            self.cellDef.value.expr = self.codeMirror.getValue();
            setDirty();
        });
    }

    _toggleSourceType(source) {
        if (source === 'expression') {
            this.pathRow.hide();
            this.exprRow.show();
            this._initCodeMirror();
            if (this.codeMirror) this.codeMirror.refresh();
        } else {
            this.pathRow.show();
            this.exprRow.hide();
        }
    }

    show(cellDef, rowIndex, colIndex, row2Index, col2Index) {
        this.initialized = true;
        this.cellDef = cellDef;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.container.show();

        const v = cellDef.value;
        const source = v.source || 'text';
        this.sourceSelect.val(source);
        this._toggleSourceType(source);

        if (source === 'expression') {
            this._initCodeMirror();
            this.codeMirror.setValue(v.expr || '');
            this.codeMirror.refresh();
        } else {
            this.pathInput.val(v.path || '');
        }

        this.widthInput.val(v.width || '');
        this.heightInput.val(v.height || '');

        this.initialized = false;
    }

    hide() {
        this.container.hide();
    }

    _setSource(source) {
        for (let i = this.rowIndex; i <= this.row2Index; i++) {
            for (let j = this.colIndex; j <= this.col2Index; j++) {
                const cd = this.context.getCell(i, j);
                if (!cd || !cd.value || cd.value.type !== 'image') continue;
                cd.value.source = source;
            }
        }
        setDirty();
    }
}
