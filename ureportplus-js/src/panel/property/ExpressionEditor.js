/**
 * ExpressionEditor v4 — expression content editor with CodeMirror.
 * Supports multi-cell editing and keeps CodeMirror integration.
 */
import {setDirty} from '../../Utils.js';
import {alert} from '../../MsgBox.js';
import CodeMirror from 'codemirror';

export default class ExpressionEditor {
    constructor(parentContainer, context) {
        this.context = context;
        this.container = $(`<div style="display:none"></div>`);
        parentContainer.append(this.container);
        this.codeMirror = null;
        this._init();
    }

    _init() {
        const self = this;

        // Expression label + actions row
        const header = $(`<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:4px"></div>`);
        header.append(`<span style="font-size:11px;color:#666">表达式</span>`);

        const actions = $(`<div style="display:flex;gap:4px"></div>`);
        const formatBtn = $(`<button class="btn btn-default btn-sm" title="格式化表达式">格式化</button>`);
        const expandBtn = $(`<button class="btn btn-default btn-sm" title="放大编辑">放大</button>`);
        const condBtn = $(`<button class="btn btn-default btn-sm" title="条件属性">条件属性</button>`);

        formatBtn.on('click', () => this._formatExpr());
        expandBtn.on('click', () => this._expandEditor());
        condBtn.on('click', () => this._openConditions());

        actions.append(formatBtn, expandBtn, condBtn);
        header.append(actions);
        this.container.append(header);

        // CodeMirror container
        this.cmContainer = $(`<div style="border:1px solid #e5e5e5;border-radius:6px;overflow:hidden;min-height:60px"></div>`);
        this.container.append(this.cmContainer);

        // Format result hint
        this.hintEl = $(`<div style="font-size:10px;color:#999;margin-top:4px;min-height:16px"></div>`);
        this.container.append(this.hintEl);
    }

    /**
     * Initialize CodeMirror lazily (on first show).
     */
    _initCodeMirror() {
        if (this.codeMirror) return;
        const self = this;

        this.codeMirror = CodeMirror(this.cmContainer.get(0), {
            value: '',
            mode: 'javascript',
            lineNumbers: true,
            lineWrapping: true,
            indentUnit: 2,
            tabSize: 2,
            gutters: ['CodeMirror-lint-markers'],
            lint: { getAnnotations: this._buildScriptLintFunction(), async: true },
            extraKeys: {
                'Ctrl-Space': 'autocomplete',
                'Ctrl-Enter': function () {
                    self._formatExpr();
                }
            }
        });

        this.codeMirror.setSize(null, 60);

        this.codeMirror.on('change', function () {
            if (self.initialized) return;
            const value = self.codeMirror.getValue();
            self.cellDef.value.value = value;
            setDirty();
        });
    }

    show(cellDef, rowIndex, colIndex, row2Index, col2Index) {
        this.initialized = true;
        this.cellDef = cellDef;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.container.show();

        this._initCodeMirror();
        this.codeMirror.setValue(cellDef.value.value || '');
        this.codeMirror.refresh();
        this.initialized = false;
    }

    hide() {
        this.container.hide();
    }

    // ---- Internal ----

    _formatExpr() {
        if (!this.codeMirror) return;
        const expr = this.codeMirror.getValue();
        if (!expr || !expr.trim()) return;
        const url = window._server + '/designer/formatExpression';
        const self = this;
        $.ajax({
            url,
            type: 'POST',
            data: { expression: expr },
            success(result) {
                if (result) {
                    self.codeMirror.setValue(result);
                    self.hintEl.text('已格式化');
                    setTimeout(() => self.hintEl.text(''), 2000);
                }
            },
            error() {
                self.hintEl.text('格式化失败');
                setTimeout(() => self.hintEl.text(''), 2000);
            }
        });
    }

    _expandEditor() {
        if (!this.codeMirror) return;
        const currentHeight = this.codeMirror.getWrapperElement().style.height;
        const isExpanded = currentHeight && parseInt(currentHeight) > 100;
        this.codeMirror.setSize(null, isExpanded ? 60 : 200);
        this.codeMirror.refresh();
    }

    _openConditions() {
        if (this.cb && this.cb.onEditConditions) {
            this.cb.onEditConditions();
        }
    }

    _buildScriptLintFunction() {
        const self = this;
        return function (text, updateLinting, options, editor) {
            if (!text || text.trim() === '') {
                updateLinting(editor, []);
                return;
            }
            const url = window._server + '/designer/scriptValidation';
            $.ajax({
                url,
                data: { content: text },
                type: 'POST',
                success(result) {
                    if (result && result.length) {
                        result.forEach(item => {
                            item.from = { line: item.line - 1 };
                            item.to = { line: item.line - 1 };
                        });
                        updateLinting(editor, result);
                    } else {
                        updateLinting(editor, []);
                    }
                },
                error() {
                    updateLinting(editor, []);
                }
            });
        };
    }
}
