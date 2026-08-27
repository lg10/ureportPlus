/**
 * ConditionGroup — inline condition properties editor.
 * Renders a list of condition rule cards inside the property panel.
 *
 * Replaces the 60KB PropertyConditionDialog popup with inline editing.
 */
import ConditionCard from './ConditionCard.js';
import {setDirty} from '../../Utils.js';

export default class ConditionGroup {
    /**
     * @param {jQuery} container - the group body element
     * @param {Object} context - the application context
     */
    constructor(container, context) {
        this.container = container;
        this.context = context;
        this.cards = [];
        this.cellDef = null;
        this.datasources = [];
        this.datasetName = '';
    }

    /**
     * Render conditions for the given cell.
     */
    show(cellDef, datasources, datasetName) {
        this.cellDef = cellDef;
        this.datasources = datasources || [];
        this.datasetName = datasetName || '';
        // Guarantee the array exists on the cellDef so all edits below
        // write through the same reference (XML serialization reads it)
        if (cellDef && !cellDef.conditionPropertyItems) {
            cellDef.conditionPropertyItems = [];
        }
        this._render();
    }

    hide() {
        this.container.empty();
        this.cards = [];
    }

    _render() {
        this.container.empty();
        this.cards = [];

        if (!this.cellDef) return;

        // Must be the cellDef's own array — never a detached fallback
        const items = this.cellDef.conditionPropertyItems;
        const self = this;

        // Condition list
        const list = $(`<div class="ud-cond-list"></div>`);
        this.container.append(list);

        items.forEach((item, idx) => {
            const card = new ConditionCard(list, this.context);
            card.show(item, idx, (updatedItem) => {
                items[idx] = updatedItem;
                setDirty();
            }, () => {
                // Delete
                items.splice(idx, 1);
                self._render();
                setDirty();
            });
            this.cards.push(card);
        });

        // Add button
        const addRow = $(`<div style="margin-top:6px"></div>`);
        const addBtn = $(`<button class="btn btn-default btn-sm">+ 添加规则</button>`);
        addBtn.on('click', () => {
            if (!self.cellDef.conditionPropertyItems) self.cellDef.conditionPropertyItems = [];
            const newItem = {
                name: '规则 ' + (items.length + 1),
                conditions: [],
                cellStyle: {},
                rowHeight: -1,
                colWidth: -1
            };
            items.push(newItem);
            self._render();
            setDirty();
        });
        addRow.append(addBtn);
        this.container.append(addRow);
    }
}
