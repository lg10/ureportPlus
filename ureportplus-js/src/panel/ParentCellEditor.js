/**
 * ParentCellEditor — left/top parent cell selector.
 * Radio toggle between 默认 (default) and 自定义 (custom),
 * with column letter + row number dual selects for custom mode.
 */
import {setDirty} from '../Utils.js';

export default class ParentCellEditor {
    /**
     * @param {string} label - "左父格" or "上父格"
     * @param {string} propPath - "leftParentCellName" or "topParentCellName"
     * @param {Object} state - PropertyState instance
     */
    constructor(label, propPath, state) {
        this.label = label;
        this.propPath = propPath;
        this.state = state;
    }

    render(container) {
        const self = this;
        const state = this.state;
        const value = state.get(this.propPath) || null; // null = default, "root" = root, "A1" = specific
        const isCustom = value !== null && value !== undefined;
        const mixed = state.isMixed(this.propPath);

        const row = $(`<div class="ud-prop-v4-field"></div>`);
        row.append(`<label>${this.label}</label>`);

        const wrapper = $(`<div class="field-input"></div>`);

        // Radio toggle row
        const radioRow = $(`<div style="display:flex;gap:10px;margin-bottom:4px"></div>`);

        const defaultId = `ud-parent-default-${this.propPath.replace(/\./g, '-')}`;
        const customId = `ud-parent-custom-${this.propPath.replace(/\./g, '-')}`;

        const defaultLabel = $(`<label style="font-size:11px;font-weight:400;color:#666;cursor:pointer">
            <input type="radio" name="ud-parent-${this.propPath}" id="${defaultId}" value="default" ${!isCustom && !mixed ? 'checked' : ''}> 默认
        </label>`);
        defaultLabel.find('input').on('change', function () {
            if (this.checked) {
                self.colSelect.prop('disabled', true);
                self.rowSelect.prop('disabled', true);
                state.set(self.propPath, undefined);
            }
        });

        const customLabel = $(`<label style="font-size:11px;font-weight:400;color:#666;cursor:pointer">
            <input type="radio" name="ud-parent-${this.propPath}" id="${customId}" value="custom" ${isCustom ? 'checked' : ''}> 自定义
        </label>`);
        customLabel.find('input').on('change', function () {
            if (this.checked) {
                self.colSelect.prop('disabled', false);
                self.rowSelect.prop('disabled', false);
                // Set initial value
                const col = self.colSelect.val();
                const row = self.rowSelect.val();
                if (col === 'root') {
                    state.set(self.propPath, 'root');
                } else if (col && row) {
                    state.set(self.propPath, col + row);
                }
            }
        });

        radioRow.append(defaultLabel, customLabel);
        wrapper.append(radioRow);

        // Selects row
        const selectRow = $(`<div style="display:flex;gap:4px"></div>`);

        // Column select
        this.colSelect = $(`<select style="flex:1;min-width:0;font-size:11px" ${isCustom ? '' : 'disabled'}>
            <option value="root">无</option>
        </select>`);
        const hot = state.context.hot;
        if (hot) {
            for (let j = 0; j < hot.countCols(); j++) {
                const name = state.context.getCellName(null, j);
                this.colSelect.append(`<option value="${name}">${name}</option>`);
            }
        }
        this.colSelect.on('change', function () {
            const col = $(this).val();
            if (col === 'root') {
                state.set(self.propPath, 'root');
            } else {
                const r = self.rowSelect.val();
                if (r) state.set(self.propPath, col + r);
            }
        });

        // Row select
        this.rowSelect = $(`<select style="width:50px;flex-shrink:0;font-size:11px" ${isCustom ? '' : 'disabled'}>
            <option value=""></option>
        </select>`);
        if (hot) {
            for (let j = 0; j < hot.countRows(); j++) {
                this.rowSelect.append(`<option value="${j + 1}">${j + 1}</option>`);
            }
        }
        this.rowSelect.on('change', function () {
            const r = $(this).val();
            const col = self.colSelect.val();
            if (col && col !== 'root' && r) {
                state.set(self.propPath, col + r);
            }
        });

        // Set current values
        if (isCustom && value) {
            if (value === 'root') {
                this.colSelect.val('root');
            } else {
                // Parse "A3" into column letter and row number
                const match = value.match(/^([A-Z]+)(\d+)$/);
                if (match) {
                    this.colSelect.val(match[1]);
                    this.rowSelect.val(match[2]);
                }
            }
        }

        selectRow.append(this.colSelect, this.rowSelect);
        wrapper.append(selectRow);
        row.append(wrapper);
        container.append(row);
    }
}
