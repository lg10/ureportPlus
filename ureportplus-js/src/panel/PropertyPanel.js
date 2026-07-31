/**
 * Property Panel v3 — stable scroll container, grid style tab, type pills.
 * Fixes: no page jumps, clean grid layout for styles, compact Notion aesthetic.
 */
import SimpleValueEditor from './property/SimpleValueEditor.js';
import ExpressionValueEditor from './property/ExpressionValueEditor.js';
import DatasetValueEditor from './property/DatasetValueEditor.js';
import ImageValueEditor from './property/ImageValueEditor.js';
import SlashValueEditor from './property/SlashValueEditor.js';
import ZxingValueEditor from './property/ZxingValueEditor.js';
import URLParameterDialog from '../dialog/URLParameterDialog.js';
import BarChartValueEditor from './property/chart/BarChartValueEditor.js';
import LineChartValueEditor from './property/chart/LineChartValueEditor.js';
import AreaChartValueEditor from './property/chart/AreaChartValueEditor.js';
import HorizontalBarChartValueEditor from './property/chart/HorizontalBarChartValueEditor.js';
import BubbleChartValueEditor from './property/chart/BubbleChartValueEditor.js';
import DoughnutChartValueEditor from './property/chart/DoughnutChartValueEditor.js';
import PieChartValueEditor from './property/chart/PieChartValueEditor.js';
import PolarChartValueEditor from './property/chart/PolarChartValueEditor.js';
import RadarChartValueEditor from './property/chart/RadarChartValueEditor.js';
import ScatterChartValueEditor from './property/chart/ScatterChartValueEditor.js';
import CrossTabWidget from '../widget/CrossTabWidget.js';
import {setDirty} from '../Utils.js';
import {alert} from '../MsgBox.js'

const TYPE_ITEMS = [
    {v:'simple',label:'文本',icon:'Aa'},
    {v:'expression',label:'表达式',icon:'ƒ'},
    {v:'dataset',label:'数据集',icon:'⊞'},
    {v:'image',label:'图片',icon:'🖼'},
    {v:'slash',label:'斜线',icon:'/'},
    {v:'qrcode',label:'二维码',icon:'▣'},
    {v:'barcode',label:'条码',icon:'∥'},
    {v:'chart',label:'图表',icon:'📊'},
];

const STYLE_FIELDS = [
    ['字体','fontFamily','text'],
    ['字号','fontSize','number'],
    ['粗体','bold','cb'],
    ['斜体','italic','cb'],
    ['前景色','forecolor','text'],
    ['背景色','bgcolor','text'],
    ['对齐','align','sel','left|center|right'],
    ['左边框','borderLeft','sel','none|solid|dashed|dotted'],
    ['上边框','borderTop','sel','none|solid|dashed|dotted'],
    ['右边框','borderRight','sel','none|solid|dashed|dotted'],
    ['下边框','borderBottom','sel','none|solid|dashed|dotted'],
];

export default class PropertyPanel {
    constructor(context) { this.context = context; }

    buildPanel() {
        if (!$('#ud-prop-v3-css').length) {
            $('<style id="ud-prop-v3-css">').text(`
.ud-prop{font:12px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;color:#1d1d1f;overflow-x:hidden;padding:0 8px 8px}
.ud-prop *{box-sizing:border-box}
.ud-prop input,.ud-prop select,.ud-prop textarea{font:12px inherit}
.ud-prop input[type=text],.ud-prop input[type=number],.ud-prop textarea,.ud-prop select{width:100%;padding:4px 8px;border:1px solid #e5e7eb;border-radius:5px;color:#1d1d1f;background:#fff;outline:none;transition:border-color .15s;height:28px}
.ud-prop input:focus,.ud-prop select:focus,.ud-prop textarea:focus{border-color:#6366f1;box-shadow:0 0 0 2px rgba(99,102,241,.1)}
.ud-prop select{cursor:pointer}
.ud-prop input[type=radio],.ud-prop input[type=checkbox]{accent-color:#6366f1;margin:0 4px 0 0;vertical-align:middle}
.ud-prop .btn{font:11px/1 inherit;font-weight:500;padding:3px 10px;border-radius:5px;border:none;cursor:pointer}
.ud-prop .btn-primary{background:#6366f1;color:#fff}.ud-prop .btn-primary:hover{background:#4f46e5}
.ud-prop .btn-default{background:#f3f4f6;color:#374151}.ud-prop .btn-default:hover{background:#e5e7eb}

.ud-prop-tabs{display:flex;gap:0;border-bottom:1px solid #f0f0f0;margin-bottom:6px;position:sticky;top:0;background:#fff;z-index:10}
.ud-prop-tab{padding:5px 12px;font-size:11px;font-weight:600;color:#9ca3af;cursor:pointer;border:none;background:0;position:relative;transition:color .15s}
.ud-prop-tab:hover{color:#6366f1}
.ud-prop-tab.on{color:#6366f1}
.ud-prop-tab.on::after{content:'';position:absolute;bottom:-1px;left:6px;right:6px;height:2px;background:#6366f1;border-radius:1px}

.ud-prop-types{display:flex;flex-wrap:wrap;gap:2px;margin-bottom:8px;position:sticky;top:32px;background:#fff;z-index:9;padding:4px 0}
.ud-prop-type{padding:3px 8px;font-size:11px;font-weight:500;border-radius:5px;cursor:pointer;color:#6b7280;background:0;border:1px solid transparent;transition:all .15s;white-space:nowrap}
.ud-prop-type:hover{background:#f3f4f6;color:#1d1d1f}
.ud-prop-type.on{background:#eef2ff;color:#6366f1;border-color:#c7d2fe}

.ud-prop-sec{margin-bottom:10px}
.ud-prop-sec-hd{font-size:10px;font-weight:600;color:#9ca3af;text-transform:uppercase;letter-spacing:.5px;padding:3px 0;cursor:pointer;user-select:none;display:flex;align-items:center;gap:4px}
.ud-prop-sec-hd:hover{color:#6b7280}
.ud-prop-sec-hd .arr{font-size:8px;transition:transform .2s}
.ud-prop-sec-hd.folded .arr{transform:rotate(-90deg)}
.ud-prop-sec-bd{padding-top:4px}

.ud-prop-row{margin-bottom:5px}
.ud-prop-row>label{display:block;font-size:11px;font-weight:500;color:#6b7280;margin-bottom:2px}
.ud-prop-inline{display:flex;align-items:center;gap:4px}
.ud-prop-inline>*{min-width:0}
.ud-prop-inline label{font-size:11px;white-space:nowrap;color:#6b7280}
.ud-prop-hint{font-size:10px;color:#9ca3af;font-weight:400;margin-left:3px}

.ud-prop-grid{display:grid;grid-template-columns:48px 1fr;gap:3px 6px;align-items:center}
.ud-prop-grid>label{font-size:11px;color:#6b7280;text-align:right}
.ud-prop-grid input[type=text],.ud-prop-grid input[type=number],.ud-prop-grid select{width:100%;min-width:0}
.ud-prop-grid input[type=checkbox]{justify-self:start}

.ud-ac{position:fixed;z-index:9999;background:#fff;border:1px solid #e5e7eb;border-radius:8px;box-shadow:0 12px 40px rgba(0,0,0,.15);max-height:200px;overflow-y:auto;min-width:200px;display:none}
.ud-ac-item{padding:5px 10px;font-size:11px;cursor:pointer;display:flex;justify-content:space-between;color:#374151}
.ud-ac-item:hover,.ud-ac-item.sel{background:#eef2ff;color:#6366f1}
.ud-ac-tag{font-size:10px;color:#9ca3af;background:#f3f4f6;padding:1px 5px;border-radius:3px}
`).appendTo('head');
        }
        this._build();
        return this.el;
    }

    _build() {
        this.el = $(`<div class="ud-prop"><div class="ud-prop-tabs">
            <div class="ud-prop-tab on" data-tab="prop">属性</div>
            <div class="ud-prop-tab" data-tab="style">样式</div>
            <div class="ud-prop-tab" data-tab="advanced">高级</div>
        </div></div>`);
        const _ = this;
        this.el.find('.ud-prop-tab').click(function () {
            _.el.find('.ud-prop-tab').removeClass('on');
            $(this).addClass('on');
            _.el.find('.ud-pane').hide();
            _.el.find(`.ud-pane-${$(this).data('tab')}`).show();
        });

        // Pane: Property
        this.paneProp = $(`<div class="ud-pane ud-pane-prop"></div>`);
        this._buildTypePills();
        this._buildValueArea();
        this._buildParentSection();
        this._buildLinkSection();
        this.el.append(this.paneProp);

        // Pane: Style
        this.paneStyle = $(`<div class="ud-pane ud-pane-style" style="display:none"></div>`);
        this._buildStyleSection();
        this.el.append(this.paneStyle);

        // Pane: Advanced
        this.paneAdv = $(`<div class="ud-pane ud-pane-advanced" style="display:none"></div>`);
        this._buildRendererSection();
        this.el.append(this.paneAdv);

        // Editors
        this.editorMap = new Map();
        this.editorMap.set('simple', new SimpleValueEditor(this.valueArea, this.context));
        this.editorMap.set('expression', new ExpressionValueEditor(this.valueArea, this.context));
        this.editorMap.set('dataset', new DatasetValueEditor(this.valueArea, this.context));
        this.editorMap.set('image', new ImageValueEditor(this.valueArea, this.context));
        this.editorMap.set('slash', new SlashValueEditor(this.valueArea, this.context));
        this.editorMap.set('zxing', new ZxingValueEditor(this.valueArea, this.context));
        this.chartEditorMap = new Map();
        ['bar','line','horizontalBar','area','radar','polarArea','scatter','bubble','doughnut','pie'].forEach(k => {
            this.chartEditorMap.set(k, new (this._chartClass(k))(this.valueArea, this.context));
        });
    }

    _chartClass(k) {
        const m = {bar: BarChartValueEditor, line: LineChartValueEditor, horizontalBar: HorizontalBarChartValueEditor,
            area: AreaChartValueEditor, radar: RadarChartValueEditor, polarArea: PolarChartValueEditor,
            scatter: ScatterChartValueEditor, bubble: BubbleChartValueEditor, doughnut: DoughnutChartValueEditor,
            pie: PieChartValueEditor};
        return m[k];
    }

    _sec(title) {
        const hd = $(`<div class="ud-prop-sec-hd"><span class="arr">▾</span>${title}</div>`);
        const bd = $(`<div class="ud-prop-sec-bd"></div>`);
        const sec = $(`<div class="ud-prop-sec"></div>`).append(hd, bd);
        let folded = false;
        hd.click(() => { folded = !folded; hd.toggleClass('folded', folded); bd.stop(true, true).slideToggle(120); });
        return { sec, bd };
    }

    // ═══ Type Pills ═══
    _buildTypePills() {
        this.typePillsEl = $(`<div class="ud-prop-types"></div>`);
        const _ = this;
        TYPE_ITEMS.forEach(t => {
            const btn = $(`<span class="ud-prop-type" data-v="${t.v}">${t.icon} ${t.label}</span>`);
            btn.click(() => _._onTypeChange(t.v));
            this.typePillsEl.append(btn);
        });
        this.paneProp.append(this.typePillsEl);
    }

    _buildValueArea() {
        this.valueArea = $(`<div style="min-height:40px"></div>`);
        this.paneProp.append(this.valueArea);
    }

    // ═══ Parent Section ═══
    _buildParentSection() {
        const { sec, bd } = this._sec('父格设置');
        const _ = this;

        // Left parent
        bd.append(`<div class="ud-prop-row"><label>左父格</label></div>`);
        const lr = $(`<div class="ud-prop-inline"></div>`);
        this.dlRadio = $(`<label><input type="radio" name="__lp" value="default"> 默认</label>`);
        this.clRadio = $(`<label><input type="radio" name="__lp" value="custom"> 自定义</label>`);
        this.lpName = $(`<select disabled style="flex:1;min-width:0"></select>`);
        this.lpRow = $(`<select disabled style="width:46px;flex-shrink:0"></select>`);
        lr.append(this.dlRadio, this.clRadio, this.lpName, this.lpRow);
        bd.append(lr);
        this.dlRadio.find('input').click(() => { this.lpName.prop('disabled', true); this.lpRow.prop('disabled', true); this._setParent(null, true); });
        this.clRadio.find('input').click(() => { this.lpName.prop('disabled', false); this.lpRow.prop('disabled', false); setDirty(); });
        this.lpName.change(() => { const n = this.lpName.val(), r = this.lpRow.val(); if (n && r) this._setParent(n === 'root' ? 'root' : n + r, true); });
        this.lpRow.change(() => { const n = this.lpName.val(), r = this.lpRow.val(); if (n && r) this._setParent(n === 'root' ? 'root' : n + r, true); });

        // Top parent
        bd.append(`<div class="ud-prop-row"><label>上父格</label></div>`);
        const tr = $(`<div class="ud-prop-inline"></div>`);
        this.dtRadio = $(`<label><input type="radio" name="__tp" value="default"> 默认</label>`);
        this.ctRadio = $(`<label><input type="radio" name="__tp" value="custom"> 自定义</label>`);
        this.tpName = $(`<select disabled style="flex:1;min-width:0"></select>`);
        this.tpRow = $(`<select disabled style="width:46px;flex-shrink:0"></select>`);
        tr.append(this.dtRadio, this.ctRadio, this.tpName, this.tpRow);
        bd.append(tr);
        this.dtRadio.find('input').click(() => { this.tpName.prop('disabled', true); this.tpRow.prop('disabled', true); this._setParent(null, false); });
        this.ctRadio.find('input').click(() => { this.tpName.prop('disabled', false); this.tpRow.prop('disabled', false); });
        this.tpName.change(() => { const n = this.tpName.val(), r = this.tpRow.val(); if (n && r) this._setParent(n === 'root' ? 'root' : n + r, false); });
        this.tpRow.change(() => { const n = this.tpName.val(), r = this.tpRow.val(); if (n && r) this._setParent(n === 'root' ? 'root' : n + r, false); });

        this.paneProp.append(sec);
    }

    // ═══ Link Section ═══
    _buildLinkSection() {
        const { sec, bd } = this._sec('链接配置');
        const _ = this;
        bd.append(`<div class="ud-prop-row"><label>URL <span class="ud-prop-hint">支持表达式</span></label></div>`);
        this.linkEditor = $(`<div style="position:relative"><input type="text" placeholder="如 param('id') 或 &A1"></div>`);
        this.linkEditor.find('input').change(function () { _.cellDef.linkUrl = this.value; setDirty(); });
        bd.append(this.linkEditor);

        bd.append(`<div class="ud-prop-row" style="margin-top:3px"><label>打开方式</label></div>`);
        const tr = $(`<div class="ud-prop-inline"></div>`);
        this.targetSelect = $(`<select style="flex:1"><option value="_blank">新窗口</option><option value="_self">当前窗口</option></select>`);
        this.targetSelect.change(function () { _.cellDef.linkTargetWindow = this.value; setDirty(); });
        const pb = $(`<button class="btn btn-default" style="flex-shrink:0">参数配置</button>`);
        pb.click(() => {
            if (!_.cellDef.linkUrl) { alert('请先填写URL'); return; }
            if (!_.cellDef.linkParameters) _.cellDef.linkParameters = [];
            new URLParameterDialog().show(_.cellDef.linkParameters);
            setDirty();
        });
        tr.append(this.targetSelect, pb);
        bd.append(tr);
        this.paneProp.append(sec);
    }

    // ═══ Style Section (Grid Layout) ═══
    _buildStyleSection() {
        this.styleInputs = {};
        const grid = $(`<div class="ud-prop-grid"></div>`);
        const _ = this;
        STYLE_FIELDS.forEach(([label, key, type, opts]) => {
            grid.append(`<label>${label}</label>`);
            let input;
            if (type === 'sel') {
                input = $(`<select>${opts.split('|').map(o => `<option value="${o}">${o}</option>`).join('')}</select>`);
            } else if (type === 'cb') {
                input = $(`<input type="checkbox">`);
            } else {
                input = $(`<input type="${type}">`);
            }
            input.on('change input', () => { _._onStyleChange(); setDirty(); });
            this.styleInputs[key] = input;
            grid.append(input);
        });
        this.paneStyle.append(grid);
    }

    // ═══ Renderer Section ═══
    _buildRendererSection() {
        const { bd } = this._sec('渲染器');
        this.rendererEditor = $(`<input type="text" placeholder="Spring Bean 名称">`);
        this.rendererEditor.change(() => this._setRenderer(this.rendererEditor.val()));
        bd.append(this.rendererEditor);
        const sec = this.paneAdv.find('.ud-prop-sec');
    }

    // ═══ Type Change ═══
    _onTypeChange(type) {
        if (!this.cellDef) return;
        for (let e of this.editorMap.values()) e.hide();
        for (let e of this.chartEditorMap.values()) e.hide();
        const cd = this.cellDef, ri = this.rowIndex, ci = this.colIndex, r2 = this.row2Index, c2 = this.col2Index;
        if (type === 'simple') { if (cd.value.type !== 'simple') cd.value = { type: 'simple' }; cd.expand = 'None'; this.editorMap.get('simple').show(cd, ri, ci, r2, c2); }
        else if (type === 'expression') { if (cd.value.type !== 'expression') cd.value = { type: 'expression', value: '' }; cd.expand = 'None'; this.editorMap.get('expression').show(cd, ri, ci, r2, c2); }
        else if (type === 'dataset') { if (cd.value.type !== 'dataset') cd.value = { type: 'dataset', datasetName: '', property: '', aggregate: '', conditions: [], order: 'none' }; cd.expand = 'Down'; this.editorMap.get('dataset').show(cd, ri, ci, r2, c2); }
        else if (type === 'image') { if (cd.value.type !== 'image') cd.value = { type: 'image', source: 'text' }; cd.expand = 'None'; this.editorMap.get('image').show(cd, ri, ci, r2, c2); }
        else if (type === 'qrcode' || type === 'barcode') { const cat = type === 'qrcode' ? 'qrcode' : 'barcode'; if (cd.value.type !== 'zxing' || cd.value.category !== cat) { const td = this.context.hot.getCell(ri, ci); cd.value = { width: this._cellW(ci, td.colSpan), height: this._cellH(ri, td.rowSpan), type: 'zxing', source: 'text', category: cat, data: '', format: type === 'barcode' ? 'CODE_128' : '' }; cd.expand = 'None'; } this.editorMap.get('zxing').show(cd, ri, ci, r2, c2); }
        else if (type === 'slash') { cd.crossTabWidget = new CrossTabWidget(this.context, ri, ci); cd.expand = 'None'; this.editorMap.get('slash').show(cd, ri, ci, r2, c2); }
        else if (type === 'chart') { const td = this.context.hot.getCell(ri, ci); cd.value = { width: this._cellW(ci, td.colSpan), height: this._cellH(ri, td.rowSpan), type: 'chart', chart: { dataset: { type: 'pie' } } }; }
        this._updatePills(type);
        this.context.hot.setDataAtCell(ri, ci, '');
        this.context.hot.render();
        setDirty();
    }

    _updatePills(active) {
        this.typePillsEl.find('.ud-prop-type').each(function () { $(this).toggleClass('on', $(this).data('v') === active); });
        if (active === 'qrcode' || active === 'barcode') this.typePillsEl.find('.ud-prop-type[data-v="qrcode"]').addClass('on');
    }

    // ═══ Helpers ═══
    _setParent(name, isLeft) { if (this.initialized) return; for (let i = this.rowIndex; i <= this.row2Index; i++) for (let j = this.colIndex; j <= this.col2Index; j++) { const cd = this.context.getCell(i, j); if (!cd) continue; if (isLeft) cd.leftParentCellName = name; else cd.topParentCellName = name; } setDirty(); }
    _buildOpts(sel, prefix) { sel.empty(); sel.append(`<option value="root">无</option>`); for (let j = 0; j < this.context.hot.countCols(); j++) { const n = this.context.getCellName(null, j); sel.append(`<option value="${n}">${n}</option>`); } }
    _buildRowOpts(sel) { sel.empty(); sel.append(`<option></option>`); for (let j = 0; j < this.context.hot.countRows(); j++) sel.append(`<option>${j + 1}</option>`); }
    _parse(cn) { let p = -1; for (let i = 0; i < cn.length; i++) if (!isNaN(parseInt(cn.charAt(i)))) { p = i; break; } return { name: cn.substring(0, p), num: cn.substring(p) }; }
    _cellW(ci, cs) { let w = this.context.hot.getColWidth(ci) - 3; if (cs >= 2) for (let i = ci + 1; i < ci + cs; i++) w += this.context.hot.getColWidth(i); return w; }
    _cellH(ri, rs) { let h = this.context.hot.getRowHeight(ri) - 3; if (rs >= 2) for (let i = ri + 1; i < ri + rs; i++) h += this.context.hot.getRowHeight(i); return h; }
    _setRenderer(r) { if (this.initialized) return; for (let i = this.rowIndex; i <= this.row2Index; i++) for (let j = this.colIndex; j <= this.col2Index; j++) { const cd = this.context.getCell(i, j); if (cd) cd.renderer = r; } setDirty(); }
    _onStyleChange() { if (!this.cellDef) return; let cs = this.cellDef.cellStyle; if (!cs) { cs = {}; this.cellDef.cellStyle = cs; } for (let [k, el] of Object.entries(this.styleInputs)) { const v = el.is(':checkbox') ? el.prop('checked') : el.val(); if (v !== '' && v !== false) cs[k] = v; } }

    // ═══ Refresh — populate panel from cell ═══
    refresh(ri, ci, r2, c2) {
        const cd = this.context.getCell(ri, ci);
        if (!cd) return;
        this.cellDef = cd;
        this.initialized = true;
        this.rowIndex = ri; this.colIndex = ci; this.row2Index = r2; this.col2Index = c2;
        $('#__prop_tab_link').html(`属性[${this.context.getCellName(ri, ci)}]`);

        // Parent
        this._buildOpts(this.lpName); this._buildRowOpts(this.lpRow);
        this._buildOpts(this.tpName); this._buildRowOpts(this.tpRow);
        const lp = cd.leftParentCellName;
        if (lp) { this.clRadio.find('input').prop('checked', true).trigger('click'); this.lpName.prop('disabled', false); this.lpRow.prop('disabled', false); if (lp === 'root') { this.lpName.val('root'); } else { const d = this._parse(lp); this.lpName.val(d.name); this.lpRow.val(d.num); } } else { this.dlRadio.find('input').prop('checked', true).trigger('click'); }
        const tp = cd.topParentCellName;
        if (tp) { this.ctRadio.find('input').prop('checked', true).trigger('click'); this.tpName.prop('disabled', false); this.tpRow.prop('disabled', false); if (tp === 'root') { this.tpName.val('root'); } else { const d = this._parse(tp); this.tpName.val(d.name); this.tpRow.val(d.num); } } else { this.dtRadio.find('input').prop('checked', true).trigger('click'); }

        // Link
        this.linkEditor.find('input').val(cd.linkUrl || '');
        this.targetSelect.val(cd.linkTargetWindow || '_blank');

        // Renderer
        this.rendererEditor.val((cd.cellStyle && cd.cellStyle.renderer) || '');

        // Style
        const cs = cd.cellStyle || {};
        for (let [k, el] of Object.entries(this.styleInputs)) {
            if (el.is(':checkbox')) el.prop('checked', !!cs[k]);
            else el.val(cs[k] || '');
        }

        // Type
        let type = cd.value.type || 'simple';
        if (type === 'zxing') type = cd.value.category || 'qrcode';
        this._updatePills(type);

        for (let e of this.editorMap.values()) e.hide();
        for (let e of this.chartEditorMap.values()) e.hide();
        if (type === 'chart') {
            const ct = (cd.value.chart && cd.value.chart.dataset) ? cd.value.chart.dataset.type : 'pie';
            const ce = this.chartEditorMap.get(ct);
            if (ce) ce.show(cd, ri, ci, r2, c2);
        } else {
            const e = this.editorMap.get(type);
            if (e) e.show(cd, ri, ci, r2, c2);
        }
        this.initialized = false;
        // Scroll value area into view if needed
        this.el.scrollTop(0);
    }
}
