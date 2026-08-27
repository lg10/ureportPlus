/**
 * SlashEditor v4 — slash cell content editor.
 * Supports multiple slash lines with text/X/Y/degree properties.
 */
import {setDirty} from '../../Utils.js';
import CrossTabWidget from '../../widget/CrossTabWidget.js';

export default class SlashEditor {
    constructor(parentContainer, context) {
        this.context = context;
        this.container = $(`<div style="display:none"></div>`);
        parentContainer.append(this.container);
        this._init();
    }

    _init() {
        const self = this;

        // Refresh preview button
        const btnRow = $(`<div style="margin-bottom:6px"></div>`);
        this.refreshBtn = $(`<button class="btn btn-default btn-sm">刷新预览</button>`);
        this.refreshBtn.on('click', () => self._refreshPreview());
        btnRow.append(this.refreshBtn);
        this.container.append(btnRow);

        // Slash list container
        this.slashList = $(`<div></div>`);
        this.container.append(this.slashList);

        // Add button
        const addRow = $(`<div style="margin-top:4px"></div>`);
        this.addBtn = $(`<button class="btn btn-default btn-sm">+ 添加斜线</button>`);
        this.addBtn.on('click', () => self._addSlash());
        addRow.append(this.addBtn);
        this.container.append(addRow);
    }

    show(cellDef, rowIndex, colIndex, row2Index, col2Index) {
        this.initialized = true;
        this.cellDef = cellDef;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.container.show();
        this._renderSlashes(cellDef);
        this.initialized = false;
    }

    hide() {
        this.container.hide();
    }

    _renderSlashes(cellDef) {
        const self = this;
        this.slashList.empty();

        const value = cellDef.value;
        const slashes = (value && value.slashes) ? value.slashes : [];
        if (!value || !value.slashes) {
            if (value) value.slashes = [];
        }

        slashes.forEach((slash, idx) => {
            const card = $(`<div style="border:1px solid #f0f0f0;border-radius:6px;padding:8px;margin-bottom:6px"></div>`);

            // Header with delete
            const hdr = $(`<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:4px">
                <span style="font-size:11px;font-weight:600;color:#666">斜线 ${idx + 1}</span>
                <button class="btn btn-danger btn-sm" style="padding:1px 6px;font-size:10px">×</button>
            </div>`);
            hdr.find('button').on('click', function () {
                value.slashes.splice(idx, 1);
                self._renderSlashes(cellDef);
                self._refreshPreview();
                setDirty();
            });
            card.append(hdr);

            // Text
            const textRow = $(`<div style="margin-bottom:4px"><label style="font-size:11px;color:#666">文字</label></div>`);
            const textInput = $(`<input type="text" value="${slash.text || ''}">`);
            textInput.on('change', function () {
                slash.text = this.value;
                setDirty();
            });
            textRow.append(textInput);
            card.append(textRow);

            // X, Y, Degree
            const xydRow = $(`<div style="display:flex;gap:8px"></div>`);
            ['X', 'Y', '角度'].forEach((label, fi) => {
                const field = fi === 0 ? 'x' : (fi === 1 ? 'y' : 'degree');
                const grp = $(`<div style="flex:1"><label style="font-size:11px;color:#666">${label}</label></div>`);
                const input = $(`<input type="number" value="${slash[field] || 0}">`);
                input.on('change', function () {
                    slash[field] = parseFloat(this.value) || 0;
                    setDirty();
                });
                grp.append(input);
                xydRow.append(grp);
            });
            card.append(xydRow);
            this.slashList.append(card);
        });
    }

    _addSlash() {
        if (!this.cellDef) return;
        const value = this.cellDef.value;
        if (!value.slashes) value.slashes = [];
        value.slashes.push({ text: '', x: 0, y: 0, degree: 45, type: 'slash' });
        this._renderSlashes(this.cellDef);
        this._refreshPreview();
        setDirty();
    }

    _refreshPreview() {
        if (!this.cellDef) return;
        // Use existing CrossTabWidget for preview rendering
        const widget = this.cellDef.crossTabWidget;
        if (widget) {
            widget.refresh();
        } else {
            this.cellDef.crossTabWidget = new CrossTabWidget(
                this.context, this.rowIndex, this.colIndex
            );
        }
        if (this.context.hot) {
            this.context.hot.render();
        }
        setDirty();
    }
}
