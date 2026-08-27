/**
 * PropertyState — lightweight reactive state for the property panel.
 * Supports multi-cell mixed-state display, undo/redo integration, and
 * change listeners for toolbar sync.
 */
import {setDirty,undoManager} from '../Utils.js';

export default class PropertyState {
    constructor(context) {
        this.context = context;
        this.listeners = [];

        // Current selection
        this.selection = null;      // { ri, ci, r2, c2 }
        this.isMulti = false;
        this.cellCount = 0;
        this.anchorCellDef = null;  // first cell's CellDefinition (for value type)

        // All selected CellDefinitions
        this.cellDefs = [];

        // Mixed-state tracking: fields whose values differ across selected cells
        this.mixedFields = new Set();

        // Current content value type (null if mixed across cells)
        this.currentValueType = null;
        this.currentZxingCategory = null;
        this.currentChartType = null;

        // Guard to prevent write-back loops during show()
        this.initialized = false;

        // Row/col indices mirror
        this.rowIndex = -1;
        this.colIndex = -1;
        this.row2Index = -1;
        this.col2Index = -1;
    }

    onChange(callback) {
        this.listeners.push(callback);
    }

    notify() {
        this.listeners.forEach(fn => fn(this));
        // Repaint the grid so style changes take effect immediately
        this._renderTable();
    }

    _renderTable() {
        const hot = this.context && this.context.hot;
        if (hot && hot.render) {
            try { hot.render(); } catch (e) {}
        }
    }

    /**
     * Update selection from Handsontable afterSelectionEnd event.
     */
    updateSelection(ri, ci, r2, c2) {
        this.selection = { ri, ci, r2, c2 };
        this.rowIndex = ri;
        this.colIndex = ci;
        this.row2Index = r2;
        this.col2Index = c2;
        this.cellDefs = this._collectCellDefs(ri, ci, r2, c2);
        this.isMulti = this.cellDefs.length > 1;
        this.cellCount = this.cellDefs.length;
        this.anchorCellDef = this.cellDefs[0] || null;
        this._computeMixedFields();
        this.currentValueType = this._computeCommonValueType();
        this.currentZxingCategory = this._computeZxingCategory();
        this.currentChartType = this._computeChartType();
        this.notify();
    }

    /**
     * Read a property value. Returns undefined if mixed across cells.
     * @param {string} path - dot-separated path, e.g. 'cellStyle.fontFamily'
     * @param {*} defaultValue
     */
    get(path, defaultValue) {
        if (this.mixedFields.has(path)) return undefined;
        if (!this.anchorCellDef) return defaultValue;
        const val = deepGet(this.anchorCellDef, path);
        return val !== undefined ? val : defaultValue;
    }

    /**
     * Check if a field is in mixed state.
     */
    isMixed(path) {
        return this.mixedFields.has(path);
    }

    /**
     * Write a property value to ALL selected cells. Registers undo command.
     * @param {string} path - dot-separated path
     * @param {*} value
     */
    set(path, value) {
        if (!this.cellDefs.length) return;
        // Snapshot the target cells so undo/redo always hits the same cells,
        // even if the selection changes afterwards
        const defs = this.cellDefs.slice();
        const undoCommands = [];
        defs.forEach(cd => {
            const oldValue = deepGet(cd, path);
            undoCommands.push({ cd, path, oldValue });
            deepSet(cd, path, value);
        });
        // Register undo/redo (module singleton — context.undoManager is never set)
        undoManager.add({
            redo: () => {
                defs.forEach(cd => deepSet(cd, path, value));
                this._recomputeAndNotify();
            },
            undo: () => {
                undoCommands.forEach(cmd => {
                    if (cmd.oldValue === undefined) {
                        deepDelete(cmd.cd, cmd.path);
                    } else {
                        deepSet(cmd.cd, cmd.path, cmd.oldValue);
                    }
                });
                this._recomputeAndNotify();
            }
        });
        setDirty();
        this._recomputeAndNotify();
    }

    /**
     * Bulk-write multiple properties at once (single undo entry).
     * @param {Array<{path: string, value: *}>} updates
     */
    setBulk(updates) {
        if (!this.cellDefs.length) return;
        const defs = this.cellDefs.slice();
        const undoCommands = [];
        updates.forEach(({path, value}) => {
            defs.forEach(cd => {
                const oldValue = deepGet(cd, path);
                undoCommands.push({ cd, path, oldValue });
                deepSet(cd, path, value);
            });
        });
        undoManager.add({
            redo: () => {
                updates.forEach(({path, value}) => {
                    defs.forEach(cd => deepSet(cd, path, value));
                });
                this._recomputeAndNotify();
            },
            undo: () => {
                undoCommands.forEach(cmd => {
                    if (cmd.oldValue === undefined) {
                        deepDelete(cmd.cd, cmd.path);
                    } else {
                        deepSet(cmd.cd, cmd.path, cmd.oldValue);
                    }
                });
                this._recomputeAndNotify();
            }
        });
        setDirty();
        this._recomputeAndNotify();
    }

    /**
     * Iterate over all selected cells for custom operations.
     */
    forEachCell(fn) {
        this.cellDefs.forEach(cd => fn(cd));
    }

    // ---- Internal ----

    _collectCellDefs(ri, ci, r2, c2) {
        const defs = [];
        const seen = new Set();
        for (let i = ri; i <= r2; i++) {
            for (let j = ci; j <= c2; j++) {
                const cd = this.context.getCell(i, j);
                if (cd && !seen.has(cd)) {
                    seen.add(cd);
                    defs.push(cd);
                }
            }
        }
        return defs;
    }

    _computeMixedFields() {
        this.mixedFields.clear();
        if (this.cellDefs.length < 2) return;
        const first = this.cellDefs[0];
        const paths = [
            // CellStyle
            'cellStyle.fontFamily', 'cellStyle.fontSize', 'cellStyle.bold',
            'cellStyle.italic', 'cellStyle.underline', 'cellStyle.forecolor',
            'cellStyle.bgcolor', 'cellStyle.format', 'cellStyle.lineHeight',
            'cellStyle.align', 'cellStyle.valign', 'cellStyle.wrapCompute',
            // Borders
            'cellStyle.leftBorder.width', 'cellStyle.leftBorder.color', 'cellStyle.leftBorder.style',
            'cellStyle.rightBorder.width', 'cellStyle.rightBorder.color', 'cellStyle.rightBorder.style',
            'cellStyle.topBorder.width', 'cellStyle.topBorder.color', 'cellStyle.topBorder.style',
            'cellStyle.bottomBorder.width', 'cellStyle.bottomBorder.color', 'cellStyle.bottomBorder.style',
            // Layout
            'expand', 'leftParentCellName', 'topParentCellName',
            // Link
            'linkUrl', 'linkTargetWindow',
            // Advanced
            'fillBlankRows', 'multiple',
            // Value type
            'value.type',
        ];
        for (const path of paths) {
            const firstVal = deepGetOrNull(first, path);
            for (let k = 1; k < this.cellDefs.length; k++) {
                const otherVal = deepGetOrNull(this.cellDefs[k], path);
                if (!_deepEqual(firstVal, otherVal)) {
                    this.mixedFields.add(path);
                    break;
                }
            }
        }
    }

    _computeCommonValueType() {
        if (!this.anchorCellDef) return 'simple';
        const firstType = this.anchorCellDef.value && this.anchorCellDef.value.type || 'simple';
        if (this.mixedFields.has('value.type')) return null;
        return firstType;
    }

    _computeZxingCategory() {
        if (this.currentValueType !== 'zxing') return null;
        const first = this.anchorCellDef;
        if (!first || !first.value) return 'qrcode';
        return first.value.category || 'qrcode';
    }

    _computeChartType() {
        if (this.currentValueType !== 'chart') return null;
        const first = this.anchorCellDef;
        if (!first || !first.value || !first.value.chart || !first.value.chart.dataset) return null;
        return first.value.chart.dataset.type || 'pie';
    }

    _recomputeAndNotify() {
        this._computeMixedFields();
        this.currentValueType = this._computeCommonValueType();
        this.currentZxingCategory = this._computeZxingCategory();
        this.currentChartType = this._computeChartType();
        this.notify();
    }
}

// ---- Utility functions ----

function deepGet(obj, path) {
    const parts = path.split('.');
    let current = obj;
    for (const part of parts) {
        if (current == null) return undefined;
        current = current[part];
    }
    return current;
}

function deepGetOrNull(obj, path) {
    const val = deepGet(obj, path);
    return val === undefined ? null : val;
}

function deepSet(obj, path, value) {
    const parts = path.split('.');
    let current = obj;
    for (let i = 0; i < parts.length - 1; i++) {
        if (current[parts[i]] == null) {
            current[parts[i]] = {};
        }
        current = current[parts[i]];
    }
    current[parts[parts.length - 1]] = value;
}

function deepDelete(obj, path) {
    const parts = path.split('.');
    let current = obj;
    for (let i = 0; i < parts.length - 1; i++) {
        if (current[parts[i]] == null) return;
        current = current[parts[i]];
    }
    delete current[parts[parts.length - 1]];
}

function _deepEqual(a, b) {
    if (a === b) return true;
    if (a == null || b == null) return a == b;
    if (typeof a !== typeof b) return false;
    if (typeof a === 'object') {
        if (Array.isArray(a) && Array.isArray(b)) {
            if (a.length !== b.length) return false;
            return a.every((v, i) => _deepEqual(v, b[i]));
        }
        // Simple object comparison (shallow keys)
        const aKeys = Object.keys(a).filter(k => a[k] !== undefined);
        const bKeys = Object.keys(b).filter(k => b[k] !== undefined);
        if (aKeys.length !== bKeys.length) return false;
        return aKeys.every(k => _deepEqual(a[k], b[k]));
    }
    return false;
}
