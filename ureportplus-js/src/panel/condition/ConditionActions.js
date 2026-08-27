/**
 * ConditionActions — inline condition action configuration.
 * Handles: display value override, style overrides (with scope),
 *          row/col height, link, paging.
 */
import {setDirty} from '../../Utils.js';

const STYLE_PROPS = [
    { key: 'bgcolor', label: '背景色', type: 'color' },
    { key: 'forecolor', label: '前景色', type: 'color' },
    { key: 'fontSize', label: '字号', type: 'number' },
    { key: 'fontFamily', label: '字体', type: 'text' },
    { key: 'bold', label: '粗体', type: 'toggle' },
    { key: 'italic', label: '斜体', type: 'toggle' },
    { key: 'underline', label: '下划线', type: 'toggle' },
    { key: 'align', label: '对齐', type: 'select',
      options: ['left','center','right'] },
    { key: 'valign', label: '垂直对齐', type: 'select',
      options: ['top','middle','bottom'] }
];

const SCOPES = [
    { value: 'cell', label: '单元格' },
    { value: 'row', label: '整行' },
    { value: 'column', label: '整列' }
];

export default class ConditionActions {
    constructor(container, context) {
        this.container = container;
        this.context = context;
        this.item = null;
    }

    show(item, onChange) {
        this.item = item;
        this.onChange = onChange;
        this._render();
    }

    _render() {
        const self = this;
        const item = this.item;
        const cellStyle = item.cellStyle || {};
        if (!item.cellStyle) item.cellStyle = cellStyle;

        // Display value override
        const valRow = $(`<div style="margin-bottom:4px;display:flex;align-items:center;gap:6px">
            <span style="font-size:11px;color:#666;flex-shrink:0">显示值:</span>
        </div>`);
        const valInput = $(`<input type="text" value="${self._esc(item.newValue || '')}" placeholder="(不覆盖)"
            style="flex:1;padding:3px 6px;border:1px solid #e5e5e5;border-radius:4px;font-size:11px">`);
        valInput.on('change', function () {
            item.newValue = this.value || undefined;
            if (self.onChange) self.onChange(item);
            setDirty();
        });
        valRow.append(valInput);
        this.container.append(valRow);

        // Row/Col height
        const sizeRow = $(`<div style="display:flex;gap:8px;margin-bottom:4px"></div>`);
        ['rowHeight', 'colWidth'].forEach((key, idx) => {
            const label = idx === 0 ? '行高' : '列宽';
            const grp = $(`<div style="flex:1"><span style="font-size:11px;color:#666">${label}:</span></div>`);
            const input = $(`<input type="number" value="${item[key] >= 0 ? item[key] : ''}" placeholder="(不变)"
                style="width:100%;padding:3px 6px;border:1px solid #e5e5e5;border-radius:4px;font-size:11px">`);
            input.on('change', function () {
                const v = this.value === '' ? -1 : parseInt(this.value, 10);
                item[key] = v;
                if (self.onChange) self.onChange(item);
                setDirty();
            });
            grp.append(input);
            sizeRow.append(grp);
        });
        this.container.append(sizeRow);

        // Style overrides
        const styleSection = $(`<div style="margin-bottom:4px"></div>`);
        const styleHdr = $(`<div style="font-size:11px;font-weight:600;color:#666;margin-bottom:3px;display:flex;align-items:center;justify-content:space-between">
            <span>样式覆盖</span>
            <button class="btn btn-default btn-sm" style="font-size:10px;padding:1px 6px">+ 样式</button>
        </div>`);
        styleSection.append(styleHdr);

        const styleList = $(`<div></div>`);
        styleSection.append(styleList);

        // Track which style props are active
        const activeProps = {};
        STYLE_PROPS.forEach(p => {
            const scopeKey = p.key + 'Scope';
            if (cellStyle[p.key] !== undefined || cellStyle[scopeKey] !== undefined) {
                activeProps[p.key] = true;
            }
        });

        const renderStyleItems = () => {
            styleList.empty();
            STYLE_PROPS.forEach(prop => {
                if (!activeProps[prop.key]) return;
                const row = $(`<div style="display:flex;align-items:center;gap:4px;margin-bottom:3px;font-size:11px"></div>`);
                row.append(`<span style="width:50px;color:#999;flex-shrink:0">${prop.label}:</span>`);

                let valueInput;
                if (prop.type === 'color') {
                    valueInput = $(`<input type="text" value="${self._esc(cellStyle[prop.key] || '')}" placeholder="如 255,0,0"
                        style="flex:1;min-width:0;padding:2px 4px;border:1px solid #e5e5e5;border-radius:4px;font-size:11px">`);
                } else if (prop.type === 'toggle') {
                    valueInput = $(`<input type="checkbox" ${cellStyle[prop.key] ? 'checked' : ''}
                        style="flex-shrink:0">`);
                } else if (prop.type === 'select') {
                    valueInput = $(`<select style="flex:1;min-width:0;padding:2px;border:1px solid #e5e5e5;border-radius:4px;font-size:11px">
                        <option value="">-</option></select>`);
                    (prop.options || []).forEach(o => {
                        valueInput.append(`<option value="${o}" ${cellStyle[prop.key] === o ? 'selected' : ''}>${o}</option>`);
                    });
                } else {
                    valueInput = $(`<input type="${prop.type}" value="${self._esc(cellStyle[prop.key] || '')}" placeholder="${prop.label}"
                        style="flex:1;min-width:0;padding:2px 4px;border:1px solid #e5e5e5;border-radius:4px;font-size:11px">`);
                }

                const onChangeValue = function () {
                    const val = prop.type === 'toggle' ? this.checked : this.value;
                    cellStyle[prop.key] = val || undefined;
                    if (self.onChange) self.onChange(item);
                    setDirty();
                };
                valueInput.on(prop.type === 'toggle' ? 'change' : 'change', onChangeValue);
                row.append(valueInput);

                // Scope
                const scopeKey = prop.key + 'Scope';
                const scopeSelect = $(`<select style="width:55px;font-size:10px;padding:1px;border:1px solid #e5e5e5;border-radius:4px;flex-shrink:0"></select>`);
                SCOPES.forEach(s => {
                    scopeSelect.append(`<option value="${s.value}" ${cellStyle[scopeKey] === s.value ? 'selected' : ''}>${s.label}</option>`);
                });
                scopeSelect.on('change', function () {
                    cellStyle[scopeKey] = this.value;
                    if (self.onChange) self.onChange(item);
                    setDirty();
                });
                row.append(scopeSelect);

                // Remove
                const rmBtn = $(`<button style="padding:0 4px;font-size:10px;border:none;background:none;color:#999;cursor:pointer;flex-shrink:0">×</button>`);
                rmBtn.on('click', () => {
                    delete activeProps[prop.key];
                    delete cellStyle[prop.key];
                    delete cellStyle[scopeKey];
                    renderStyleItems();
                    if (self.onChange) self.onChange(item);
                    setDirty();
                });
                row.append(rmBtn);

                styleList.append(row);
            });
        };
        renderStyleItems();

        // Add style button — dropdown menu instead of raw prompt()
        styleHdr.find('button').on('click', function (e) {
            e.stopPropagation();
            $('.ud-style-add-menu').remove();
            const menu = $(`<div class="ud-style-add-menu"></div>`).css({
                position: 'fixed', zIndex: 99999, background: '#fff',
                border: '1px solid #e5e5e5', borderRadius: '6px',
                boxShadow: '0 4px 12px rgba(0,0,0,.12)', padding: '4px 0',
                fontSize: '11px', minWidth: '110px'
            });
            const candidates = STYLE_PROPS.filter(p => !activeProps[p.key]);
            if (!candidates.length) {
                menu.append(`<div style="padding:4px 12px;color:#999">已全部添加</div>`);
            }
            candidates.forEach(p => {
                const mi = $(`<div></div>`).text(p.label).css({ padding: '4px 12px', cursor: 'pointer' });
                mi.on('mouseenter', () => mi.css('background', '#f0f6ff'));
                mi.on('mouseleave', () => mi.css('background', ''));
                mi.on('click', () => {
                    activeProps[p.key] = true;
                    if (cellStyle[p.key] === undefined) {
                        cellStyle[p.key] = p.type === 'toggle' ? true : '';
                    }
                    renderStyleItems();
                    if (self.onChange) self.onChange(item);
                    setDirty();
                    menu.remove();
                });
                menu.append(mi);
            });
            $('body').append(menu);
            const rect = e.currentTarget.getBoundingClientRect();
            menu.css({ left: rect.left + 'px', top: (rect.bottom + 2) + 'px' });
            setTimeout(() => {
                $(document).one('mousedown.udStyleMenu', function (ev) {
                    if (!menu.has(ev.target).length) menu.remove();
                });
            }, 0);
        });

        this.container.append(styleSection);

        // Link (collapsed)
        const linkSection = $(`<div style="margin-bottom:4px"></div>`);
        const linkHdr = $(`<div style="font-size:11px;font-weight:600;color:#666;cursor:pointer;user-select:none">
            <span class="arrow" style="font-size:8px;transition:transform .2s">▸</span> 链接
        </div>`);
        const linkBody = $(`<div style="display:none;padding-top:4px"></div>`);
        const linkUrlInput = $(`<input type="text" value="${self._esc(item.linkUrl || '')}" placeholder="URL"
            style="width:100%;padding:3px 6px;border:1px solid #e5e5e5;border-radius:4px;font-size:11px;margin-bottom:3px">`);
        linkUrlInput.on('change', function () {
            item.linkUrl = this.value || undefined;
            if (self.onChange) self.onChange(item);
            setDirty();
        });
        linkBody.append(linkUrlInput);
        linkSection.append(linkHdr, linkBody);
        linkHdr.on('click', () => {
            const folded = linkBody.is(':visible');
            linkHdr.find('.arrow').css('transform', folded ? '' : 'rotate(90deg)');
            linkBody.stop(true, true).slideToggle(100);
        });
        this.container.append(linkSection);

        // Paging (collapsed)
        const pagingSection = $(`<div></div>`);
        const pagingHdr = $(`<div style="font-size:11px;font-weight:600;color:#666;cursor:pointer;user-select:none">
            <span class="arrow" style="font-size:8px;transition:transform .2s">▸</span> 分页
        </div>`);
        const pagingBody = $(`<div style="display:none;padding-top:4px"></div>`);
        const pagingSelect = $(`<select style="width:100%;padding:3px 6px;border:1px solid #e5e5e5;border-radius:4px;font-size:11px">
            <option value="">无</option>
            <option value="before" ${(item.paging && item.paging.position) === 'before' ? 'selected' : ''}>前置分页</option>
            <option value="after" ${(item.paging && item.paging.position) === 'after' ? 'selected' : ''}>后置分页</option>
        </select>`);
        pagingSelect.on('change', function () {
            if (this.value) {
                if (!item.paging) item.paging = {};
                item.paging.position = this.value;
            } else {
                item.paging = undefined;
            }
            if (self.onChange) self.onChange(item);
            setDirty();
        });
        pagingBody.append(pagingSelect);
        pagingSection.append(pagingHdr, pagingBody);
        pagingHdr.on('click', () => {
            const folded = pagingBody.is(':visible');
            pagingHdr.find('.arrow').css('transform', folded ? '' : 'rotate(90deg)');
            pagingBody.stop(true, true).slideToggle(100);
        });
        this.container.append(pagingSection);
    }

    _esc(s) {
        if (!s) return '';
        return ('' + s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
}
