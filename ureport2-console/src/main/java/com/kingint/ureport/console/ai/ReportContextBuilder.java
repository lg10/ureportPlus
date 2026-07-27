package com.kingint.ureport.console.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.kingint.ureport.definition.CellDefinition;
import com.kingint.ureport.definition.ReportDefinition;
import com.kingint.ureport.definition.dataset.DatasetDefinition;
import com.kingint.ureport.definition.dataset.Field;
import com.kingint.ureport.definition.dataset.Parameter;
import com.kingint.ureport.definition.dataset.SqlDatasetDefinition;
import com.kingint.ureport.definition.datasource.DatasourceDefinition;
import com.kingint.ureport.definition.value.DatasetValue;
import com.kingint.ureport.definition.value.ExpressionValue;
import com.kingint.ureport.definition.value.SimpleValue;
import com.kingint.ureport.definition.value.Value;
import com.kingint.ureport.definition.value.ValueType;

/**
 * Builds structured context for the AI about the current report:
 * - dataset schemas (names, fields, SQL)
 * - expression syntax reference
 * - current cell layout with types and values
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
							for (Field f : ds.getFields()) {
								fields.add(f.getName());
							}
						}
						dsInfo.put("fields", fields);

						// Include SQL for SQL datasets
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

				if (cell.getLeftParentCellName() != null) {
					cellInfo.put("leftParent", cell.getLeftParentCellName());
				}
				if (cell.getTopParentCellName() != null) {
					cellInfo.put("topParent", cell.getTopParentCellName());
				}

				cells.add(cellInfo);
			}
		}
		context.put("cells", cells);

		// 4. Row/column structure
		context.put("totalRows", reportDef.getRows() != null ? reportDef.getRows().size() : 0);
		context.put("totalColumns", reportDef.getColumns() != null ? reportDef.getColumns().size() : 0);

		return context;
	}

	/**
	 * Build the system prompt for the AI agent.
	 */
	public String buildSystemPrompt(Map<String, Object> context) {
		StringBuilder sb = new StringBuilder();
		sb.append("你是一个 UReport2 报表设计助手。你将根据用户的自然语言描述，生成报表单元格的修改指令。\n\n");

		sb.append("## 可用数据集\n");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> datasets = (List<Map<String, Object>>) context.get("datasets");
		if (datasets.isEmpty()) {
			sb.append("(无数据集配置)\n");
		} else {
			for (Map<String, Object> ds : datasets) {
				sb.append("- **").append(ds.get("name")).append("**: 字段=[");
				@SuppressWarnings("unchecked")
				List<String> fields = (List<String>) ds.get("fields");
				sb.append(String.join(", ", fields));
				sb.append("]");
				if (ds.containsKey("sql")) {
					sb.append(", SQL: ").append(ds.get("sql"));
				}
				sb.append("\n");
			}
		}

		sb.append("\n## 表达式语法\n");
		@SuppressWarnings("unchecked")
		Map<String, Object> syntax = (Map<String, Object>) context.get("expressionSyntax");
		sb.append("- 数据集表达式: ").append(syntax.get("datasetPattern")).append("\n");
		sb.append("- 聚合类型: ").append(String.join(", ", (String[]) syntax.get("aggregates"))).append("\n");
		sb.append("- 可用函数: ").append(String.join(", ", (String[]) syntax.get("functions"))).append("\n");
		sb.append("- 单元格引用: ").append(String.join("; ", (String[]) syntax.get("cellReference"))).append("\n");
		sb.append("- 条件语句: ").append(syntax.get("conditionals")).append("\n");

		sb.append("\n## 当前报表布局\n");
		sb.append("总行数: ").append(context.get("totalRows"));
		sb.append(", 总列数: ").append(context.get("totalColumns")).append("\n");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> cells = (List<Map<String, Object>>) context.get("cells");
		sb.append("| 单元格 | 行 | 列 | 类型 | 值/表达式 | 展开 | 左父格 |\n");
		sb.append("|--------|----|----|------|----------|------|--------|\n");
		for (Map<String, Object> cell : cells) {
			sb.append("| ").append(cell.get("name"));
			sb.append(" | ").append(cell.get("row"));
			sb.append(" | ").append(cell.get("col"));
			sb.append(" | ").append(cell.get("type"));
			Object val = null;
			if (cell.containsKey("simpleValue")) val = cell.get("simpleValue");
			else if (cell.containsKey("expressionValue")) val = cell.get("expressionValue");
			else if (cell.containsKey("datasetName")) {
				val = cell.get("datasetName") + "." + cell.get("aggregate") + "(" + cell.get("property") + ")";
			}
			sb.append(" | ").append(val != null ? val : "");
			sb.append(" | ").append(cell.get("expand"));
			sb.append(" | ").append(cell.get("leftParent") != null ? cell.get("leftParent") : "");
			sb.append(" |\n");
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
		sb.append("6. 设置左父格可以限定聚合范围(如小计单元格的左父格应为分组单元格)\n");
		sb.append("7. 如果用户的请求无法实现，返回空数组[]\n");

		return sb.toString();
	}
}
