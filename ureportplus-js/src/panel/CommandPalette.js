/**
 * CommandPalette — Ctrl+Shift+P command palette for quick property operations.
 * Fuzzy search over all property-related commands.
 */
import {setDirty} from '../Utils.js';

const COMMANDS = [
    // Content type switching
    { id: 'type:simple',     group: '内容', label: '设置为简单文本',    shortcut: '', action: 'setContentType', arg: 'simple' },
    { id: 'type:expression', group: '内容', label: '设置为表达式',      shortcut: 'Ctrl+E', action: 'setContentType', arg: 'expression' },
    { id: 'type:dataset',    group: '内容', label: '设置为数据集',      shortcut: 'Ctrl+D', action: 'setContentType', arg: 'dataset' },
    { id: 'type:image',      group: '内容', label: '设置为图片',        shortcut: 'Ctrl+Shift+I', action: 'setContentType', arg: 'image' },
    { id: 'type:chart',      group: '内容', label: '设置为图表',        shortcut: 'Ctrl+Shift+C', action: 'setContentType', arg: 'chart' },
    { id: 'type:slash',      group: '内容', label: '设置为斜线',        shortcut: '', action: 'setContentType', arg: 'slash' },
    { id: 'type:zxing',      group: '内容', label: '设置为条码/二维码', shortcut: 'Ctrl+Shift+Z', action: 'setContentType', arg: 'zxing' },

    // Style
    { id: 'style:bold',      group: '样式', label: '切换粗体',          shortcut: 'Ctrl+B', action: 'toggleProp', arg: 'cellStyle.bold' },
    { id: 'style:italic',    group: '样式', label: '切换斜体',          shortcut: 'Ctrl+I', action: 'toggleProp', arg: 'cellStyle.italic' },
    { id: 'style:underline', group: '样式', label: '切换下划线',        shortcut: 'Ctrl+U', action: 'toggleProp', arg: 'cellStyle.underline' },
    { id: 'style:fgcolor',   group: '样式', label: '设置文字颜色...',   shortcut: '', action: 'focusProp', arg: 'cellStyle.forecolor' },
    { id: 'style:bgcolor',   group: '样式', label: '设置背景色...',     shortcut: '', action: 'focusProp', arg: 'cellStyle.bgcolor' },
    { id: 'style:font',      group: '样式', label: '设置字体...',       shortcut: '', action: 'focusProp', arg: 'cellStyle.fontFamily' },
    { id: 'style:fontSize',  group: '样式', label: '设置字号...',       shortcut: '', action: 'focusProp', arg: 'cellStyle.fontSize' },

    // Layout
    { id: 'layout:expand-none',  group: '布局', label: '展开方向：无',     shortcut: '', action: 'setExpand', arg: 'None' },
    { id: 'layout:expand-right', group: '布局', label: '展开方向：向右',   shortcut: '', action: 'setExpand', arg: 'Right' },
    { id: 'layout:expand-down',  group: '布局', label: '展开方向：向下',   shortcut: '', action: 'setExpand', arg: 'Down' },
    { id: 'layout:left-parent',  group: '布局', label: '设置左父格...',    shortcut: '', action: 'focusProp', arg: 'leftParentCellName' },
    { id: 'layout:top-parent',   group: '布局', label: '设置上父格...',    shortcut: '', action: 'focusProp', arg: 'topParentCellName' },

    // Condition
    { id: 'cond:add', group: '条件', label: '添加条件属性规则', shortcut: '', action: 'addCondition' },

    // General
    { id: 'gen:save',     group: '其他', label: '保存报表',   shortcut: 'Ctrl+S', action: 'saveReport' },
    { id: 'gen:preview',  group: '其他', label: '预览报表',   shortcut: '', action: 'previewReport' },
    { id: 'gen:exportPdf',group: '其他', label: '导出 PDF...', shortcut: '', action: 'exportPdf' },
];

const PALETTE_CSS = `
.ud-cmd-overlay{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.3);z-index:10000;display:flex;align-items:flex-start;justify-content:center;padding-top:15vh}
.ud-cmd-box{background:#fff;border-radius:12px;box-shadow:0 8px 40px rgba(0,0,0,.2);width:520px;max-height:420px;overflow:hidden;display:flex;flex-direction:column}
.ud-cmd-input-wrap{padding:12px 16px;border-bottom:1px solid #f0f0f0}
.ud-cmd-input{width:100%;border:none;font-size:14px;outline:none;padding:4px 0;color:#1a1a1a}
.ud-cmd-input::placeholder{color:#999}
.ud-cmd-list{flex:1;overflow-y:auto;padding:4px 0}
.ud-cmd-item{padding:6px 16px;display:flex;align-items:center;justify-content:space-between;cursor:pointer;font-size:12px;color:#333;transition:background .08s}
.ud-cmd-item:hover,.ud-cmd-item.sel{background:#e8f0fe}
.ud-cmd-item .label{flex:1}
.ud-cmd-item .group{font-size:10px;color:#999;margin-right:8px}
.ud-cmd-item .shortcut{font-size:10px;color:#999;background:#f3f4f6;padding:1px 6px;border-radius:4px;font-family:monospace}
.ud-cmd-empty{padding:24px;text-align:center;color:#999;font-size:13px}
`;

export default class CommandPalette {
    constructor(context, callbacks) {
        this.context = context;
        this.cb = callbacks || {};
        this.visible = false;
        this.filtered = [];
        this.selIdx = 0;
    }

    /**
     * Inject CSS once.
     */
    static injectCSS() {
        if ($('#ud-cmd-css').length) return;
        $('<style id="ud-cmd-css">').text(PALETTE_CSS).appendTo('head');
    }

    /**
     * Show the command palette.
     */
    show() {
        if (this.visible) return;
        CommandPalette.injectCSS();

        const self = this;
        this.visible = true;
        this.filtered = COMMANDS.slice();
        this.selIdx = 0;

        // Build overlay
        this.overlay = $(`<div class="ud-cmd-overlay"></div>`);
        this.box = $(`<div class="ud-cmd-box"></div>`);

        // Input
        const inputWrap = $(`<div class="ud-cmd-input-wrap"></div>`);
        this.input = $(`<input class="ud-cmd-input" type="text" placeholder="输入命令..." autofocus>`);
        this.input.on('input', () => self._filter());
        this.input.on('keydown', (e) => self._onKey(e));
        inputWrap.append(this.input);

        // List
        this.list = $(`<div class="ud-cmd-list"></div>`);

        this.box.append(inputWrap, this.list);
        this.overlay.append(this.box);
        $('body').append(this.overlay);

        // Close on overlay click
        this.overlay.on('mousedown', function (e) {
            if (e.target === this) self.hide();
        });

        this._render();
        setTimeout(() => this.input.focus(), 50);
    }

    hide() {
        this.visible = false;
        if (this.overlay) this.overlay.remove();
        this.overlay = null;
        this.box = null;
        this.input = null;
        this.list = null;
    }

    toggle() {
        if (this.visible) this.hide();
        else this.show();
    }

    // ---- Internal ----

    _filter() {
        const query = (this.input.val() || '').toLowerCase();
        if (!query) {
            this.filtered = COMMANDS.slice();
        } else {
            this.filtered = COMMANDS.filter(c =>
                c.label.toLowerCase().indexOf(query) >= 0 ||
                c.group.toLowerCase().indexOf(query) >= 0
            );
        }
        this.selIdx = 0;
        this._render();
    }

    _render() {
        const self = this;
        this.list.empty();

        if (this.filtered.length === 0) {
            this.list.append(`<div class="ud-cmd-empty">无匹配命令</div>`);
            return;
        }

        // Group by section
        let lastGroup = null;
        this.filtered.forEach((cmd, idx) => {
            if (cmd.group !== lastGroup) {
                this.list.append(`<div style="padding:4px 16px;font-size:10px;font-weight:600;color:#999;text-transform:uppercase">${cmd.group}</div>`);
                lastGroup = cmd.group;
            }
            const item = $(`<div class="ud-cmd-item ${idx === self.selIdx ? 'sel' : ''}" data-idx="${idx}">
                <span class="label">${cmd.label}</span>
                ${cmd.shortcut ? `<span class="shortcut">${cmd.shortcut}</span>` : ''}
            </div>`);
            item.on('mousedown', function (e) {
                e.preventDefault();
                self._execute(self.filtered[parseInt($(this).data('idx'))]);
            });
            item.on('mouseenter', function () {
                self.list.find('.ud-cmd-item').removeClass('sel');
                $(this).addClass('sel');
                self.selIdx = parseInt($(this).data('idx'));
            });
            this.list.append(item);
        });

        // Scroll selected into view
        const selEl = this.list.find('.sel');
        if (selEl.length) {
            selEl[0].scrollIntoView({ block: 'nearest' });
        }
    }

    _onKey(e) {
        switch (e.key) {
            case 'Escape':
                e.preventDefault();
                this.hide();
                break;
            case 'ArrowDown':
                e.preventDefault();
                this.selIdx = Math.min(this.selIdx + 1, this.filtered.length - 1);
                this._render();
                break;
            case 'ArrowUp':
                e.preventDefault();
                this.selIdx = Math.max(this.selIdx - 1, 0);
                this._render();
                break;
            case 'Enter':
                e.preventDefault();
                if (this.filtered[this.selIdx]) {
                    this._execute(this.filtered[this.selIdx]);
                }
                break;
        }
    }

    _execute(cmd) {
        if (!cmd) return;
        this.hide();

        switch (cmd.action) {
            case 'setContentType':
                if (this.cb.setContentType) this.cb.setContentType(cmd.arg);
                break;
            case 'toggleProp':
                if (this.cb.toggleProp) this.cb.toggleProp(cmd.arg);
                break;
            case 'focusProp':
                if (this.cb.focusProp) this.cb.focusProp(cmd.arg);
                break;
            case 'setExpand':
                if (this.cb.setExpand) this.cb.setExpand(cmd.arg);
                break;
            case 'addCondition':
                if (this.cb.addCondition) this.cb.addCondition();
                break;
            case 'saveReport':
                if (this.cb.saveReport) this.cb.saveReport();
                break;
            case 'previewReport':
                if (this.cb.previewReport) this.cb.previewReport();
                break;
            case 'exportPdf':
                if (this.cb.exportPdf) this.cb.exportPdf();
                break;
        }
    }
}
