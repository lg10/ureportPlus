/**
 * ConditionBuilder — inline condition expression builder.
 * Builds a list of condition lines (property + operator + value) with AND/OR joining.
 */
import {setDirty} from '../../Utils.js';

const OPERATORS = [
    { value: 'eq', label: '等于' },
    { value: 'ne', label: '不等于' },
    { value: 'gt', label: '大于' },
    { value: 'gte', label: '大于等于' },
    { value: 'lt', label: '小于' },
    { value: 'lte', label: '小于等于' },
    { value: 'like', label: '包含' },
    { value: 'in', label: '在...中' },
    { value: 'notin', label: '不在...中' }
];

export default class ConditionBuilder {
    constructor(container, context) {
        this.container = container;
        this.context = context;
        this.item = null;
    }

    show(item) {
        this.item = item;
        this._render();
    }

    _render() {
        const self = this;
        const item = this.item;
        const conditions = item.conditions || [];

        // Join type selector
        const joinRow = $(`<div style="display:flex;align-items:center;gap:6px;margin-bottom:4px"></div>`);
        const joinSelect = $(`<select style="width:60px;font-size:11px;padding:2px 4px;border:1px solid #e5e5e5;border-radius:4px">
            <option value="and" ${(item.join || 'and') === 'and' ? 'selected' : ''}>AND</option>
            <option value="or" ${(item.join || 'and') === 'or' ? 'selected' : ''}>OR</option>
        </select>`);
        joinSelect.on('change', function () {
            item.join = this.value;
            setDirty();
        });
        joinRow.append($(`<span style="font-size:10px;color:#999">连接方式:</span>`), joinSelect);
        this.container.append(joinRow);

        // Condition lines
        const linesContainer = $(`<div></div>`);
        this.container.append(linesContainer);

        const renderLines = () => {
            linesContainer.empty();
            (conditions || []).forEach((cond, idx) => {
                const row = $(`<div style="display:flex;align-items:center;gap:4px;margin-bottom:3px"></div>`);

                // Property
                const propInput = $(`<input type="text" value="${self._esc(cond.property || '')}" placeholder="属性"
                    style="flex:1;min-width:0;padding:3px 6px;border:1px solid #e5e5e5;border-radius:4px;font-size:11px">`);
                propInput.on('change', function () {
                    cond.property = this.value;
                    setDirty();
                });
                row.append(propInput);

                // Operator
                const opSelect = $(`<select style="width:70px;font-size:11px;padding:2px;border:1px solid #e5e5e5;border-radius:4px"></select>`);
                OPERATORS.forEach(op => {
                    opSelect.append(`<option value="${op.value}" ${cond.op === op.value ? 'selected' : ''}>${op.label}</option>`);
                });
                opSelect.on('change', function () {
                    cond.op = this.value;
                    setDirty();
                });
                row.append(opSelect);

                // Value
                const valInput = $(`<input type="text" value="${self._esc(cond.value || '')}" placeholder="值"
                    style="flex:1;min-width:0;padding:3px 6px;border:1px solid #e5e5e5;border-radius:4px;font-size:11px">`);
                valInput.on('change', function () {
                    cond.value = this.value;
                    setDirty();
                });
                row.append(valInput);

                // Delete
                const delBtn = $(`<button style="padding:1px 5px;font-size:10px;border:1px solid #fecaca;background:#fff;color:#ef4444;border-radius:4px;cursor:pointer;flex-shrink:0">×</button>`);
                delBtn.on('click', () => {
                    conditions.splice(idx, 1);
                    renderLines();
                    setDirty();
                });
                row.append(delBtn);

                linesContainer.append(row);
            });
        };

        renderLines();

        // Add condition button
        const addBtn = $(`<button class="btn btn-default btn-sm" style="font-size:10px;padding:2px 8px">+ 添加条件</button>`);
        addBtn.on('click', () => {
            if (!item.conditions) item.conditions = [];
            item.conditions.push({ property: '', op: 'eq', value: '' });
            renderLines();
            setDirty();
        });
        this.container.append(addBtn);
    }

    _esc(s) {
        if (!s) return '';
        return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
}
