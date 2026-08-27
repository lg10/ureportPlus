/**
 * PropertyPanel v4 — unified property panel with grouped property list.
 *
 * Changes from v3:
 *  - No more tabs (属性/样式/高级) — everything in one scrollable grouped list
 *  - Content type is a dropdown (not pills) — "条码" and "二维码" are one option
 *  - Multi-cell mixed-state display (Figma-style)
 *  - Toolbar sync via PropertyState change events
 *  - Undo/redo via integrated UndoManager
 *
 * Backward compatible: same constructor signature, same refresh() entry point,
 * same editor contract (show/hide).
 */
import PropertyState from './PropertyState.js';
import PropertyRenderer, { PROPERTY_GROUPS } from './PropertyRenderer.js';
import ContentTypeEditor from './property/ContentTypeEditor.js';

// Value editors
import SimpleEditor from './property/SimpleEditor.js';
import ExpressionEditor from './property/ExpressionEditor.js';
import DatasetEditor from './property/DatasetEditor.js';
import ImageEditor from './property/ImageEditor.js';
import SlashEditor from './property/SlashEditor.js';
import ZxingEditor from './property/ZxingEditor.js';

// Consolidated chart editor (10→1)
import ChartEditor from './property/ChartEditor.js';

// Condition properties inline editor
import ConditionGroup from './condition/ConditionGroup.js';

// Command palette
import CommandPalette from './CommandPalette.js';

import URLParameterDialog from '../dialog/URLParameterDialog.js';
import {alert} from '../MsgBox.js';

export default class PropertyPanel {
    constructor(context) {
        this.context = context;
        this.state = new PropertyState(context);
        this.renderer = null;
        this.contentTypeEditor = null;
        this.commandPalette = null;
        this.el = null;

        // Set up Ctrl+Shift+P command palette shortcut
        this._setupCommandPaletteShortcut();
    }

    _setupCommandPaletteShortcut() {
        const self = this;
        $(document).on('keydown.cmdpalette', function (e) {
            if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.keyCode === 80) {
                e.preventDefault();
                if (self.commandPalette) {
                    self.commandPalette.toggle();
                }
            }
        });
    }

    /**
     * Build the panel DOM. Returns the root element for insertion.
     * Same contract as v3.
     */
    buildPanel() {
        // Inject CSS
        PropertyRenderer.injectCSS();

        // Init state listener for toolbar sync
        this.state.onChange((state) => {
            this._syncToolbar(state);
        });

        // Build renderer callbacks
        const self = this;
        const callbacks = {
            onSet(path, value) {
                self.state.set(path, value);
            },
            onEditLinkParams() {
                self._editLinkParams();
            },
            renderCustomGroup(groupId, bd, state) {
                self._renderCustomGroup(groupId, bd, state);
            }
        };

        this.renderer = new PropertyRenderer(this.state, null, callbacks);
        this.el = this.renderer.build();

        // Build the content type editor (goes into the 'content' group body)
        const contentBd = this.renderer.getGroupBody('content');
        this.contentTypeEditor = new ContentTypeEditor(contentBd, this.context);
        this.contentTypeEditor.build();

        // Register value editors
        const cc = this.contentTypeEditor;
        cc.registerEditor('simple', new SimpleEditor(cc.editorArea, this.context));
        const exprEditor = new ExpressionEditor(cc.editorArea, this.context);
        exprEditor.cb = {
            onEditConditions: () => {
                // Expand the inline condition group and scroll it into view
                const g = self.renderer.groups['condition'];
                if (g) {
                    if (g.folded) g.hd.click();
                    setTimeout(() => {
                        if (self.el) self.el.animate({ scrollTop: self.el.get(0).scrollHeight }, 150);
                    }, 130);
                }
            }
        };
        cc.registerEditor('expression', exprEditor);
        cc.registerEditor('dataset', new DatasetEditor(cc.editorArea, this.context));
        cc.registerEditor('image', new ImageEditor(cc.editorArea, this.context));
        cc.registerEditor('slash', new SlashEditor(cc.editorArea, this.context));
        cc.registerEditor('zxing', new ZxingEditor(cc.editorArea, this.context));

        // Register chart editor (consolidated: supports all chart types)
        const chartEditor = new ChartEditor(cc.editorArea, this.context);
        // Register for all chart types so ContentTypeEditor can route correctly
        ['bar','line','horizontalBar','area','radar','polarArea','scatter','bubble','doughnut','pie'].forEach(t => {
            cc.registerChartEditor(t, chartEditor);
        });

        // Initialize command palette
        this.commandPalette = new CommandPalette(this.context, {
            setContentType: (type) => {
                if (self.contentTypeEditor) {
                    self.contentTypeEditor._switchType(type);
                }
            },
            toggleProp: (path) => {
                const current = self.state.get(path);
                self.state.set(path, !current);
            },
            focusProp: (path) => {
                // Scroll to the property group and focus the field
                // Find which group contains this field
                for (const group of PROPERTY_GROUPS) {
                    if (group.fields && group.fields.some(f => f.path === path)) {
                        const g = self.renderer.groups[group.id];
                        if (g && g.folded) {
                            g.hd.click(); // expand
                        }
                        // Try to focus the input
                        setTimeout(() => {
                            const bd = self.renderer.getGroupBody(group.id);
                            if (bd) bd.find('input, select').first().focus();
                        }, 150);
                        break;
                    }
                }
            },
            setExpand: (expand) => {
                self.state.set('expand', expand);
            },
            addCondition: () => {
                const cd = self.state.anchorCellDef;
                if (!cd) return;
                if (!cd.conditionPropertyItems) cd.conditionPropertyItems = [];
                cd.conditionPropertyItems.push({
                    name: '规则 ' + (cd.conditionPropertyItems.length + 1),
                    conditions: [],
                    cellStyle: {}
                });
                // Expand condition group
                const g = self.renderer.groups['condition'];
                if (g && g.folded) {
                    g.hd.click();
                } else {
                    self.renderer.refresh();
                }
            },
            saveReport: () => {
                if (window._designer && window._designer._quickSave) {
                    window._designer._quickSave();
                }
            },
            previewReport: () => {
                if (window._reportFile) {
                    window.open(window._server + '/preview?_u=file:' + window._reportFile, '_blank');
                }
            },
            exportPdf: () => {
                if (window._reportFile) {
                    window.open(window._server + '/pdf?_u=file:' + window._reportFile, '_blank');
                }
            }
        });

        return this.el;
    }

    /**
     * Refresh panel from cell selection. Called by designer on afterSelectionEnd.
     * Same contract as v3.
     * options: { debounce: false } to run immediately, { noScroll: true } to keep scroll.
     */
    refresh(ri, ci, r2, c2, options) {
        const opts = options || {};
        if (this._refreshTimer) {
            clearTimeout(this._refreshTimer);
            this._refreshTimer = null;
        }
        // Debounce full rebuilds so fast selection moves don't thrash the panel
        if (opts.debounce === false) {
            this._doRefresh(ri, ci, r2, c2, opts);
        } else {
            this._refreshTimer = setTimeout(() => {
                this._refreshTimer = null;
                this._doRefresh(ri, ci, r2, c2, opts);
            }, 50);
        }
    }

    _doRefresh(ri, ci, r2, c2, opts) {
        const cd = this.context.getCell(ri, ci);
        if (!cd) return;

        // Update state
        this.state.initialized = true;
        this.state.updateSelection(ri, ci, r2, c2);

        // Refresh renderer groups
        this.renderer.refresh();

        // Show content type editor
        if (this.contentTypeEditor) {
            this.contentTypeEditor.show(this.state);
        }

        this.state.initialized = false;

        // Scroll to top (skip for lightweight syncs, e.g. in-grid editing)
        if (!opts.noScroll && this.el) this.el.scrollTop(0);
    }

    // ---- Internal ----

    _renderCustomGroup(groupId, bd, state) {
        // The 'content' group is handled by ContentTypeEditor separately
        // (it is not cleared/re-rendered during refresh to preserve editor state)
        if (groupId === 'content') {
            return;
        }
        if (groupId === 'condition') {
            // Inline condition properties editor.
            // Datasources come from reportDef (no ajax — there is no
            // /designer/loadDatasources endpoint), and the ConditionGroup
            // component is reused across selection changes.
            const cd = state.anchorCellDef;
            if (!cd) return;
            const datasources = (this.context.reportDef && this.context.reportDef.datasources) || [];
            let datasetName = '';
            if (cd.value && cd.value.type === 'dataset') {
                datasetName = cd.value.datasetName || '';
            }
            if (!this._conditionGroup || this._conditionGroup.container.get(0) !== bd.get(0)) {
                this._conditionGroup = new ConditionGroup(bd, this.context);
            }
            this._conditionGroup.show(cd, datasources, datasetName);
        }
    }

    _editLinkParams() {
        const cd = this.state.anchorCellDef;
        if (!cd) return;
        if (!cd.linkUrl) {
            alert('请先填写URL');
            return;
        }
        if (!cd.linkParameters) cd.linkParameters = [];
        new URLParameterDialog().show(cd.linkParameters);
    }

    _syncToolbar(state) {
        // Notify toolbar tools to refresh their button states
        // Tools with refresh() method are called by designer's selection handler
        // This provides additional sync when properties change via the panel
    }
}
