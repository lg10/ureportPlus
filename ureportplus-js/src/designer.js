/**
 * Created by Jacky.Gao on 2017-01-25.
 */
import Context from './Context.js';
import ReportTable from './table/ReportTable.js';
import SaveTool from './tools/SaveTool.js';
import OpenTool from './tools/OpenTool.js';
import AlignLeftTool from './tools/AlignLeftTool.js';
import AlignTopTool from './tools/AlignTopTool.js';
import RedoTool from './tools/RedoTool.js';
import UndoTool from './tools/UndoTool.js';
import BorderTool from './tools/BorderTool.js';
import BoldTool from './tools/BoldTool.js';
import ItalicTool from './tools/ItalicTool.js';
import UnderlineTool from './tools/UnderlineTool.js';
import BgcolorTool from './tools/BgcolorTool.js';
import ForecolorTool from './tools/ForecolorTool.js';
import ImageTool from './tools/ImageTool.js';
import ChartTool from './tools/ChartTool.js';
import CrosstabTool from './tools/CrosstabTool.js';
import MergeTool from './tools/MergeTool.js';
import ImportTool from './tools/ImportTool.js';
import PreviewTool from './tools/PreviewTool.js';
import FontFamilyTool from './tools/FontFamilyTool.js';
import FontSizeTool from './tools/FontSizeTool.js';
import ZxingTool from './tools/ZxingTool.js';
import SettingsTool from './tools/SettingsTool.js';
import SearchFormSwitchTool from './tools/SearchFormSwitchTool.js';
import DatasourcePanel from './panel/DatasourcePanel.js';
import PropertyPanel from './panel/PropertyPanel.js';

import {undoManager,tableToXml,resetDirty} from './Utils.js';
import {alert} from './MsgBox.js';
import PrintLine from './PrintLine.js';
import FileInfo from './FileInfo.js';
import {renderRowHeader} from './table/HeaderUtils.js';
import AiChatPanel from './ai/AiChatPanel.js';

export default class UReportPlusDesigner{
    constructor(containerId,searchFormContainerId){
        window._designer = this;
        undoManager.setLimit(100);
        const _this=this;
        this.container=$('#'+containerId);
        const tableContainer=$(`<div></div>`);
        this.container.append(tableContainer);
        const fileInfo=new FileInfo();
        const reportTable=new ReportTable(tableContainer.get(0),function(){
            _this.context=new Context(this);
            _this.context.fileInfo=fileInfo;
            _this.buildTools(_this.context);
            _this.datasourcePanel=new DatasourcePanel(_this.context);
            _this.propertyPanel=new PropertyPanel(_this.context);
            _this.buildPropertyPanel();
            this.bindSelectionEvent(function(rowIndex, colIndex, row2Index, col2Index){
                _this.propertyPanel.refresh(rowIndex,colIndex,row2Index,col2Index);
                for(let tool of _this.tools){
                    if(tool.refresh){
                        tool.refresh(rowIndex,colIndex,row2Index,col2Index);
                    }
                }
            });
            _this.printLine=new PrintLine(_this.context);
            const rows=_this.context.reportDef.rows;
            for(let row of rows){
                const band=row.band;
                if(!band){
                    continue;
                }
                _this.context.addRowHeader(row.rowNumber-1,band);
            }
            renderRowHeader(_this.context.hot,_this.context);

            // Initialize AI chat panel
            _this.aiChatPanel = new AiChatPanel(_this.context);
            _this.aiChatPanel.init();

            // Ctrl+S / Cmd+S save
            var handleSaveKey = function(e) {
                if ((e.ctrlKey || e.metaKey) && e.keyCode === 83) {
                    e.preventDefault();
                    e.stopPropagation();
                    _this._quickSave();
                    return false;
                }
            };
            // Capture phase on both document and body to beat all other handlers
            document.addEventListener('keydown', handleSaveKey, true);
            if (document.body) document.body.addEventListener('keydown', handleSaveKey, true);
            // Also try jQuery on window as fallback
            $(window).on('keydown.save', handleSaveKey);

            // Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y — undo/redo (skip text inputs so
            // native field-level undo keeps working there)
            var handleUndoRedoKey = function(e) {
                if (!(e.ctrlKey || e.metaKey)) return;
                const tag = e.target && e.target.tagName;
                if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
                if (e.keyCode === 90) {
                    e.preventDefault();
                    e.stopPropagation();
                    if (e.shiftKey) {
                        if (undoManager.hasRedo()) undoManager.redo();
                    } else {
                        if (undoManager.hasUndo()) undoManager.undo();
                    }
                    _this.context.hot.render();
                    return false;
                } else if (e.keyCode === 89) {
                    e.preventDefault();
                    e.stopPropagation();
                    if (undoManager.hasRedo()) undoManager.redo();
                    _this.context.hot.render();
                    return false;
                }
            };
            document.addEventListener('keydown', handleUndoRedoKey, true);

            // Track save status (no auto-save)
            $('<style>').text(
                '.ud-save-status{font-size:11px;padding:3px 10px;border-radius:99px;margin-left:12px;font-weight:500;display:inline-flex;align-items:center}' +
                '.ud-save-status.saved{color:#16a34a;background:#f0fdf4;border:1px solid #bbf7d0}' +
                '.ud-save-status.unsaved{color:#d97706;background:#fffbeb;border:1px solid #fde68a}'
            ).appendTo('head');
            _this._dirty = false;
            _this._saveStatusEl = $('<span class="ud-save-status saved" title="Ctrl+S 保存">● 已保存</span>');
            $('.ud-toolbar').append(_this._saveStatusEl);
            // Push report file name and save status to the rightmost
            $('.ud-toolbar').css('display', 'flex').css('align-items', 'center');
            $('.ud-toolbar .ud-save-status').css('margin-left', 'auto');
            _this.context.hot.addHook('afterChange', function(changes, source) {
                if (source === 'loadData' || !changes) return;
                // Panel-driven writes (SimpleEditor / type switch) already updated
                // the model — refreshing here would rebuild the panel and steal focus
                if (source === 'panel') return;
                _this._dirty = true;
                _this._saveStatusEl.text('● 未保存').removeClass('saved').addClass('unsaved');
                // Lightweight sync: no panel rebuild scroll-to-top, no debouncing
                if (changes && changes.length) {
                    const sel = _this.context.hot.getSelected();
                    if (sel) {
                        const [ri, ci, r2, c2] = sel;
                        _this.propertyPanel.refresh(ri, ci, r2, c2, { debounce: false, noScroll: true });
                        for (let tool of _this.tools) {
                            if (tool.refresh) tool.refresh(ri, ci, r2, c2);
                        }
                    }
                }
            });
        });
    }

    _quickSave(silent) {
        const _this = this;
        if (!window._reportFile) return;
        const content = tableToXml(this.context);
        window._lastXml = content; // sync for inline Cmd+S handler
        const saveIcon = $('.ureportplus-save').first();
        $.ajax({
            url: window._server + '/designer/saveReportFile',
            data: { content, file: window._reportFile },
            type: 'POST',
            success() {
                resetDirty();
                _this._dirty = false;
                _this._saveStatusEl.text('● 已保存').removeClass('unsaved').addClass('saved');
                saveIcon.css('color', '#16a34a').attr('title', '已保存');
                setTimeout(() => saveIcon.css('color', '#0e90d2').attr('title', '保存 (Ctrl+S)'), 2000);
                _this._toast('✓ 已保存');
            },
            error(xhr) {
                const msg = xhr && xhr.responseText ? xhr.responseText.substring(0, 80) : '保存失败';
                saveIcon.css('color', '#dc2626').attr('title', msg);
                setTimeout(() => saveIcon.css('color', '#0e90d2').attr('title', '保存 (Ctrl+S)'), 5000);
                _this._toast('✕ 保存失败');
            }
        });
    }

    _toast(msg) {
        if (!$('#ud-toast-css').length) {
            $('<style id="ud-toast-css">').text(
                '.ud-toast{position:fixed;top:20px;left:50%;transform:translateX(-50%);z-index:99999;' +
                'background:#16a34a;color:#fff;padding:8px 24px;border-radius:8px;font-size:14px;font-weight:500;' +
                'box-shadow:0 4px 12px rgba(0,0,0,.15);animation:udToastIn .3s ease;pointer-events:none}' +
                '@keyframes udToastIn{from{opacity:0;transform:translateX(-50%) translateY(-10px)}to{opacity:1;transform:translateX(-50%) translateY(0)}}' +
                '.ud-toast-out{opacity:0;transition:opacity .3s}'
            ).appendTo('head');
        }
        const el = $('<div class="ud-toast">' + msg + '</div>');
        $('body').append(el);
        setTimeout(() => { el.addClass('ud-toast-out'); setTimeout(() => el.remove(), 300); }, 1500);
    }
    buildPropertyPanel(){
        const propContainerId='_prop_container';
        const dsContainerId='_datasource_container';
        const propertyPanel=$('<div class="ud-property-panel"/>');
        this.container.prepend(propertyPanel);
        const propertyTab=$(`<ul class="nav nav-tabs">
            <li class="active">
                <a href="#${propContainerId}" data-toggle="tab" id="__prop_tab_link">${window.i18n.panel.property}</a>
            </li>
            <li>
                <a href="#${dsContainerId}" data-toggle="tab">${window.i18n.panel.datasource}</a>
            </li>
        </ul>`);
        const trigger=$(`
            <i class="glyphicon glyphicon-circle-arrow-down ud-panel-collapse-icon"
                title="${window.i18n.panel.tip}">
             </i>
            `);
        propertyTab.append(trigger);
        propertyPanel.append(propertyTab);
        propertyTab.mousedown(function (e) {
            e.preventDefault();
        });
        const tabContent=$(`<div class="tab-content" style="min-height: 300px"/>`);
        const propContainer=$(`<div id="${propContainerId}" class="tab-pane fade in active"></div>`);
        const dsContainer=$(`<div id="${dsContainerId}" class="tab-pane fade"></div>`);
        tabContent.append(propContainer);
        tabContent.append(dsContainer);
        propContainer.append(this.propertyPanel.buildPanel());
        dsContainer.append(this.datasourcePanel.buildPanel());
        propertyPanel.append(tabContent);
        propertyPanel.draggable();
        trigger.click(function(){
            tabContent.toggle();
            const display=tabContent.css("display");
            if(!display || display==='none'){
                trigger.removeClass("glyphicon-circle-arrow-down");
                trigger.addClass("glyphicon-circle-arrow-left");
            }else{
                trigger.removeClass("glyphicon-circle-arrow-left");
                trigger.addClass("glyphicon-circle-arrow-down");
            }
        });
    }

    buildTools(context){
        const toolbar=$(`<div class="btn-group ud-toolbar top-toolbar"></div>`);
        this.container.prepend(toolbar);
        this.tools=[];
        // File operations
        this.tools.push(new PreviewTool(context));
        this.tools.push(new SaveTool(context));
        this.tools.push(new OpenTool(context));
        this.tools.push(new ImportTool(context));
        // Undo/Redo
        this.tools.push(new RedoTool(context));
        this.tools.push(new UndoTool(context));
        // Cell operations
        this.tools.push(new MergeTool(context));
        // Alignment
        this.tools.push(new AlignLeftTool(context));
        this.tools.push(new AlignTopTool(context));
        // Borders
        this.tools.push(new BorderTool(context));
        // Font
        this.tools.push(new FontFamilyTool(context));
        this.tools.push(new FontSizeTool(context));
        this.tools.push(new BoldTool(context));
        this.tools.push(new ItalicTool(context));
        this.tools.push(new UnderlineTool(context));
        // Colors
        this.tools.push(new BgcolorTool(context));
        this.tools.push(new ForecolorTool(context));
        // Insert
        this.tools.push(new CrosstabTool(context));
        this.tools.push(new ImageTool(context));
        this.tools.push(new ZxingTool(context));
        this.tools.push(new ChartTool(context));
        // Settings
        this.tools.push(new SettingsTool(context));
        this.tools.push(new SearchFormSwitchTool(context));

        // Separators between logical groups: file | undo | cell | align | border | font | color | insert | settings
        const sepIndices = [4, 6, 7, 9, 10, 15, 17, 21];
        for (let i = 0; i < this.tools.length; i++) {
            if (sepIndices.indexOf(i) !== -1) {
                toolbar.append($('<span class="ud-toolbar-sep"></span>'));
            }
            toolbar.append(this.tools[i].buildButton());
        }
    }
}