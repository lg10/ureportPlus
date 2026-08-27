/**
 * ChartEditor v4 — unified chart editor replacing 10 separate files.
 *
 * Consolidates: bar, line, area, horizontalBar, pie, doughnut,
 *               polarArea, radar, scatter, bubble, mix
 *
 * Dynamic fields:
 *   - Axis tab: hidden for pie/doughnut/polarArea/radar
 *   - Dataset binding: category-based (bar family) vs scatter/bubble vs pie family
 *   - Options: same for all (title, legend, animation, data labels, layout)
 *
 * Reuses ChartValueEditor base for option methods.
 */
import ChartValueEditor from './chart/ChartValueEditor.js';
import CategoryChartValueEditor from './chart/CategoryChartValueEditor.js';
import ScatterChartValueEditor from './chart/ScatterChartValueEditor.js';
import BubbleChartValueEditor from './chart/BubbleChartValueEditor.js';
import {setDirty} from '../../Utils.js';

// Chart types that hide the axis tab
const NO_AXIS_TYPES = ['pie', 'doughnut', 'polarArea', 'radar'];

// Chart types using scatter/bubble dataset binding
const SCATTER_TYPES = ['scatter'];
const BUBBLE_TYPES = ['bubble'];

// Chart types using category-based binding
const CATEGORY_TYPES = ['bar', 'line', 'area', 'horizontalBar', 'pie', 'doughnut', 'polarArea', 'radar'];

// All available chart types for the dropdown
const CHART_TYPE_OPTIONS = [
    { value: 'bar', label: '柱状图' },
    { value: 'line', label: '折线图' },
    { value: 'area', label: '面积图' },
    { value: 'horizontalBar', label: '横向柱状图' },
    { value: 'pie', label: '饼图' },
    { value: 'doughnut', label: '环形图' },
    { value: 'polarArea', label: '极区图' },
    { value: 'radar', label: '雷达图' },
    { value: 'scatter', label: '散点图' },
    { value: 'bubble', label: '气泡图' },
];

export default class ChartEditor {
    constructor(parentContainer, context) {
        this.context = context;
        this.container = $(`<div style="display:none"></div>`);
        parentContainer.append(this.container);
        this._currentType = 'bar';
        this._editors = {};  // lazily created type-specific editors
        this._subContainer = null;
        this._init();
    }

    _init() {
        const self = this;

        // Chart type selector
        const typeRow = $(`<div class="ud-prop-v4-field" style="margin-bottom:8px"><label>图表类型</label></div>`);
        this.typeSelect = $(`<select class="field-input"></select>`);
        CHART_TYPE_OPTIONS.forEach(opt => {
            this.typeSelect.append(`<option value="${opt.value}">${opt.label}</option>`);
        });
        this.typeSelect.on('change', function () {
            const newType = $(this).val();
            self._switchChartType(newType);
        });
        typeRow.append(this.typeSelect);
        this.container.append(typeRow);

        // Dynamic editor area
        this.editorArea = $(`<div></div>`);
        this.container.append(this.editorArea);
    }

    _switchChartType(newType) {
        if (!this.cellDef) return;

        // Update chart type in cellDef
        const cd = this.cellDef;
        if (!cd.value.chart) {
            cd.value.chart = { dataset: {} };
        }
        if (!cd.value.chart.dataset) {
            cd.value.chart.dataset = {};
        }
        cd.value.chart.dataset.type = newType;

        // Update chart widget if exists
        if (cd.chartWidget && cd.chartWidget.chart) {
            cd.chartWidget.chart.config.type = newType;
            cd.chartWidget.chart.update();
        }

        // Re-show with new type
        this._currentType = newType;
        this._renderEditor(newType);
        setDirty();
    }

    show(cellDef, rowIndex, colIndex, row2Index, col2Index) {
        this.cellDef = cellDef;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.row2Index = row2Index;
        this.col2Index = col2Index;
        this.container.show();

        // Determine current chart type
        const chart = cellDef.value.chart || {};
        const dataset = chart.dataset || {};
        const currentType = dataset.type || 'bar';
        this._currentType = currentType;

        // Update type selector
        this.typeSelect.val(currentType);

        // Render editor for current type
        this._renderEditor(currentType);
    }

    hide() {
        this.container.hide();
        // Do NOT empty editorArea — cached type editors must keep their DOM
    }

    _renderEditor(chartType) {
        const self = this;

        // Hide every cached editor container, then show the target one.
        // Never empty() the area: cached editors keep their DOM attached.
        Object.keys(this._editors).forEach(key => {
            const e = this._editors[key];
            if (e && e.container) e.container.hide();
        });

        let editor = this._editors[chartType];
        if (!editor) {
            if (CATEGORY_TYPES.indexOf(chartType) >= 0) {
                editor = new CategoryChartValueEditor();
                editor.id = chartType;
                editor.context = this.context;
                editor.container = $(`<div style="display:none"></div>`);
                this.editorArea.append(editor.container);
                this._delegateInit(editor, chartType);
                editor.show = function (cellDef, rowIndex, colIndex) {
                    self._showCategoryEditor(this, cellDef, rowIndex, colIndex);
                };
            } else if (SCATTER_TYPES.indexOf(chartType) >= 0) {
                editor = new ScatterChartValueEditor(this.editorArea, this.context);
            } else if (BUBBLE_TYPES.indexOf(chartType) >= 0) {
                editor = new BubbleChartValueEditor(this.editorArea, this.context);
            }
            if (editor) this._editors[chartType] = editor;
        }

        if (editor && editor.show) {
            editor.cellDef = this.cellDef;
            editor.rowIndex = this.rowIndex;
            editor.colIndex = this.colIndex;
            editor.row2Index = this.row2Index;
            editor.col2Index = this.col2Index;
            if (editor.container) editor.container.show();
            editor.show(this.cellDef, this.rowIndex, this.colIndex, this.row2Index, this.col2Index);
        }
    }

    /**
     * Populate a category-family editor from the cell's chart config.
     * Mirrors the original BarChartValueEditor.show() logic.
     */
    _showCategoryEditor(editor, cellDef, rowIndex, colIndex) {
        editor.cellDef = cellDef;
        editor.rowIndex = rowIndex;
        editor.colIndex = colIndex;
        const chart = cellDef.value.chart || {};
        const dataset = chart.dataset || {};

        editor.datasetSelect.empty();
        const datasources = this.context.reportDef.datasources || [];
        // The dataset select's change handler iterates editor.datasources
        editor.datasources = datasources;
        for (let ds of datasources) {
            for (let d of (ds.datasets || [])) {
                editor.datasetSelect.append(`<option>${d.name}</option>`);
            }
        }
        editor.datasetSelect.append(`<option selected></option>`);
        editor.datasetSelect.val(dataset.datasetName);
        editor.datasetSelect.trigger('change');
        editor.categoryPropertySelect.val(dataset.categoryProperty);
        if (editor.seriesPropertySelect) editor.seriesPropertySelect.val(dataset.seriesProperty);
        if (editor.seriesTextEditor) editor.seriesTextEditor.val(dataset.seriesText);
        editor.valuePropertySelect.val(dataset.valueProperty);
        if (editor.aggregateSelect) editor.aggregateSelect.val(dataset.collectType);
        if (dataset.seriesType === 'property' && editor.propertySeriesRadio) {
            editor.propertySeriesRadio.children('input').attr('checked', true);
            editor.propertySeriesRadio.children('input').trigger('click');
        } else if (editor.textSeriesRadio) {
            editor.textSeriesRadio.children('input').attr('checked', true);
            editor.textSeriesRadio.children('input').trigger('click');
        }
        if (editor.formatEditor) editor.formatEditor.val(dataset.format);

        // Axes (only present for non-pie family types)
        if (editor.xAxesRotationEditor) {
            const xaxes = chart.xaxes || { rotation: 0, xposition: 'left' };
            editor.xAxesRotationEditor.val(xaxes.rotation);
            const xScaleLabel = xaxes.scaleLabel || {};
            if (xScaleLabel.display) {
                editor.showXTitleRadio.trigger('click');
                editor.xTitleEditor.val(xScaleLabel.labelString);
            } else {
                editor.hideXTitleRadio.trigger('click');
            }
        }
        if (editor.yAxesRotationEditor) {
            const yaxes = chart.yaxes || { rotation: 0, yposition: 'bottom' };
            editor.yAxesRotationEditor.val(yaxes.rotation);
            const yScaleLabel = yaxes.scaleLabel || {};
            if (yScaleLabel.display) {
                editor.showYTitleRadio.trigger('click');
                editor.yTitleEditor.val(yScaleLabel.labelString);
            } else {
                editor.hideYTitleRadio.trigger('click');
            }
        }

        if (editor.hideDataLabelsRadio) editor.hideDataLabelsRadio.children('input').attr('checked', true);
        const plugins = chart.plugins || [];
        for (let plugin of plugins) {
            if (plugin.name === 'data-labels' && plugin.display && editor.showDataLabelsRadio) {
                editor.showDataLabelsRadio.children('input').attr('checked', true);
            }
        }
        const options = chart.options || [];
        for (let option of options) {
            switch (option.type) {
                case 'animation':
                    if (editor.durationEditor) editor.durationEditor.val(option.duration);
                    if (editor.easingSelect) editor.easingSelect.val(option.easing);
                    break;
                case 'title':
                    if (!editor.showTitleRadio) break;
                    if (option.display) {
                        editor.showTitleRadio.children('input').attr('checked', true);
                        editor.titlePositionSelect.val(option.position);
                        editor.titleTextEditor.val(option.text);
                        editor.titleTextGroup.show();
                        editor.titlePositionGroup.show();
                    } else {
                        editor.hideTitleRadio.children('input').attr('checked', true);
                        editor.titleTextGroup.hide();
                        editor.titlePositionGroup.hide();
                    }
                    break;
                case 'layout': {
                    const layout = option.layout || { left: 0, right: 0, top: 0, bottom: 0 };
                    if (editor.upPaddingEditor) {
                        editor.upPaddingEditor.val(layout.top);
                        editor.downPaddingEditor.val(layout.bottom);
                        editor.leftPaddingEditor.val(layout.left);
                        editor.rightPaddingEditor.val(layout.right);
                    }
                    break;
                }
                case 'legend':
                    if (!editor.showLegendRadio) break;
                    if (option.display) {
                        editor.showLegendRadio.children('input').attr('checked', true);
                        editor.legendPositionGroup.show();
                        editor.legendPositionSelect.val(option.position);
                    } else {
                        editor.hideLegendRadio.children('input').attr('checked', true);
                        editor.legendPositionGroup.hide();
                    }
                    break;
            }
        }
    }

    _delegateInit(editor, chartType) {
        // Reuse existing CategoryChartValueEditor setup for each type
        // This is a simplified version — the actual setup mirrors BarChartValueEditor
        const tabUL = $(`<ul class="nav nav-tabs"></ul>`);
        editor.container.append(tabUL);

        const dsLI = $(`<li class="active"><a href="#chart_bind_dataset_${chartType}" data-toggle="tab">${window.i18n.chart.datasetBind}</a></li>`);
        tabUL.append(dsLI);

        const optionLI = $(`<li><a href="#chart_option_${chartType}" data-toggle="tab">${window.i18n.chart.option}</a></li>`);
        tabUL.append(optionLI);

        // Axis tab — hidden for pie/doughnut/polar/radar
        if (NO_AXIS_TYPES.indexOf(chartType) < 0) {
            const axisLI = $(`<li><a href="#chart_axis_${chartType}" data-toggle="tab">${window.i18n.chart.axisConfig}</a></li>`);
            tabUL.append(axisLI);
        }

        const tabContent = $(`<div class="tab-content"></div>`);
        editor.container.append(tabContent);

        // Dataset tab
        const dsContent = $(`<div class="tab-pane fade in active" id="chart_bind_dataset_${chartType}"></div>`);
        tabContent.append(dsContent);
        editor.initCategoryDataset(dsContent);

        // Options tab
        const optionContent = $(`<div class="tab-pane fade" id="chart_option_${chartType}"></div>`);
        tabContent.append(optionContent);
        const optGroup = $(`<div></div>`);
        optionContent.append(optGroup);
        editor.initTitleOption(optGroup);
        editor.initLegendOption(optGroup);
        editor.initDataLabelsOption(optGroup);
        editor.initAnimationsOption(optGroup);
        editor.initPaddingOption(optGroup);

        // Axis tab
        if (NO_AXIS_TYPES.indexOf(chartType) < 0) {
            const axisContent = $(`<div class="tab-pane fade" id="chart_axis_${chartType}"></div>`);
            tabContent.append(axisContent);
            editor.initXAxes(axisContent);
            editor.initYAxes(axisContent);
        }

        // Initialize Bootstrap tabs
        tabUL.find('a[data-toggle="tab"]').on('click', function (e) {
            e.preventDefault();
            $(this).tab('show');
        });
    }
}
