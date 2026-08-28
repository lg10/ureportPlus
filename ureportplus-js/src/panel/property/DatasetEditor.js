/**
 * DatasetEditor v4.1 — wizard-style progressive disclosure.
 *
 * Fixed against v4.0:
 *  - Datasources come from context.reportDef.datasources (no ajax, the
 *    /designer/loadDatasources endpoint does not exist on the backend)
 *  - Property binding row is visible whenever a dataset is bound
 *  - Mapping section is reachable (shown for group/select/regroup/reselect)
 *  - Filter conditions support add/edit/delete via ConditionDialog
 *    (v4.0 called the dialog with a wrong signature)
 *  - Custom group aggregate gets a "配置自定义分组" button (CustomGroupDialog)
 *  - Sort row hidden for aggregate types (sum/count/max/min/avg), same as v3
 *  - Default aggregate is 'select' (列表), consistent with the original
 */
import uuid from 'node-uuid';
import {setDirty} from '../../Utils.js';
import {alert, confirm} from '../../MsgBox.js';
import ConditionDialog from '../../dialog/ConditionDialog.js';
import CustomGroupDialog from '../../dialog/CustomGroupDialog.js';
import MappingDialog from '../../dialog/MappingDialog.js';

export default class DatasetEditor {
    constructor(parentContainer, context) {
        this.context = context;
        this.container = $(`<div style="display:none"></div>`);
        parentContainer.append(this.container);
        this._init();
    }

    get datasources() {
        return (this.context.reportDef && this.context.reportDef.datasources) || [];
    }

    _init() {
        // Step 1: Dataset select + aggregate type + custom group button
        this.step1 = $(`<div style="margin-bottom:6px"></div>`);
        this._buildDatasetSelect();
        this._buildAggregateSelect();
        this.container.append(this.step1);

        // Step 2: Property binding
        this.step2 = $(`<div style="margin-bottom:6px;display:none"></div>`);
        this._buildPropertySelect();
        this.container.append(this.step2);

        // Step 3: Filter conditions (always visible, like v3)
        this.step3 = $(`<div style="margin-bottom:6px"></div>`);
        this._buildFilterSection();
        this.container.append(this.step3);

        // Step 4: Order (hidden for aggregate types)
        this.step4 = $(`<div style="margin-bottom:6px"></div>`);
        this._buildOrderSelect();
        this.container.append(this.step4);

        // Step 5: Data mapping (shown for group/select/regroup/reselect)
        this.step5 = $(`<div style="margin-bottom:6px"></div>`);
        this._buildMappingSection();
        this.container.append(this.step5);

        this.customGroupDialog = new CustomGroupDialog();
        this.mappingDialog = new MappingDialog();
    }

    _buildDatasetSelect() {
        const self = this;
        const row = $(`<div class="ud-prop-v4-field"><label>数据集</label></div>`);
        this.datasetSelect = $(`<select class="field-input"><option value="">请选择数据集</option></select>`);
        this.datasetSelect.on('change', function () {
            const dsName = $(this).val();
            self._populateFields(dsName);
            if (!self.initialized) {
                self._setDatasetName(dsName);
                const fieldNames = self._fieldsOf(dsName).map(f => f.name);
                const curProp = self.cellDef && self.cellDef.value ? self.cellDef.value.property : '';
                if (curProp && fieldNames.indexOf(curProp) < 0) {
                    self.propertySelect.val('');
                    self._setProperty('');
                }
                self.step2.toggle(!!dsName);
                self._updateTableData();
            }
        });
        row.append(this.datasetSelect);
        this.step1.append(row);
    }

    _buildAggregateSelect() {
        const self = this;
        const row = $(`<div class="ud-prop-v4-field"><label>聚合方式</label></div>`);
        this.aggregateSelect = $(`<select class="field-input" style="flex:1">
            <option value="select">列表</option>
            <option value="group">分组</option>
            <option value="customgroup">自定义分组</option>
            <option value="regroup">重新分组</option>
            <option value="reselect">重新选择</option>
            <option value="sum">求和</option>
            <option value="avg">平均值</option>
            <option value="max">最大值</option>
            <option value="min">最小值</option>
            <option value="count">计数</option>
        </select>`);
        this.aggregateSelect.on('change', function () {
            const agg = $(this).val();
            self._updateVisibility(agg);
            if (!self.initialized) {
                self._setAggregate(agg);
                self._updateTableData();
            }
        });
        row.append(this.aggregateSelect);

        // Custom group config button (shown only for customgroup)
        this.customGroupBtn = $(`<button class="btn btn-default btn-sm" style="display:none;flex-shrink:0">配置分组</button>`);
        this.customGroupBtn.on('click', () => {
            if (!self.cellDef || !self.cellDef.value) return;
            const fields = self._buildFields();
            if (!fields) return;
            if (!self.cellDef.value.groupItems) self.cellDef.value.groupItems = [];
            self.customGroupDialog.show(self.cellDef, fields);
            setDirty();
        });
        row.append(this.customGroupBtn);
        this.step1.append(row);
    }

    _buildPropertySelect() {
        const self = this;
        const row = $(`<div class="ud-prop-v4-field"><label>属性字段</label></div>`);
        this.propertySelect = $(`<select class="field-input"><option value="">选择属性</option></select>`);
        this.propertySelect.on('change', function () {
            if (!self.initialized) {
                self._setProperty($(this).val());
                self._updateTableData();
            }
        });
        row.append(this.propertySelect);
        this.step2.append(row);
    }

    _buildFilterSection() {
        const self = this;
        const hd = $(`<div class="ud-prop-v4-group-hd" style="padding:6px 0;border-bottom:1px solid #f0f0f0;margin-bottom:4px">
            <span class="arrow">▾</span><span class="label" style="font-size:11px">过滤条件</span>
        </div>`);
        this.filterBd = $(`<div></div>`);

        const btnRow = $(`<div style="display:flex;gap:4px;margin-bottom:4px"></div>`);
        const addBtn = $(`<button class="btn btn-default btn-sm">+ 添加</button>`);
        addBtn.on('click', () => self._addCondition());
        const editBtn = $(`<button class="btn btn-default btn-sm">编辑</button>`);
        editBtn.on('click', () => self._editCondition());
        const delBtn = $(`<button class="btn btn-default btn-sm">删除</button>`);
        delBtn.on('click', () => self._deleteCondition());
        btnRow.append(addBtn, editBtn, delBtn);
        this.filterBd.append(btnRow);

        this.conditionList = $(`<select size="3" style="width:100%;font-size:11px;border:1px solid #e5e5e5;border-radius:6px;padding:2px"></select>`);
        this.filterBd.append(this.conditionList);

        this.step3.append(hd, this.filterBd);
        this.filterFolded = false;
        hd.on('click', function () {
            self.filterFolded = !self.filterFolded;
            hd.find('.arrow').toggleClass('folded', self.filterFolded);
            self.filterBd.stop(true, true).slideToggle(120);
        });
    }

    _buildOrderSelect() {
        const self = this;
        const row = $(`<div class="ud-prop-v4-field"><label>排序</label></div>`);
        this.orderSelect = $(`<select class="field-input">
            <option value="none">无</option>
            <option value="asc">升序</option>
            <option value="desc">降序</option>
        </select>`);
        this.orderSelect.on('change', function () {
            if (!self.initialized) self._setOrder($(this).val());
        });
        row.append(this.orderSelect);
        this.step4.append(row);
    }

    _buildMappingSection() {
        const self = this;
        const hd = $(`<div class="ud-prop-v4-group-hd" style="padding:6px 0;border-bottom:1px solid #f0f0f0;margin-bottom:4px">
            <span class="arrow">▾</span><span class="label" style="font-size:11px">数据映射</span>
            <span style="font-size:10px;color:#999">(可选)</span>
        </div>`);
        this.mappingBd = $(`<div></div>`);

        // Mapping type
        const typeRow = $(`<div class="ud-prop-v4-field"><label>映射类型</label></div>`);
        this.mappingTypeSelect = $(`<select class="field-input">
            <option value="simple">简单映射</option>
            <option value="dataset">数据集映射</option>
        </select>`);
        this.mappingTypeSelect.on('change', function () {
            const t = $(this).val();
            self.simpleMappingGroup.toggle(t === 'simple');
            self.datasetMappingGroup.toggle(t === 'dataset');
            if (!self.initialized && self.cellDef && self.cellDef.value) {
                self.cellDef.value.mappingType = t;
                setDirty();
            }
        });
        typeRow.append(this.mappingTypeSelect);
        this.mappingBd.append(typeRow);

        // Simple mapping: item table
        this.simpleMappingGroup = $(`<div></div>`);
        const addMappingBtn = $(`<button class="btn btn-default btn-sm" style="margin-bottom:4px">+ 添加映射项</button>`);
        addMappingBtn.on('click', () => {
            const newItem = {value: '', label: ''};
            self.mappingDialog.show(function () {
                const v = self.cellDef && self.cellDef.value;
                if (!v) return;
                if (!v.mappingItems) v.mappingItems = [];
                v.mappingItems.push(newItem);
                self._renderMappingTable();
                setDirty();
            }, newItem, 'add');
        });
        this.simpleMappingGroup.append(addMappingBtn);
        this.mappingTableBody = $(`<div style="font-size:11px"></div>`);
        this.simpleMappingGroup.append(this.mappingTableBody);
        this.mappingBd.append(this.simpleMappingGroup);

        // Dataset mapping: dataset/key/value selects
        this.datasetMappingGroup = $(`<div style="display:none"></div>`);
        const mkRow = (label) => {
            const r = $(`<div class="ud-prop-v4-field"><label>${label}</label></div>`);
            const sel = $(`<select class="field-input"></select>`);
            r.append(sel);
            this.datasetMappingGroup.append(r);
            return sel;
        };
        this.mappingDatasetSelect = mkRow('数据集');
        this.mappingKeySelect = mkRow('键属性');
        this.mappingValueSelect = mkRow('值属性');
        this.mappingDatasetSelect.on('change', function () {
            const dsName = $(this).val();
            self._populateMappingFields(dsName);
            if (!self.initialized && self.cellDef && self.cellDef.value) {
                self.cellDef.value.mappingDataset = dsName;
                setDirty();
            }
        });
        this.mappingKeySelect.on('change', function () {
            if (!self.initialized && self.cellDef && self.cellDef.value) {
                self.cellDef.value.mappingKeyProperty = $(this).val();
                setDirty();
            }
        });
        this.mappingValueSelect.on('change', function () {
            if (!self.initialized && self.cellDef && self.cellDef.value) {
                self.cellDef.value.mappingValueProperty = $(this).val();
                setDirty();
            }
        });
        this.mappingBd.append(this.datasetMappingGroup);

        this.step5.append(hd, this.mappingBd);
        this.mappingFolded = true;
        hd.find('.arrow').addClass('folded');
        this.mappingBd.hide();
        hd.on('click', function () {
            self.mappingFolded = !self.mappingFolded;
            hd.find('.arrow').toggleClass('folded', self.mappingFolded);
            self.mappingBd.stop(true, true).slideToggle(120);
        });
    }

    // ---- Lifecycle ----

    show(cellDef, rowIndex, colIndex, row2Index, col2Index) {
        this.initialized = true;
        this.cellDef = cellDef;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.row2Index = row2Index;
        this.col2Index = col2Index;

        const v = cellDef.value || {};
        this.container.show();

        try {
            // Rebuild dataset select from report datasources
            this._rebuildDatasetSelect();
            this.datasetSelect.val(v.datasetName || '');

            // Aggregate ('select' is the original default)
            this.aggregateSelect.val(v.aggregate || 'select');

            // Property binding: visible whenever a dataset is bound
            this._populateFields(v.datasetName);
            this.step2.toggle(!!v.datasetName);
            this.propertySelect.val(v.property || '');

            // Visibility per aggregate
            this._updateVisibility(v.aggregate || 'select');

            // Order
            this.orderSelect.val(v.order || 'none');

            // Conditions list
            this._renderConditions(v);

            // Mapping state
            this._renderMapping(v);
        } finally {
            this.initialized = false;
        }
    }

    hide() {
        this.container.hide();
    }

    // ---- Internal ----

    _rebuildDatasetSelect() {
        const current = this.datasetSelect.val();
        this.datasetSelect.empty();
        this.datasetSelect.append(`<option value="">请选择数据集</option>`);
        this.datasources.forEach(ds => {
            (ds.datasets || []).forEach(d => {
                this.datasetSelect.append(`<option value="${d.name}">${d.name}</option>`);
            });
        });
        if (current) this.datasetSelect.val(current);
    }

    _updateVisibility(agg) {
        const isAggregateType = ['sum', 'count', 'max', 'min', 'avg'].indexOf(agg) >= 0;
        this.step4.toggle(!isAggregateType);
        const showMapping = ['group', 'select', 'regroup', 'reselect'].indexOf(agg) >= 0;
        this.step5.toggle(showMapping);
        this.customGroupBtn.toggle(agg === 'customgroup');
    }

    _populateFields(dsName) {
        this.propertySelect.empty();
        this.propertySelect.append(`<option value="">选择属性</option>`);
        this._fieldsOf(dsName).forEach(f => {
            this.propertySelect.append(`<option value="${f.name}">${f.name}</option>`);
        });
    }

    _populateMappingFields(dsName) {
        [this.mappingKeySelect, this.mappingValueSelect].forEach(sel => {
            sel.empty();
            sel.append(`<option value=""></option>`);
        });
        this._fieldsOf(dsName).forEach(f => {
            this.mappingKeySelect.append(`<option value="${f.name}">${f.name}</option>`);
            this.mappingValueSelect.append(`<option value="${f.name}">${f.name}</option>`);
        });
    }

    _fieldsOf(dsName) {
        if (!dsName) return [];
        for (const ds of this.datasources) {
            for (const d of (ds.datasets || [])) {
                if (d.name === dsName) {
                    return d.fields || [];
                }
            }
        }
        return [];
    }

    _buildFields() {
        const dsName = this.datasetSelect.val();
        if (!dsName) {
            alert('请先绑定数据集');
            return null;
        }
        return this._fieldsOf(dsName);
    }

    // ---- Filter conditions ----

    _renderConditions(v) {
        this.conditionList.empty();
        const conds = v.conditions || [];
        conds.forEach(c => {
            if (!c.id) c.id = uuid.v1();
            let text = c.left + ' ' + c.operation + ' ' + c.right;
            if (c.join) text = c.join + ' ' + text;
            const option = $(`<option></option>`).text(text);
            option.data(c);
            this.conditionList.append(option);
        });
    }

    _conditions() {
        if (this.cellDef && this.cellDef.value) {
            if (!this.cellDef.value.conditions) this.cellDef.value.conditions = [];
            return this.cellDef.value.conditions;
        }
        return null;
    }

    _addCondition() {
        const fields = this._buildFields();
        if (!fields) return;
        const conditions = this._conditions();
        if (!conditions) return;
        const self = this;
        const dlg = new ConditionDialog(conditions);
        dlg.show(function (left, op, right, join) {
            const c = {left, operation: op, right, join, id: uuid.v1()};
            conditions.push(c);
            self._renderConditions(self.cellDef.value);
            setDirty();
        }, fields);
    }

    _editCondition() {
        const option = this.conditionList.find('option:selected');
        if (option.length === 0) {
            alert('请先选择要编辑的条件');
            return;
        }
        const fields = this._buildFields();
        if (!fields) return;
        const conditions = this._conditions();
        if (!conditions) return;
        const condition = option.data();
        const self = this;
        const dlg = new ConditionDialog(conditions);
        dlg.show(function (left, op, right, join) {
            const target = conditions.find(c => c.id === condition.id);
            if (target) {
                target.left = left;
                target.operation = op;
                target.right = right;
                target.join = join;
            }
            self._renderConditions(self.cellDef.value);
            setDirty();
        }, fields, condition);
    }

    _deleteCondition() {
        const option = this.conditionList.find('option:selected');
        if (option.length === 0) {
            alert('请先选择要删除的条件');
            return;
        }
        const conditions = this._conditions();
        if (!conditions) return;
        const condition = option.data();
        const index = conditions.findIndex(c => c.id === condition.id);
        if (index >= 0) conditions.splice(index, 1);
        this._renderConditions(this.cellDef.value);
        setDirty();
    }

    // ---- Mapping ----

    _renderMapping(v) {
        const mappingType = v.mappingType && v.mappingType !== 'simple' ? 'dataset' : 'simple';
        this.mappingTypeSelect.val(mappingType);
        this.simpleMappingGroup.toggle(mappingType === 'simple');
        this.datasetMappingGroup.toggle(mappingType === 'dataset');

        this._renderMappingTable();

        // Dataset mapping selects
        this.mappingDatasetSelect.empty();
        this.mappingDatasetSelect.append(`<option value=""></option>`);
        this.datasources.forEach(ds => {
            (ds.datasets || []).forEach(d => {
                this.mappingDatasetSelect.append(`<option value="${d.name}">${d.name}</option>`);
            });
        });
        this.mappingDatasetSelect.val(v.mappingDataset || '');
        this._populateMappingFields(v.mappingDataset || '');
        this.mappingKeySelect.val(v.mappingKeyProperty || '');
        this.mappingValueSelect.val(v.mappingValueProperty || '');
    }

    _renderMappingTable() {
        this.mappingTableBody.empty();
        const v = this.cellDef && this.cellDef.value;
        if (!v) return;
        const items = v.mappingItems || [];
        if (!items.length) {
            this.mappingTableBody.append(`<div style="color:#999;margin-bottom:4px">无映射项</div>`);
            return;
        }
        const self = this;
        items.forEach(item => {
            const row = $(`<div style="display:flex;align-items:center;gap:6px;margin-bottom:3px"></div>`);
            row.append(`<span style="flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${item.value} → ${item.label}</span>`);
            const editLink = $(`<button class="btn btn-default btn-sm" style="flex-shrink:0">编辑</button>`);
            editLink.on('click', function () {
                self.mappingDialog.show(function () {
                    self._renderMappingTable();
                    setDirty();
                }, item, 'edit');
            });
            const delLink = $(`<button class="btn btn-danger btn-sm" style="flex-shrink:0">×</button>`);
            delLink.on('click', function () {
                confirm('确定删除该映射项?', function () {
                    const index = v.mappingItems.indexOf(item);
                    if (index >= 0) v.mappingItems.splice(index, 1);
                    self._renderMappingTable();
                    setDirty();
                });
            });
            row.append(editLink, delLink);
            this.mappingTableBody.append(row);
        });
    }

    // ---- Multi-cell setters ----

    _updateTableData() {
        const hot = this.context.hot;
        if (!hot) return;
        for (let i = this.rowIndex; i <= this.row2Index; i++) {
            for (let j = this.colIndex; j <= this.col2Index; j++) {
                const cd = this.context.getCell(i, j);
                if (!cd || !cd.value || cd.value.type !== 'dataset') continue;
                const v = cd.value;
                const data = v.datasetName + '.' + v.aggregate + '(' + (v.property || '') + ')';
                hot.setDataAtCell(i, j, data, 'panel');
            }
        }
        hot.render();
    }

    _forEachDatasetCell(fn) {
        for (let i = this.rowIndex; i <= this.row2Index; i++) {
            for (let j = this.colIndex; j <= this.col2Index; j++) {
                const cd = this.context.getCell(i, j);
                if (!cd || !cd.value || cd.value.type !== 'dataset') continue;
                fn(cd);
            }
        }
        setDirty();
    }

    _setDatasetName(dsName) {
        this._forEachDatasetCell(cd => { cd.value.datasetName = dsName; });
    }

    _setAggregate(agg) {
        this._forEachDatasetCell(cd => { cd.value.aggregate = agg; });
    }

    _setProperty(prop) {
        this._forEachDatasetCell(cd => { cd.value.property = prop; });
    }

    _setOrder(order) {
        this._forEachDatasetCell(cd => { cd.value.order = order; });
    }
}
