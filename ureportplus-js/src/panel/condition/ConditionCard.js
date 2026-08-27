/**
 * ConditionCard — a single condition rule card.
 * Collapsible card with:
 *   - Condition builder (当...)
 *   - Actions (则...): display value, style overrides, row/col height, link, paging
 */
import ConditionBuilder from './ConditionBuilder.js';
import ConditionActions from './ConditionActions.js';

export default class ConditionCard {
    constructor(parentContainer, context) {
        this.parentContainer = parentContainer;
        this.context = context;
        this.card = null;
        this.body = null;
        this.folded = false;
        this.builder = null;
        this.actions = null;
    }

    show(item, index, onChange, onDelete) {
        this.item = item;
        this.index = index;
        this.onChange = onChange;
        this.onDelete = onDelete;
        this._render();
    }

    _render() {
        const self = this;
        const item = this.item;

        this.card = $(`<div style="border:1px solid #e5e5e5;border-radius:8px;margin-bottom:6px;overflow:hidden"></div>`);

        // Header
        const hdr = $(`<div style="display:flex;align-items:center;padding:8px 10px;background:#fafafa;cursor:pointer;gap:8px">
            <span class="arrow" style="font-size:8px;color:#999;transition:transform .2s">▾</span>
            <input type="text" value="${this._esc(item.name || '')}" placeholder="规则名称"
                style="flex:1;border:none;background:transparent;font-size:12px;font-weight:600;color:#333;outline:none;padding:0">
            <button class="btn btn-danger btn-sm" style="padding:1px 6px;font-size:10px;flex-shrink:0">×</button>
        </div>`);

        // Name change
        hdr.find('input').on('change', function () {
            item.name = this.value;
            if (self.onChange) self.onChange(item);
        });

        // Delete
        hdr.find('button').on('click', function (e) {
            e.stopPropagation();
            if (self.onDelete) self.onDelete();
        });

        // Toggle fold
        hdr.on('click', function (e) {
            if (e.target.tagName === 'INPUT' || e.target.tagName === 'BUTTON') return;
            self.folded = !self.folded;
            hdr.find('.arrow').css('transform', self.folded ? 'rotate(-90deg)' : '');
            self.body.stop(true, true).slideToggle(120);
        });

        // Body
        this.body = $(`<div style="padding:8px 10px"></div>`);

        // Condition builder (当...)
        const whenSection = $(`<div style="margin-bottom:8px">
            <div style="font-size:11px;font-weight:600;color:#666;margin-bottom:4px">当</div>
        </div>`);
        this.builder = new ConditionBuilder(whenSection, this.context);
        this.builder.show(item);
        this.body.append(whenSection);

        // Actions (则...)
        const thenSection = $(`<div>
            <div style="font-size:11px;font-weight:600;color:#666;margin-bottom:4px">则</div>
        </div>`);
        this.actions = new ConditionActions(thenSection, this.context);
        this.actions.show(item, (updated) => {
            Object.assign(item, updated);
            if (self.onChange) self.onChange(item);
        });
        this.body.append(thenSection);

        this.card.append(hdr, this.body);
        this.parentContainer.append(this.card);
    }

    _esc(s) {
        if (!s) return '';
        return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    hide() {
        if (this.card) this.card.remove();
    }
}
