package com.kingint.ureportplus.console.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.kingint.ureportplus.definition.CellDefinition;
import com.kingint.ureportplus.definition.ColumnDefinition;
import com.kingint.ureportplus.definition.ReportDefinition;
import com.kingint.ureportplus.definition.RowDefinition;
import com.kingint.ureportplus.definition.dataset.DatasetDefinition;
import com.kingint.ureportplus.definition.dataset.Field;
import com.kingint.ureportplus.definition.dataset.Parameter;
import com.kingint.ureportplus.definition.dataset.SqlDatasetDefinition;
import com.kingint.ureportplus.definition.datasource.DatasourceDefinition;
import com.kingint.ureportplus.definition.value.DatasetValue;
import com.kingint.ureportplus.definition.value.ExpressionValue;
import com.kingint.ureportplus.definition.value.SimpleValue;
import com.kingint.ureportplus.definition.value.Value;
import com.kingint.ureportplus.definition.value.ValueType;

/**
 * Builds structured context for the AI about the current report including
 * datasets, expression syntax, cell layout, row/col dimensions, bands, and merges.
 */
public class ReportContextBuilder {

	private final ReportDefinition reportDef;

	public ReportContextBuilder(ReportDefinition reportDef) {
		this.reportDef = reportDef;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Map<String, Object> buildContext() {
		Map<String, Object> context = new HashMap<String, Object>();

		// 1. Dataset schemas
		List<Map<String, Object>> datasets = new ArrayList<Map<String, Object>>();
		if (reportDef.getDatasources() != null) {
			for (DatasourceDefinition dsDef : reportDef.getDatasources()) {
				if (dsDef.getDatasets() != null) {
					for (DatasetDefinition ds : dsDef.getDatasets()) {
						Map<String, Object> dsInfo = new HashMap<String, Object>();
						dsInfo.put("name", ds.getName());
						List<String> fields = new ArrayList<String>();
						if (ds.getFields() != null) {
							for (Field f : ds.getFields()) fields.add(f.getName());
						}
						dsInfo.put("fields", fields);
						if (ds instanceof SqlDatasetDefinition) {
							SqlDatasetDefinition sqlDs = (SqlDatasetDefinition) ds;
							dsInfo.put("sql", sqlDs.getSql());
							List<Map<String, Object>> params = new ArrayList<Map<String, Object>>();
							if (sqlDs.getParameters() != null) {
								for (Parameter p : sqlDs.getParameters()) {
									Map<String, Object> pInfo = new HashMap<String, Object>();
									pInfo.put("name", p.getName());
									pInfo.put("type", p.getType() != null ? p.getType().name() : "String");
									pInfo.put("defaultValue", p.getDefaultValue());
									params.add(pInfo);
								}
							}
							dsInfo.put("parameters", params);
						}
						datasets.add(dsInfo);
					}
				}
			}
		}
		context.put("datasets", datasets);

		// 2. Expression syntax reference
		Map<String, Object> syntax = new HashMap<String, Object>();
		syntax.put("datasetPattern", "datasetName.aggregate(property, conditions?, order?)");
		syntax.put("aggregates", new String[]{
			"select", "group", "customgroup", "regroup", "reselect",
			"sum", "count", "avg", "max", "min"
		});
		syntax.put("functions", new String[]{
			"abs", "ceil", "floor", "round", "pow", "sqrt", "log",
			"sin", "cos", "tan", "random", "median", "mode",
			"sum(list)", "avg(list)", "count(list)", "max(list)", "min(list)",
			"list(...args)", "get(list, index)", "get(list, index, property)",
			"order(list, 'asc'|'desc')",
			"json(jsonString, propertyPath)", "param(paramName)", "paramIsEmpty(paramName)",
			"formatnumber(num, pattern)", "formatdate(date, pattern)",
			"row()", "column()", "page()", "pages()",
			"page_sum(cell)", "page_avg(cell)", "page_max(cell)", "page_min(cell)", "page_count(cell)",
			"upper(str)", "lower(str)", "trim(str)", "substring(str, start, end?)",
			"replace(str, old, new)", "length(str)", "indexof(str, search)",
			"date(year,month,day)", "day(date)", "month(date)", "year(date)", "week(date)",
			"chn(number)", "chnmoney(number)",
			"stdevp(list)", "vara(list)"
		});
		syntax.put("cellReference", new String[]{
			"&A1  -- 绝对引用A1单元格的值",
			"$A1  -- 相对引用A1单元格",
			"#    -- 当前单元格的值",
			"#.fieldName -- 当前单元格绑定对象的属性",
			"A1   -- 直接引用A1单元格",
			"A1[变量] -- 带坐标的单元格引用",
			"A1:A5 -- 单元格范围"
		});
		syntax.put("conditionals", "if(条件, 真值块, 假值块)\ncase{条件1: 值1; 条件2: 值2; ...}");
		syntax.put("operators", new String[]{
			"+ - * / %", "> < == != >= <=",
			"in, not in", "like", "and/&&, or/||", "!"
		});
		context.put("expressionSyntax", syntax);

		// 3. Cell layout
		List<Map<String, Object>> cells = new ArrayList<Map<String, Object>>();
		if (reportDef.getCells() != null) {
			for (CellDefinition cell : reportDef.getCells()) {
				Map<String, Object> cellInfo = new HashMap<String, Object>();
				cellInfo.put("name", cell.getName());
				cellInfo.put("row", cell.getRowNumber());
				cellInfo.put("col", cell.getColumnNumber());
				cellInfo.put("expand", cell.getExpand() != null ? cell.getExpand().name() : "None");
				Value value = cell.getValue();
				if (value != null) {
					cellInfo.put("type", value.getType().name());
					if (value instanceof SimpleValue) {
						cellInfo.put("simpleValue", ((SimpleValue) value).getValue());
					} else if (value instanceof ExpressionValue) {
						cellInfo.put("expressionValue", ((ExpressionValue) value).getValue());
					} else if (value instanceof DatasetValue) {
						DatasetValue dv = (DatasetValue) value;
						cellInfo.put("datasetName", dv.getDatasetName());
						cellInfo.put("aggregate", dv.getAggregate() != null ? dv.getAggregate().name() : "group");
						cellInfo.put("property", dv.getProperty());
					}
				}
				if (cell.getLeftParentCellName() != null) cellInfo.put("leftParent", cell.getLeftParentCellName());
				if (cell.getTopParentCellName() != null) cellInfo.put("topParent", cell.getTopParentCellName());
				cells.add(cellInfo);
			}
		}
		context.put("cells", cells);

		// 4. Row/column structure
		context.put("totalRows", reportDef.getRows() != null ? reportDef.getRows().size() : 0);
		context.put("totalColumns", reportDef.getColumns() != null ? reportDef.getColumns().size() : 0);

		// 5. Row details
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		if (reportDef.getRows() != null) {
			for (RowDefinition row : reportDef.getRows()) {
				Map<String, Object> ri = new HashMap<String, Object>();
				ri.put("number", row.getRowNumber());
				ri.put("height", row.getHeight());
				if (row.getBand() != null) ri.put("band", row.getBand().name());
				rows.add(ri);
			}
		}
		context.put("rows", rows);

		// 6. Column details
		List<Map<String, Object>> columns = new ArrayList<Map<String, Object>>();
		if (reportDef.getColumns() != null) {
			for (ColumnDefinition col : reportDef.getColumns()) {
				Map<String, Object> ci = new HashMap<String, Object>();
				ci.put("number", col.getColumnNumber());
				ci.put("width", col.getWidth());
				columns.add(ci);
			}
		}
		context.put("columns", columns);

		// 7. Merged cells
		List<Map<String, Object>> merges = new ArrayList<Map<String, Object>>();
		if (reportDef.getCells() != null) {
			for (CellDefinition cell : reportDef.getCells()) {
				if (cell.getColSpan() > 1 || cell.getRowSpan() > 1) {
					Map<String, Object> m = new HashMap<String, Object>();
					m.put("cell", cell.getName());
					m.put("rowSpan", cell.getRowSpan());
					m.put("colSpan", cell.getColSpan());
					merges.add(m);
				}
			}
		}
		context.put("merges", merges);

		return context;
	}

	/**
	 * Build the system prompt for the AI agent with full structural capabilities.
	 */
	@SuppressWarnings("unchecked")
	public String buildSystemPrompt(Map<String, Object> context) {
		StringBuilder sb = new StringBuilder();
		sb.append("你是一个 UReportPlus 报表设计助手。你将根据用户的自然语言描述，生成报表单元格的修改指令。\n\n");

		sb.append("## 可用数据集\n");
		List<Map<String, Object>> datasets = (List<Map<String, Object>>) context.get("datasets");
		if (datasets.isEmpty()) {
			sb.append("(无数据集配置)\n");
		} else {
			for (Map<String, Object> ds : datasets) {
				sb.append("- **").append(ds.get("name")).append("**: 字段=[");
				List<String> fields = (List<String>) ds.get("fields");
				sb.append(String.join(", ", fields));
				sb.append("]");
				if (ds.containsKey("sql")) sb.append(", SQL: ").append(ds.get("sql"));
				sb.append("\n");
			}
		}

		sb.append("\n## 表达式语法\n");
		Map<String, Object> syntax = (Map<String, Object>) context.get("expressionSyntax");
		sb.append("- 数据集表达式: ").append(syntax.get("datasetPattern")).append("\n");
		sb.append("- 聚合类型: ").append(String.join(", ", (String[]) syntax.get("aggregates"))).append("\n");
		sb.append("- 可用函数: ").append(String.join(", ", (String[]) syntax.get("functions"))).append("\n");
		sb.append("- 单元格引用: ").append(String.join("; ", (String[]) syntax.get("cellReference"))).append("\n");
		sb.append("- 条件语句: ").append(syntax.get("conditionals")).append("\n");

		sb.append("\n## 当前报表布局\n");
		sb.append("总行数: ").append(context.get("totalRows"));
		sb.append(", 总列数: ").append(context.get("totalColumns")).append("\n\n");

		List<Map<String, Object>> cells = (List<Map<String, Object>>) context.get("cells");
		sb.append("| 单元格 | 行 | 列 | 类型 | 值/表达式 | 展开 | 左父格 | 上父格 |\n");
		sb.append("|--------|----|----|------|----------|------|--------|--------|\n");
		for (Map<String, Object> cell : cells) {
			sb.append("| ").append(cell.get("name"));
			sb.append(" | ").append(cell.get("row"));
			sb.append(" | ").append(cell.get("col"));
			sb.append(" | ").append(cell.get("type"));
			Object val = null;
			if (cell.containsKey("simpleValue")) val = cell.get("simpleValue");
			else if (cell.containsKey("expressionValue")) val = cell.get("expressionValue");
			else if (cell.containsKey("datasetName"))
				val = cell.get("datasetName") + "." + cell.get("aggregate") + "(" + cell.get("property") + ")";
			sb.append(" | ").append(val != null ? val : "");
			sb.append(" | ").append(cell.get("expand"));
			sb.append(" | ").append(cell.get("leftParent") != null ? cell.get("leftParent") : "");
			sb.append(" | ").append(cell.get("topParent") != null ? cell.get("topParent") : "");
			sb.append(" |\n");
		}

		if (context.containsKey("rows")) {
			List<Map<String, Object>> rows = (List<Map<String, Object>>) context.get("rows");
			sb.append("\n## 行结构\n");
			for (Map<String, Object> r : rows) {
				sb.append("- 行").append(r.get("number")).append(": 高=").append(r.get("height"));
				if (r.get("band") != null) sb.append(" band=").append(r.get("band"));
				sb.append("\n");
			}
		}
		if (context.containsKey("columns")) {
			List<Map<String, Object>> cols = (List<Map<String, Object>>) context.get("columns");
			sb.append("\n## 列结构\n");
			for (Map<String, Object> c : cols) {
				sb.append("- 列").append(c.get("number")).append(": 宽=").append(c.get("width")).append("\n");
			}
		}
		if (context.containsKey("merges") && !((List<?>)context.get("merges")).isEmpty()) {
			sb.append("\n## 合并单元格\n");
			for (Map<String, Object> m : (List<Map<String, Object>>)context.get("merges")) {
				sb.append("- ").append(m.get("cell"));
				sb.append(" rowSpan=").append(m.get("rowSpan")).append(" colSpan=").append(m.get("colSpan")).append("\n");
			}
		}

		sb.append("\n## 输出格式\n");
		sb.append("你必须返回一个严格的JSON数组，每个元素代表一个单元格修改：\n");
		sb.append("```json\n");
		sb.append("[\n");
		sb.append("  {\n");
		sb.append("    \"cellName\": \"B3\",\n");
		sb.append("    \"type\": \"dataset|expression|simple\",\n");
		sb.append("    \"value\": \"具体的表达式或文本值\",\n");
		sb.append("    \"datasetId\": \"数据集名称(仅type=dataset时)\",\n");
		sb.append("    \"aggregate\": \"select|group|sum|count|avg|max|min|customgroup(仅type=dataset时)\",\n");
		sb.append("    \"property\": \"字段名(仅type=dataset时)\",\n");
		sb.append("    \"expand\": \"Down|Right|None\",\n");
		sb.append("    \"leftParent\": \"左父格名称,如A2\",\n");
		sb.append("    \"topParent\": \"上父格名称\",\n");
		sb.append("    \"explanation\": \"修改说明\"\n");
		sb.append("  }\n");
		sb.append("]\n");
		sb.append("```\n");
		sb.append("规则：\n");
		sb.append("1. 只返回JSON数组，不要有任何额外的文字说明\n");
		sb.append("2. 数据集表达式格式: datasetName.aggregate(property, conditions?, order?)\n");
		sb.append("3. 表达式必须使用上述可用数据集中的字段名\n");
		sb.append("4. 分组(group)单元格的expand应为Down或Right\n");
		sb.append("5. 汇总(sum/count/avg等)单元格的expand应为None\n");
		sb.append("6. 设置左父格可以限定聚合范围\n");
		sb.append("7. 如果用户的请求无法实现，返回空数组[]\n");
		sb.append("8. cellName 格式为列字母+行号(如 A1, B3, C2)\n");

		return sb.toString();
	}
}
