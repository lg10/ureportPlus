/**
 * ZxingEditor v4 — unified barcode/QR code editor.
 *
 * BUG FIX (from v3):
 *   In v3, PropertyPanel had two separate type pills (qrcode / barcode) that
 *   both mapped to the same zxing editor instance. The refresh() method used
 *   `type==='zxing'` and always grabbed `editorMap['zxing']`, ignoring the
 *   `category` field — so switching between a barcode cell and a QR cell
 *   showed the wrong editor state.
 *
 *   In v4, "条码/二维码" is ONE content type in the dropdown. The category
 *   (barcode vs qrcode) is a sub-property dropdown INSIDE this editor.
 *   Switching category changes the displayed fields — no editor swapping.
 */
import {setDirty} from '../../Utils.js';
import CodeMirror from 'codemirror';

const BARCODE_FORMATS = [
    'AZTEC', 'CODABAR', 'CODE_39', 'CODE_93', 'CODE_128',
    'DATA_MATRIX', 'EAN_8', 'EAN_13', 'ITF', 'MAXICODE',
    'PDF_417', 'QR_CODE', 'RSS_14', 'RSS_EXPANDED', 'UPC_A', 'UPC_E', 'UPC_EAN_EXTENSION'
];

export default class ZxingEditor {
    constructor(parentContainer, context) {
        this.context = context;
        this.container = $(`<div style="display:none"></div>`);
        parentContainer.append(this.container);
        this.codeMirror = null;
        this._init();
    }

    _init() {
        const self = this;

        // Category dropdown (THE BUG FIX: barcode/qrcode is now a sub-property)
        const catRow = $(`<div class="ud-prop-v4-field"><label>类型</label></div>`);
        this.categorySelect = $(`<select class="field-input">
            <option value="qrcode">二维码</option>
            <option value="barcode">条码</option>
        </select>`);
        this.categorySelect.on('change', function () {
            const cat = $(this).val();
            self._onCategoryChange(cat);
            if (!self.initialized) self._setCategory(cat);
        });
        catRow.append(this.categorySelect);
        this.container.append(catRow);

        // Source type
        const srcRow = $(`<div class="ud-prop-v4-field"><label>来源</label></div>`);
        this.sourceSelect = $(`<select class="field-input">
            <option value="text">文本</option>
            <option value="expression">表达式</option>
        </select>`);
        this.sourceSelect.on('change', function () {
            self._toggleSourceType($(this).val());
            if (!self.initialized) self._setSource($(this).val());
        });
        srcRow.append(this.sourceSelect);
        this.container.append(srcRow);

        // Text input (source=text)
        this.textRow = $(`<div></div>`);
        this.textInput = $(`<textarea rows="3" style="width:100%;padding:6px 8px;border:1px solid #e5e5e5;border-radius:6px;font:12px inherit;resize:vertical;outline:none" placeholder="输入编码内容..."></textarea>`);
        this.textInput.on('change input', function () {
            if (!self.initialized) {
                self.cellDef.value.data = this.value;
                setDirty();
            }
        });
        this.textRow.append(this.textInput);
        this.container.append(this.textRow);

        // Expression editor (source=expression)
        this.exprRow = $(`<div style="display:none"></div>`);
        this.cmContainer = $(`<div style="border:1px solid #e5e5e5;border-radius:6px;overflow:hidden;min-height:50px"></div>`);
        this.exprRow.append(this.cmContainer);
        this.container.append(this.exprRow);

        // Barcode format (only for category=barcode)
        this.formatRow = $(`<div class="ud-prop-v4-field" style="display:none"><label>条码格式</label></div>`);
        this.formatSelect = $(`<select class="field-input"></select>`);
        BARCODE_FORMATS.forEach(f => {
            this.formatSelect.append(`<option value="${f}">${f}</option>`);
        });
        this.formatSelect.on('change', function () {
            if (!self.initialized) {
                self.cellDef.value.format = $(this).val();
                setDirty();
            }
        });
        this.formatRow.append(this.formatSelect);
        this.container.append(this.formatRow);

        // Code display toggle (only for category=barcode)
        this.codeDisplayRow = $(`<div class="ud-prop-v4-field" style="display:none"><label>显示编码文字</label></div>`);
        this.codeDisplayCheck = $(`<input type="checkbox">`);
        this.codeDisplayCheck.on('change', function () {
            if (!self.initialized) {
                self.cellDef.value.codeDisplay = this.checked;
                setDirty();
            }
        });
        this.codeDisplayRow.append(this.codeDisplayCheck);
        this.container.append(this.codeDisplayRow);

        // Size
        const sizeRow = $(`<div style="display:flex;gap:8px;margin-top:6px"></div>`);
        const wGrp = $(`<div style="flex:1"><label style="font-size:11px;color:#666">宽度</label></div>`);
        this.widthInput = $(`<input type="number" min="1" placeholder="px">`);
        this.widthInput.on('change', function () {
            if (!self.initialized) {
                self.cellDef.value.width = parseInt(this.value, 10) || 0;
                if (self.context.hot) self.context.hot.render();
                setDirty();
            }
        });
        wGrp.append(this.widthInput);
        sizeRow.append(wGrp);

        const hGrp = $(`<div style="flex:1"><label style="font-size:11px;color:#666">高度</label></div>`);
        this.heightInput = $(`<input type="number" min="1" placeholder="px">`);
        this.heightInput.on('change', function () {
            if (!self.initialized) {
                self.cellDef.value.height = parseInt(this.value, 10) || 0;
                if (self.context.hot) self.context.hot.render();
                setDirty();
            }
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
            self.cellDef.value.data = self.codeMirror.getValue();
            setDirty();
        });
    }

    _onCategoryChange(cat) {
        // Show/hide barcode-specific fields based on category
        // THIS is the fix — no separate editor instance, just show/hide fields
        const isBarcode = cat === 'barcode';
        this.formatRow.toggle(isBarcode);
        this.codeDisplayRow.toggle(isBarcode);
    }

    _toggleSourceType(source) {
        if (source === 'expression') {
            this.textRow.hide();
            this.exprRow.show();
            this._initCodeMirror();
            if (this.codeMirror) this.codeMirror.refresh();
        } else {
            this.textRow.show();
            this.exprRow.hide();
        }
    }

    show(cellDef, rowIndex, colIndex, row2Index, col2Index) {
        this.initialized = true;
        this.cellDef = cellDef;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.row2Index = row2Index;
        this.col2Index = col2Index;
        this.container.show();

        const v = cellDef.value;
        const category = v.category || 'qrcode';
        const source = v.source || 'text';

        // Category
        this.categorySelect.val(category);
        this._onCategoryChange(category);

        // Source
        this.sourceSelect.val(source);
        this._toggleSourceType(source);

        // Content
        if (source === 'expression') {
            this._initCodeMirror();
            this.codeMirror.setValue(v.data || '');
            this.codeMirror.refresh();
        } else {
            this.textInput.val(v.data || '');
        }

        // Format (barcode only)
        if (category === 'barcode') {
            this.formatSelect.val(v.format || 'CODE_128');
        }

        // Code display
        this.codeDisplayCheck.prop('checked', !!v.codeDisplay);

        // Size
        this.widthInput.val(v.width || '');
        this.heightInput.val(v.height || '');

        this.initialized = false;
    }

    hide() {
        this.container.hide();
    }

    // ---- Multi-cell setters ----

    _setCategory(cat) {
        for (let i = this.rowIndex; i <= this.row2Index; i++) {
            for (let j = this.colIndex; j <= this.col2Index; j++) {
                const cd = this.context.getCell(i, j);
                if (!cd || !cd.value || cd.value.type !== 'zxing') continue;
                cd.value.category = cat;
            }
        }
        setDirty();
    }

    _setSource(source) {
        for (let i = this.rowIndex; i <= this.row2Index; i++) {
            for (let j = this.colIndex; j <= this.col2Index; j++) {
                const cd = this.context.getCell(i, j);
                if (!cd || !cd.value || cd.value.type !== 'zxing') continue;
                cd.value.source = source;
            }
        }
        setDirty();
    }
}
