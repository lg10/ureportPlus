/**
 * PropertyRenderer — renders the grouped property list.
 * Each group is a collapsible section with fields or a custom render function.
 * Supports mixed-state display and reactive re-rendering via PropertyState.
 */
import {setDirty} from '../Utils.js';
import ParentCellEditor from './ParentCellEditor.js';

// ---- Group Definitions ----

export const PROPERTY_GROUPS = [
    {
        id: 'content',
        label: '内容',
        icon: '',
        defaultExpanded: true,
        custom: true  // rendered by ContentTypeEditor
    },
    {
        id: 'text-style',
        label: '文字与颜色',
        icon: '',
        defaultExpanded: true,
        fields: [
            { path: 'cellStyle.fontFamily', type: 'font-select', label: '字体' },
            { path: 'cellStyle.fontSize',     type: 'number', label: '字号', min: 1, max: 999, width: '60px' },
            { path: 'cellStyle.bold',         type: 'toggle', label: '粗体', icon: 'B' },
            { path: 'cellStyle.italic',       type: 'toggle', label: '斜体', icon: 'I' },
            { path: 'cellStyle.underline',    type: 'toggle', label: '下划线', icon: 'U' },
            { path: 'cellStyle.forecolor',    type: 'color', label: '文字颜色' },
            { path: 'cellStyle.bgcolor',      type: 'color', label: '背景色' },
            { path: 'cellStyle.format',       type: 'format-input', label: '格式', placeholder: '如 yyyy-MM-dd' },
            { path: 'cellStyle.lineHeight',   type: 'number', label: '行高', min: 0.1, max: 10, step: 0.1, width: '60px' }
        ]
    },
    {
        id: 'alignment-border',
        label: '对齐与边框',
        icon: '',
        defaultExpanded: true,
        fields: [
            { path: 'cellStyle.align',  type: 'align-buttons', label: '水平对齐', options: ['left','center','right'] },
            { path: 'cellStyle.valign', type: 'align-buttons', label: '垂直对齐', options: ['top','middle','bottom'] },
            { path: 'cellStyle.leftBorder',   type: 'border-editor', label: '左边框' },
            { path: 'cellStyle.rightBorder',  type: 'border-editor', label: '右边框' },
            { path: 'cellStyle.topBorder',    type: 'border-editor', label: '上边框' },
            { path: 'cellStyle.bottomBorder', type: 'border-editor', label: '下边框' }
        ]
    },
    {
        id: 'layout',
        label: '布局',
        icon: '',
        defaultExpanded: false,
        fields: [
            { path: 'expand', type: 'select', label: '展开方向',
              options: [
                  { value: 'None', label: '无' },
                  { value: 'Right', label: '向右' },
                  { value: 'Down', label: '向下' }
              ]},
            { path: 'leftParentCellName', type: 'parent-cell-ref', label: '左父格' },
            { path: 'topParentCellName',  type: 'parent-cell-ref', label: '上父格' }
        ]
    },
    {
        id: 'link',
        label: '链接',
        icon: '',
        defaultExpanded: false,
        fields: [
            { path: 'linkUrl', type: 'text', label: 'URL', placeholder: '支持表达式，如 &A1' },
            { path: 'linkTargetWindow', type: 'select', label: '打开方式',
              options: [
                  { value: '_self', label: '当前窗口' },
                  { value: '_blank', label: '新窗口' },
                  { value: '_parent', label: '父窗口' },
                  { value: '_top', label: '顶层窗口' }
              ]},
            { path: 'linkParameters', type: 'link-params', label: '链接参数' }
        ]
    },
    {
        id: 'advanced',
        label: '高级',
        icon: '',
        defaultExpanded: false,
        fields: [
            { path: 'fillBlankRows', type: 'toggle', label: '补足空白行' },
            { path: 'multiple', type: 'number', label: '倍数', min: 1, width: '80px' },
            { path: 'cellStyle.wrapCompute', type: 'toggle', label: '自动行高计算' }
        ]
    },
    {
        id: 'condition',
        label: '条件属性',
        icon: '',
        defaultExpanded: false,
        custom: true  // rendered by ConditionGroup
    }
];

// ---- CSS ----

const PANEL_CSS = `
.ud-prop-v4{font:12px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;color:#1d1d1f;overflow-x:hidden;overflow-y:auto;padding:0;user-select:none;background:#fff;height:100%}
.ud-prop-v4 *{box-sizing:border-box}
.ud-prop-v4 input,.ud-prop-v4 select,.ud-prop-v4 textarea{font:12px inherit;outline:none}
.ud-prop-v4 input[type=text],.ud-prop-v4 input[type=number],.ud-prop-v4 textarea,.ud-prop-v4 select{
    width:100%;padding:4px 8px;border:1px solid #e5e5e5;border-radius:6px;
    color:#1a1a1a;background:#fff;transition:border-color .15s,box-shadow .15s;height:28px
}
.ud-prop-v4 input:focus,.ud-prop-v4 select:focus,.ud-prop-v4 textarea:focus{
    border-color:#4c9aff;box-shadow:0 0 0 2px rgba(76,154,255,.12)
}
.ud-prop-v4 select{cursor:pointer;-webkit-appearance:none;appearance:none;background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='8' height='5'%3E%3Cpath d='M0 0l4 5 4-5z' fill='%23999'/%3E%3C/svg%3E");background-repeat:no-repeat;background-position:right 8px center;padding-right:24px}
.ud-prop-v4 input[type=checkbox]{accent-color:#4c9aff;margin:0 4px 0 0;vertical-align:middle}
.ud-prop-v4 input.mixed,.ud-prop-v4 select.mixed{color:#999;font-style:italic}

/* Selection bar */
.ud-prop-v4-selbar{padding:6px 12px;font-size:11px;color:#999;border-bottom:1px solid #f0f0f0;background:#fafafa;display:flex;align-items:center;gap:8px}
.ud-prop-v4-selbar .name{font-weight:600;color:#1a1a1a;font-size:13px}
.ud-prop-v4-selbar .count{font-size:10px;color:#999}

/* Group */
.ud-prop-v4-group{border-bottom:1px solid #f0f0f0}
.ud-prop-v4-group:last-child{border-bottom:none}
.ud-prop-v4-group-hd{display:flex;align-items:center;padding:8px 12px;cursor:pointer;user-select:none;background:#fafafa;transition:background .1s;gap:6px}
.ud-prop-v4-group-hd:hover{background:#f5f5f5}
.ud-prop-v4-group-hd .arrow{font-size:8px;color:#999;transition:transform .2s;flex-shrink:0;width:12px;text-align:center}
.ud-prop-v4-group-hd .arrow.folded{transform:rotate(-90deg)}
.ud-prop-v4-group-hd .label{font-size:12px;font-weight:600;color:#333;flex:1}
.ud-prop-v4-group-hd .badge{font-size:10px;background:#e5e5e5;color:#666;padding:1px 6px;border-radius:99px;min-width:18px;text-align:center}
.ud-prop-v4-group-bd{padding:8px 12px}

/* Field row */
.ud-prop-v4-field{margin-bottom:6px;display:flex;align-items:center;gap:8px}
.ud-prop-v4-field:last-child{margin-bottom:0}
.ud-prop-v4-field>label{font-size:11px;color:#666;flex-shrink:0;min-width:48px;text-align:right}
.ud-prop-v4-field .field-input{flex:1;min-width:0}

/* Inline toggle buttons */
.ud-prop-v4-toggles{display:flex;gap:0;border:1px solid #e5e5e5;border-radius:6px;overflow:hidden}
.ud-prop-v4-toggles button{padding:3px 10px;font-size:11px;font-weight:600;border:none;background:#fff;color:#666;cursor:pointer;transition:all .1s;border-right:1px solid #e5e5e5;min-width:32px}
.ud-prop-v4-toggles button:last-child{border-right:none}
.ud-prop-v4-toggles button.on{background:#e8f0fe;color:#4c9aff}
.ud-prop-v4-toggles button:hover:not(.on){background:#f5f5f5}

/* Align buttons */
.ud-prop-v4-align-btns{display:flex;gap:2px}
.ud-prop-v4-align-btns button{width:28px;height:24px;border:1px solid #e5e5e5;border-radius:4px;background:#fff;cursor:pointer;display:flex;align-items:center;justify-content:center;font-size:12px;color:#999;transition:all .1s}
.ud-prop-v4-align-btns button.on{background:#e8f0fe;border-color:#4c9aff;color:#4c9aff}
.ud-prop-v4-align-btns button:hover:not(.on){background:#f5f5f5}

/* Border editor inline */
.ud-prop-v4-border-row{display:flex;align-items:center;gap:6px}
.ud-prop-v4-border-row select{width:70px;flex-shrink:0}
.ud-prop-v4-border-row input[type=number]{width:40px;flex-shrink:0;text-align:center}
.ud-prop-v4-border-row input[type=color]{width:24px;height:24px;padding:0;border:1px solid #e5e5e5;border-radius:4px;cursor:pointer;flex-shrink:0}

/* Color input */
.ud-prop-v4-color{display:flex;align-items:center;gap:6px}
.ud-prop-v4-color input[type=text]{flex:1}
.ud-prop-v4-color input[type=color]{width:24px;height:24px;padding:0;border:1px solid #e5e5e5;border-radius:4px;cursor:pointer;flex-shrink:0}

/* Toggle switch */
.ud-prop-v4-switch{position:relative;width:32px;height:18px;display:inline-block;flex-shrink:0}
.ud-prop-v4-switch input{opacity:0;width:0;height:0}
.ud-prop-v4-switch .slider{position:absolute;cursor:pointer;top:0;left:0;right:0;bottom:0;background:#d4d4d4;border-radius:99px;transition:background .2s}
.ud-prop-v4-switch .slider::before{content:'';position:absolute;height:14px;width:14px;left:2px;bottom:2px;background:#fff;border-radius:50%;transition:transform .2s}
.ud-prop-v4-switch input:checked+.slider{background:#4c9aff}
.ud-prop-v4-switch input:checked+.slider::before{transform:translateX(14px)}

.ud-prop-v4 .btn{font:11px/1 inherit;font-weight:500;padding:4px 12px;border-radius:6px;border:none;cursor:pointer;transition:all .15s}
.ud-prop-v4 .btn-primary{background:#4c9aff;color:#fff}.ud-prop-v4 .btn-primary:hover{background:#3a8af0}
.ud-prop-v4 .btn-default{background:#f3f4f6;color:#374151}.ud-prop-v4 .btn-default:hover{background:#e5e7eb}
.ud-prop-v4 .btn-sm{padding:2px 8px;font-size:10px}
.ud-prop-v4 .btn-danger{background:#fff;color:#ef4444;border:1px solid #fecaca}.ud-prop-v4 .btn-danger:hover{background:#fef2f2}
`;

export default class PropertyRenderer {
    constructor(state, container, callbacks) {
        this.state = state;
        this.container = container;
        this.cb = callbacks || {};  // { onSet(path, value), getEditor(type) }
        this.groups = {};
        this.editorInstance = null; // current value editor (ContentTypeEditor result)
    }

    /**
     * Inject CSS once on first construction.
     */
    static injectCSS() {
        if ($('#ud-prop-v4-css').length) return;
        $('<style id="ud-prop-v4-css">').text(PANEL_CSS).appendTo('head');
    }

    /**
     * Build the full panel DOM and return the root element.
     */
    build() {
        PropertyRenderer.injectCSS();
        this.el = $(`<div class="ud-prop-v4"></div>`);

        // Selection info bar
        this.selBar = $(`<div class="ud-prop-v4-selbar">
            <span class="name"></span><span class="count"></span>
        </div>`);
        this.el.append(this.selBar);

        // Groups
        PROPERTY_GROUPS.forEach(group => {
            const { hd, bd } = this._buildGroupHeader(group);
            this.el.append(hd);
            this.el.append(bd);
            this.groups[group.id] = { hd, bd, group, folded: !group.defaultExpanded };
            if (!group.defaultExpanded) {
                hd.find('.arrow').addClass('folded');
                bd.hide();
            } else {
                this._renderGroupContent(group, bd);
            }
        });

        return this.el;
    }

    /**
     * Refresh the panel from current state.
     * Called by PropertyPanel after selection changes.
     */
    refresh() {
        // Update selection bar
        const s = this.state.selection;
        if (s) {
            const name = this.state.context.getCellName(s.ri, s.ci);
            this.selBar.find('.name').text(name);
            if (this.state.isMulti) {
                this.selBar.find('.count').text(`(${this.state.cellCount} 个单元格)`);
            } else {
                this.selBar.find('.count').text('');
            }
        }

        // Re-render expanded groups (skip 'content' — managed by ContentTypeEditor)
        Object.entries(this.groups).forEach(([id, g]) => {
            if (!g.folded && id !== 'content') {
                g.bd.empty();
                this._renderGroupContent(g.group, g.bd);
            }
        });
    }

    /**
     * Get the body container for a group (e.g. for custom content rendering).
     */
    getGroupBody(groupId) {
        const g = this.groups[groupId];
        return g ? g.bd : null;
    }

    // ---- Internal ----

    _buildGroupHeader(group) {
        const hd = $(`<div class="ud-prop-v4-group-hd" data-group="${group.id}">
            <span class="arrow">▾</span>
            <span class="label">${group.label}</span>
            <span class="badge" style="display:none"></span>
        </div>`);
        const bd = $(`<div class="ud-prop-v4-group-bd"></div>`);
        const self = this;
        hd.click(() => {
            const g = self.groups[group.id];
            g.folded = !g.folded;
            hd.find('.arrow').toggleClass('folded', g.folded);
            if (g.folded) {
                bd.slideUp(120);
            } else {
                bd.empty();
                self._renderGroupContent(group, bd);
                bd.slideDown(120);
            }
        });
        return { hd, bd };
    }

    _renderGroupContent(group, bd) {
        if (group.custom) {
            // Custom renderer — delegate to callback
            if (this.cb.renderCustomGroup) {
                this.cb.renderCustomGroup(group.id, bd, this.state);
            }
            return;
        }
        if (!group.fields) return;

        group.fields.forEach(field => {
            const row = this._renderField(field);
            if (row) bd.append(row);
        });
    }

    _renderField(field) {
        const state = this.state;
        const mixed = state.isMixed(field.path);
        const value = state.get(field.path);

        switch (field.type) {
            case 'text':
                return this._renderTextField(field, value, mixed);
            case 'number':
                return this._renderNumberField(field, value, mixed);
            case 'select':
                return this._renderSelectField(field, value, mixed);
            case 'toggle':
                return this._renderToggleField(field, value, mixed);
            case 'color':
                return this._renderColorField(field, value, mixed);
            case 'font-select':
                return this._renderFontSelect(field, value, mixed);
            case 'format-input':
                return this._renderFormatInput(field, value, mixed);
            case 'align-buttons':
                return this._renderAlignButtons(field, value, mixed);
            case 'border-editor':
                return this._renderBorderEditor(field, value, mixed);
            case 'cell-ref':
                return this._renderCellRef(field, value, mixed);
            case 'parent-cell-ref':
                return this._renderParentCellRef(field);
            case 'link-params':
                return this._renderLinkParams(field, mixed);
            default:
                return null;
        }
    }

    _renderTextField(field, value, mixed) {
        const self = this;
        const input = $(`<input type="text" placeholder="${field.placeholder || ''}">`);
        if (mixed) {
            input.addClass('mixed');
            input.attr('placeholder', '混合');
        } else {
            input.val(value || '');
        }
        // Commit on blur/Enter only — one undo entry per edit, not per keystroke
        input.on('change', function () {
            self.state.set(field.path, this.value);
        });
        input.on('keydown', function (e) {
            if (e.keyCode === 13) this.blur();
        });
        return this._wrapField(field, input);
    }

    _renderNumberField(field, value, mixed) {
        const self = this;
        const input = $(`<input type="number" min="${field.min || ''}" max="${field.max || ''}" step="${field.step || 1}">`);
        if (field.width) input.css('width', field.width);
        if (mixed) {
            input.addClass('mixed');
            input.attr('placeholder', '混合');
            input.val('');
        } else {
            input.val(value != null ? value : '');
        }
        input.on('change', function () {
            const v = this.value === '' ? undefined : (field.step && field.step < 1 ? parseFloat(this.value) : parseInt(this.value, 10));
            self.state.set(field.path, v);
        });
        input.on('keydown', function (e) {
            if (e.keyCode === 13) this.blur();
        });
        return this._wrapField(field, input);
    }

    _renderSelectField(field, value, mixed) {
        const self = this;
        const select = $(`<select></select>`);
        if (mixed) {
            select.addClass('mixed');
            select.append(`<option value="">混合</option>`);
        }
        (field.options || []).forEach(opt => {
            const v = typeof opt === 'object' ? opt.value : opt;
            const l = typeof opt === 'object' ? opt.label : opt;
            const sel = !mixed && String(value) === String(v) ? ' selected' : '';
            select.append(`<option value="${v}"${sel}>${l}</option>`);
        });
        select.on('change', function () {
            self.state.set(field.path, this.value);
        });
        return this._wrapField(field, select);
    }

    _renderToggleField(field, value, mixed) {
        const self = this;
        const checked = !mixed && value === true;
        const label = field.icon || field.label;
        const id = `toggle-${field.path.replace(/\./g, '-')}-${Math.random().toString(36).slice(2,6)}`;

        // For icon toggles, use toggle buttons. For labeled toggles, use switch.
        if (field.icon && field.icon.length <= 2) {
            // Use a toggle button style
            const btn = $(`<button class="${checked ? 'on' : ''}">${field.icon}</button>`);
            btn.on('click', function (e) {
                e.preventDefault();
                const newVal = !$(this).hasClass('on');
                $(this).toggleClass('on', newVal);
                self.state.set(field.path, newVal);
            });
            if (mixed) {
                btn.text('?');
                btn.removeClass('on');
            }
            const row = $(`<div class="ud-prop-v4-field"></div>`);
            row.append(`<label>${field.label}</label>`);
            const btns = $(`<div class="ud-prop-v4-toggles"></div>`);
            btns.append(btn);
            row.append(btns);
            return row;
        }

        // Switch toggle
        const switchEl = $(`<label class="ud-prop-v4-switch"><input type="checkbox" id="${id}"><span class="slider"></span></label>`);
        const input = switchEl.find('input');
        if (checked) input.prop('checked', true);

        // Mixed state: indeterminate via prop
        if (mixed) {
            input.prop('indeterminate', true);
            input.prop('checked', false);
        }

        input.on('change', function () {
            self.state.set(field.path, this.checked);
        });

        const row = $(`<div class="ud-prop-v4-field"></div>`);
        row.append(`<label for="${id}">${field.label}</label>`);
        row.append(switchEl);
        return row;
    }

    _renderColorField(field, value, mixed) {
        const self = this;
        const rgbStr = mixed ? '' : (value || '0,0,0');
        const hex = rgbToHex(rgbStr);

        const textInput = $(`<input type="text" placeholder="${mixed ? '混合' : '0,0,0'}">`);
        if (!mixed) textInput.val(rgbStr);
        if (mixed) textInput.addClass('mixed');

        const colorInput = $(`<input type="color" value="${hex}">`);

        textInput.on('change', function () {
            self.state.set(field.path, this.value);
            const h = rgbToHex(this.value);
            colorInput.val(h);
        });
        colorInput.on('change', function () {
            const hexVal = this.value;
            const rgb = hexToRgb(hexVal);
            textInput.val(rgb);
            self.state.set(field.path, rgb);
        });

        const wrapper = $(`<div class="ud-prop-v4-color"></div>`);
        wrapper.append(textInput, colorInput);
        return this._wrapField(field, wrapper);
    }

    _renderFontSelect(field, value, mixed) {
        const self = this;
        const fonts = [
            '宋体', '黑体', '楷体', '仿宋', '微软雅黑',
            'Arial', 'Helvetica', 'Times New Roman', 'Courier New', 'Verdana',
            'Georgia', 'Trebuchet MS', 'Impact', 'Comic Sans MS'
        ];
        const select = $(`<select></select>`);
        if (mixed) {
            select.addClass('mixed');
            select.append(`<option value="">混合</option>`);
        }
        select.append(`<option value="">默认</option>`);
        fonts.forEach(f => {
            const sel = !mixed && value === f ? ' selected' : '';
            select.append(`<option value="${f}"${sel}>${f}</option>`);
        });
        select.on('change', function () {
            const v = this.value || undefined;
            self.state.set(field.path, v);
        });
        return this._wrapField(field, select);
    }

    _renderFormatInput(field, value, mixed) {
        const self = this;
        const input = $(`<input type="text" placeholder="${field.placeholder || ''}">`);
        if (mixed) {
            input.addClass('mixed');
            input.attr('placeholder', '混合');
        } else {
            input.val(value || '');
        }

        // Simple completer for format patterns
        const formats = [
            'yyyy/MM/dd', 'yyyy/MM', 'yyyy-MM', 'yyyy', 'yyyy-MM-dd HH:mm:ss',
            'yyyy年MM月dd日 HH:mm:ss', 'yyyy-MM-dd', 'yyyy年MM月dd日',
            'HH:mm', 'HH:mm:ss', '#.##', '#.00', '##.##%', '##.00%',
            '##,###.##', '￥##,###.##', '$##,###.##', '0.00E00', '##0.0E0'
        ];
        input.on('focus', function () {
            // Auto-complete on demand - minimal implementation
            const v = input.val();
            if (!v) {
                // Show suggestion as placeholder hint
                input.attr('title', formats.slice(0, 5).join(', ') + ' ...');
            }
        });
        input.on('change', function () {
            self.state.set(field.path, this.value || undefined);
        });
        return this._wrapField(field, input);
    }

    _renderAlignButtons(field, value, mixed) {
        const self = this;
        const row = $(`<div class="ud-prop-v4-field"></div>`);
        row.append(`<label>${field.label}</label>`);
        const btns = $(`<div class="ud-prop-v4-align-btns"></div>`);

        const icons = {
            'left': '⫷', 'center': '⫾', 'right': '⫸',
            'top': '⫯', 'middle': '⫿', 'bottom': '⫰'
        };

        (field.options || []).forEach(opt => {
            const active = !mixed && value === opt;
            const btn = $(`<button class="${active ? 'on' : ''}" title="${opt}">${icons[opt] || opt[0]}</button>`);
            btn.on('click', function (e) {
                e.preventDefault();
                btns.find('button').removeClass('on');
                $(this).addClass('on');
                self.state.set(field.path, opt);
            });
            btns.append(btn);
        });
        row.append(btns);
        return row;
    }

    _renderBorderEditor(field, value, mixed) {
        const self = this;
        const border = value || {};
        const bMixed = mixed;
        const borderStyles = ['solid', 'dashed', 'dotted', 'double', 'none'];

        const wrapper = $(`<div class="ud-prop-v4-border-row"></div>`);

        // Width
        const wInput = $(`<input type="number" min="0" max="10" step="1" value="${bMixed ? '' : (border.width || 0)}">`);
        wInput.on('change', function () {
            const v = parseInt(this.value, 10);
            self._setBorderProp(field.path, 'width', v);
        });
        wrapper.append(wInput);

        // Color
        const cInput = $(`<input type="color" value="${rgbToHex(border.color || '0,0,0')}">`);
        cInput.on('input', function () {
            self._setBorderProp(field.path, 'color', hexToRgb(this.value));
        });
        wrapper.append(cInput);

        // Style
        const sSelect = $(`<select></select>`);
        borderStyles.forEach(s => {
            const sel = !bMixed && border.style === s ? ' selected' : '';
            sSelect.append(`<option value="${s}"${sel}>${s}</option>`);
        });
        sSelect.on('change', function () {
            self._setBorderProp(field.path, 'style', this.value);
        });
        wrapper.append(sSelect);

        return this._wrapField(field, wrapper);
    }

    _renderCellRef(field, value, mixed) {
        const self = this;
        const select = $(`<select></select>`);
        select.append(`<option value="">默认</option>`);
        if (mixed) {
            select.addClass('mixed');
            select.append(`<option value="">混合</option>`);
        }

        // Populate from all columns
        const hot = this.state.context.hot;
        if (hot) {
            for (let j = 0; j < hot.countCols(); j++) {
                const name = this.state.context.getCellName(null, j);
                select.append(`<option value="${name}">${name}</option>`);
            }
        }

        if (!mixed && value) {
            select.val(value);
        }

        select.on('change', function () {
            const v = this.value || undefined;
            self.state.set(field.path, v);
        });
        return this._wrapField(field, select);
    }

    _renderParentCellRef(field) {
        const editor = new ParentCellEditor(field.label, field.path, this.state);
        const container = $(`<div></div>`);
        editor.render(container);
        return container;
    }

    _renderLinkParams(field, mixed) {
        const row = $(`<div class="ud-prop-v4-field"></div>`);
        row.append(`<label>${field.label}</label>`);
        const btn = $(`<button class="btn btn-default btn-sm">配置参数</button>`);
        btn.on('click', () => {
            if (this.cb.onEditLinkParams) {
                this.cb.onEditLinkParams();
            }
        });
        row.append(btn);
        return row;
    }

    _setBorderProp(borderPath, prop, value) {
        var state = this.state;
        var currentBorder = state.get(borderPath) || {};
        var newBorder = Object.assign({}, currentBorder);
        newBorder[prop] = value;
        state.set(borderPath, newBorder);
    }

    _wrapField(field, inputEl) {
        const row = $(`<div class="ud-prop-v4-field"></div>`);
        row.append(`<label>${field.label}</label>`);
        const wrapper = $(`<div class="field-input"></div>`);
        wrapper.append(inputEl);
        row.append(wrapper);
        return row;
    }
}

// ---- Color helpers ----

function rgbToHex(rgb) {
    if (!rgb || typeof rgb !== 'string') return '#000000';
    const parts = rgb.split(',');
    if (parts.length !== 3) return '#000000';
    const r = parseInt(parts[0], 10);
    const g = parseInt(parts[1], 10);
    const b = parseInt(parts[2], 10);
    return '#' + [r, g, b].map(x => {
        const hex = x.toString(16);
        return hex.length === 1 ? '0' + hex : hex;
    }).join('');
}

function hexToRgb(hex) {
    if (!hex || typeof hex !== 'string') return '0,0,0';
    hex = hex.replace('#', '');
    if (hex.length === 3) hex = hex.split('').map(c => c + c).join('');
    const num = parseInt(hex, 16);
    return [(num >> 16) & 255, (num >> 8) & 255, num & 255].join(',');
}
