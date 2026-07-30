package com.kingint.ureportplus.console.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kingint.ureportplus.definition.CellDefinition;
import com.kingint.ureportplus.definition.ColumnDefinition;
import com.kingint.ureportplus.definition.ReportDefinition;
import com.kingint.ureportplus.definition.RowDefinition;
import com.kingint.ureportplus.definition.value.AggregateType;
import com.kingint.ureportplus.definition.value.DatasetValue;
import com.kingint.ureportplus.definition.value.ExpressionValue;
import com.kingint.ureportplus.definition.value.SimpleValue;
import com.kingint.ureportplus.expression.ExpressionUtils;

/**
 * Multi-agent Loop AI generation service with cell + structural operation support.
 */
public class AiGenerationService {
	private static final Logger log = LoggerFactory.getLogger(AiGenerationService.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	private final OpenAiClient client;
	private final ReportDefinition reportDef;
	private final Map<String, Object> context;

	public AiGenerationService(ReportDefinition reportDef) {
		this.reportDef = reportDef;
		this.client = new OpenAiClient();
		ReportContextBuilder contextBuilder = new ReportContextBuilder(reportDef);
		this.context = contextBuilder.buildContext();
	}

	/**
	 * Multi-agent Loop: Generator → Validator → (retry | apply)
	 */
	public AiResult generate(String userPrompt, List<String> selectedCellNames) throws Exception {
		AiConfig config = AiConfig.getInstance();
		int maxLoops = config.getMaxRetries() + 1;

		String genSystemPrompt = buildGeneratorPrompt();
		String valSystemPrompt = buildValidatorPrompt();

		StringBuilder userInput = new StringBuilder();
		userInput.append(userPrompt);
		if (selectedCellNames != null && !selectedCellNames.isEmpty()) {
			userInput.append("\n\n用户选中的单元格: ").append(String.join(", ", selectedCellNames));
		}
		userInput.append("\n\n请生成单元格修改JSON。");

		String lastResponse = null;
		String lastFeedback = null;
		List<CellModification> bestModifications = null;
		List<Map<String, Object>> structuralOps = null;
		List<String> allWarnings = new ArrayList<String>();

		for (int loop = 0; loop < maxLoops; loop++) {
			log.info("[AI Loop {}/{}] Starting iteration", loop + 1, maxLoops);

			String genPrompt = userInput.toString();
			if (lastFeedback != null) {
				genPrompt += "\n\n[上一轮验证反馈 — 请修正以下问题]\n" + lastFeedback;
			}
			lastResponse = client.chat(genSystemPrompt, genPrompt);
			log.info("[AI Loop {}/{}] Generator produced {} chars", loop + 1, maxLoops, lastResponse.length());

			List<CellModification> modifications;
			try {
				modifications = parseResponse(lastResponse);
			} catch (Exception e) {
				lastFeedback = "JSON解析失败: " + e.getMessage() + "。请确保输出严格的JSON数组格式。";
				allWarnings.add("Loop " + (loop + 1) + ": " + lastFeedback);
				continue;
			}

			if (modifications.isEmpty()) {
				lastFeedback = "返回了空数组。请根据用户需求生成具体的单元格修改。";
				allWarnings.add("Loop " + (loop + 1) + ": empty response");
				continue;
			}

			String valInput = buildValidatorInput(modifications);
			String valResponse = client.chat(valSystemPrompt, valInput);

			ValidationResult valResult = parseValidationResult(valResponse);

			if (valResult != null && valResult.approved) {
				List<CellModification> validMods = new ArrayList<CellModification>();
				for (CellModification mod : modifications) {
					String error = validateModification(mod);
					if (error != null) {
						allWarnings.add(mod.cellName + ": " + error);
					} else {
						validMods.add(mod);
					}
				}
				bestModifications = validMods;
				if (!validMods.isEmpty()) {
					log.info("[AI Loop {}/{}] APPROVED with {} valid modifications", loop + 1, maxLoops, validMods.size());
					break;
				} else {
					lastFeedback = "所有" + modifications.size() + "个修改项都未通过语法/语义校验。请检查表达式格式和字段名。";
					allWarnings.add("Loop " + (loop + 1) + ": " + lastFeedback);
				}
			} else if (valResult != null) {
				lastFeedback = valResult.feedback;
				allWarnings.add("Loop " + (loop + 1) + ": validator rejected - " + valResult.feedback);
				bestModifications = modifications;
			} else {
				lastFeedback = "验证器返回格式异常，请重新生成。";
				allWarnings.add("Loop " + (loop + 1) + ": validator parse error");
				bestModifications = modifications;
			}
		}

		// Generate structural ops based on validated modifications
		if (bestModifications != null) {
			structuralOps = inferStructuralOps(bestModifications);
		}

		AiResult result = new AiResult();
		result.modifications = bestModifications != null ? bestModifications : new ArrayList<CellModification>();
		result.structuralOps = structuralOps != null ? structuralOps : new ArrayList<Map<String, Object>>();
		result.warnings = allWarnings;
		result.rawResponse = lastResponse;
		result.success = !result.modifications.isEmpty();

		return result;
	}

	// ═══════════════════════════════════════════
	// Agent System Prompts
	// ═══════════════════════════════════════════

	private String buildGeneratorPrompt() {
		ReportContextBuilder contextBuilder = new ReportContextBuilder(reportDef);
		return contextBuilder.buildSystemPrompt(context);
	}

	private String buildValidatorPrompt() {
		StringBuilder sb = new StringBuilder();
		sb.append("你是一个 UReportPlus 报表表达式校验专家。\n");
		sb.append("你的任务是检查 Generator 生成的单元格修改方案是否正确。\n\n");
		sb.append("校验规则：\n");
		sb.append("1. 检查表达式语法是否符合 UReportPlus 规范\n");
		sb.append("   - 数据集表达式: datasetName.aggregate(property)\n");
		sb.append("   - 聚合类型: select, group, sum, count, avg, max, min, customgroup\n");
		sb.append("   - 函数: abs, ceil, round, sum(list), avg(list), count(list), list(), 等\n");
		sb.append("2. 检查引用的数据集中是否存在指定的字段\n");
		sb.append("3. 检查单元格引用格式 (如 &A1, $A1, #, A1:B3)\n");
		sb.append("4. 检查逻辑是否合理：\n");
		sb.append("   - 分组(group)单元格 expand 应为 Down 或 Right\n");
		sb.append("   - 聚合(sum/count/avg) 单元格 expand 应为 None\n");
		sb.append("   - 小计单元格的 leftParent 应指向分组单元格\n");
		sb.append("5. 如果 JSON 格式不正确或不符合 Schema，也需指出\n\n");
		sb.append("输出格式 (严格的 JSON):\n");
		sb.append("{\n");
		sb.append("  \"approved\": true/false,\n");
		sb.append("  \"score\": 0-100,\n");
		sb.append("  \"issues\": [\"问题描述1\", \"问题描述2\"],\n");
		sb.append("  \"feedback\": \"如果 rejected，提供具体的修正建议\"\n");
		sb.append("}\n");
		sb.append("只在 approved=true 且 score>=70 时才批准。");
		return sb.toString();
	}

	private String buildValidatorInput(List<CellModification> modifications) {
		StringBuilder sb = new StringBuilder();
		sb.append("请校验以下 Generator 生成的单元格修改方案:\n\n");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> datasets = (List<Map<String, Object>>) context.get("datasets");
		sb.append("可用数据集:\n");
		for (Map<String, Object> ds : datasets) {
			sb.append("  - ").append(ds.get("name")).append(": 字段=").append(ds.get("fields")).append("\n");
		}
		sb.append("\n修改方案:\n");
		sb.append("```json\n");
		try {
			sb.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(modifications));
		} catch (Exception e) {
			sb.append(modifications.toString());
		}
		sb.append("\n```\n");
		return sb.toString();
	}

	// ═══════════════════════════════════════════
	// Response Parsing
	// ═══════════════════════════════════════════

	@SuppressWarnings("unchecked")
	private List<CellModification> parseResponse(String response) throws Exception {
		String json = extractJsonArray(response);
		if (json == null) throw new RuntimeException("Response is not valid JSON array");
		return mapper.readValue(json, new TypeReference<List<CellModification>>() {});
	}

	private String extractJsonArray(String text) {
		text = text.trim();
		if (text.startsWith("```")) { int end = text.lastIndexOf("```"); if (end > 3) text = text.substring(3, end).trim(); }
		int lastClose = text.lastIndexOf("]");
		if (lastClose < 0) return null;
		int depth = 0, open = -1;
		for (int i = lastClose; i >= 0; i--) {
			char c = text.charAt(i);
			if (c == ']') depth++;
			else if (c == '[') { depth--; if (depth == 0) { open = i; break; } }
		}
		return (open >= 0 && lastClose > open) ? text.substring(open, lastClose + 1) : null;
	}

	private String extractJson(String text) {
		text = text.trim();
		if (text.startsWith("```")) { int end = text.lastIndexOf("```"); if (end > 3) text = text.substring(3, end).trim(); }
		int lastClose = text.lastIndexOf("}");
		if (lastClose < 0) return null;
		int depth = 0, open = -1;
		for (int i = lastClose; i >= 0; i--) {
			char c = text.charAt(i);
			if (c == '}') depth++;
			else if (c == '{') { depth--; if (depth == 0) { open = i; break; } }
		}
		return (open >= 0 && lastClose > open) ? text.substring(open, lastClose + 1) : null;
	}

	private ValidationResult parseValidationResult(String response) {
		try {
			String json = extractJson(response);
			if (json == null) return null;
			Map<String, Object> map = mapper.readValue(json, Map.class);
			ValidationResult vr = new ValidationResult();
			vr.approved = Boolean.TRUE.equals(map.get("approved"));
			vr.score = map.get("score") instanceof Number ? ((Number) map.get("score")).intValue() : 0;
			vr.feedback = (String) map.get("feedback");
			@SuppressWarnings("unchecked")
			List<String> issues = (List<String>) map.get("issues");
			vr.issues = issues != null ? issues : new ArrayList<String>();
			return vr;
		} catch (Exception e) {
			log.debug("Failed to parse validator response: {}", e.getMessage());
			return null;
		}
	}

	// ═══════════════════════════════════════════
	// Modification Validation
	// ═══════════════════════════════════════════

	private String validateModification(CellModification mod) {
		if (mod.cellName == null || mod.cellName.isEmpty()) return "缺少cellName";
		if (mod.type == null) return "缺少type";

		CellDefinition cell = findCell(mod.cellName);
		if (cell == null) return "单元格 " + mod.cellName + " 不存在";

		if ("expression".equals(mod.type) && mod.value != null) {
			try {
				ExpressionUtils.parseExpression(mod.value);
			} catch (Exception e) {
				return "表达式语法错误: " + e.getMessage();
			}
		}
		if ("dataset".equals(mod.type) && mod.property != null && mod.datasetId != null) {
			if (!fieldExists(mod.datasetId, mod.property)) {
				return "数据集 " + mod.datasetId + " 中不存在字段 " + mod.property;
			}
		}
		if (mod.aggregate != null) {
			try {
				AggregateType.valueOf(mod.aggregate);
			} catch (IllegalArgumentException e) {
				return "无效的聚合类型: " + mod.aggregate;
			}
		}
		return null;
	}

	private boolean fieldExists(String datasetName, String fieldName) {
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> datasets = (List<Map<String, Object>>) context.get("datasets");
		for (Map<String, Object> ds : datasets) {
			if (datasetName.equals(ds.get("name"))) {
				@SuppressWarnings("unchecked")
				List<String> fields = (List<String>) ds.get("fields");
				return fields != null && fields.contains(fieldName);
			}
		}
		return false;
	}

	public CellDefinition findCellByName(String cellName) {
		return findCell(cellName);
	}

	private CellDefinition findCell(String cellName) {
		if (reportDef.getCells() != null) {
			for (CellDefinition cell : reportDef.getCells()) {
				if (cellName.equals(cell.getName())) return cell;
			}
		}
		return null;
	}

	// ═══════════════════════════════════════════
	// Apply Cell Modifications
	// ═══════════════════════════════════════════

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<Map<String, Object>> applyModifications(List<CellModification> modifications) {
		List<Map<String, Object>> appliedCells = new ArrayList<Map<String, Object>>();
		for (CellModification mod : modifications) {
			CellDefinition cell = findCell(mod.cellName);
			if (cell == null) continue;

			if ("simple".equals(mod.type)) {
				cell.setValue(new SimpleValue(mod.value != null ? mod.value : ""));
			} else if ("expression".equals(mod.type)) {
				cell.setValue(new ExpressionValue(mod.value != null ? mod.value : ""));
			} else if ("dataset".equals(mod.type)) {
				DatasetValue dv = new DatasetValue();
				if (mod.datasetId != null) dv.setDatasetName(mod.datasetId);
				if (mod.property != null) dv.setProperty(mod.property);
				if (mod.aggregate != null) {
					try { dv.setAggregate(AggregateType.valueOf(mod.aggregate)); } catch (IllegalArgumentException ignored) {}
				}
				cell.setValue(dv);
			}
			if (mod.expand != null) {
				try { cell.setExpand(com.kingint.ureportplus.definition.Expand.valueOf(mod.expand)); } catch (IllegalArgumentException ignored) {}
			}
			if (mod.leftParent != null) cell.setLeftParentCellName(mod.leftParent);
			if (mod.topParent != null) cell.setTopParentCellName(mod.topParent);

			Map<String, Object> applied = new HashMap<String, Object>();
			applied.put("cellName", cell.getName());
			applied.put("type", mod.type);
			applied.put("value", mod.value);
			applied.put("expand", mod.expand);
			applied.put("explanation", mod.explanation);
			appliedCells.add(applied);
		}
		return appliedCells;
	}

	// ═══════════════════════════════════════════
	// Apply Structural Operations
	// ═══════════════════════════════════════════

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<Map<String, Object>> applyStructuralOps(List<Map<String, Object>> ops) {
		List<Map<String, Object>> applied = new ArrayList<Map<String, Object>>();
		for (Map<String, Object> op : ops) {
			String type = (String) op.get("type");
			if (type == null) continue;

			try {
				if ("insertRow".equals(type)) {
					int rowNum = getInt(op, "rowNumber");
					int height = getInt(op, "height", 25);
					// Shift existing rows
					for (int i = reportDef.getRows().size() - 1; i >= 0; i--) {
						RowDefinition row = reportDef.getRows().get(i);
						if (row.getRowNumber() > rowNum) {
							row.setRowNumber(row.getRowNumber() + 1);
						}
					}
					// Shift cells
					for (CellDefinition cell : reportDef.getCells()) {
						if (cell.getRowNumber() > rowNum) {
							cell.setRowNumber(cell.getRowNumber() + 1);
						}
					}
					RowDefinition newRow = new RowDefinition();
					newRow.setRowNumber(rowNum + 1);
					newRow.setHeight(height);
					reportDef.getRows().add(newRow);
					applied.add(makeOpResult("insertRow", "row " + (rowNum + 1), (String)op.get("description")));
				}
				else if ("insertCol".equals(type)) {
					int colNum = getInt(op, "colNumber");
					int width = getInt(op, "width", 100);
					for (int i = reportDef.getColumns().size() - 1; i >= 0; i--) {
						ColumnDefinition col = reportDef.getColumns().get(i);
						if (col.getColumnNumber() > colNum) {
							col.setColumnNumber(col.getColumnNumber() + 1);
						}
					}
					for (CellDefinition cell : reportDef.getCells()) {
						if (cell.getColumnNumber() > colNum) {
							cell.setColumnNumber(cell.getColumnNumber() + 1);
						}
					}
					ColumnDefinition newCol = new ColumnDefinition();
					newCol.setColumnNumber(colNum + 1);
					newCol.setWidth(width);
					reportDef.getColumns().add(newCol);
					applied.add(makeOpResult("insertCol", "col " + (colNum + 1), (String)op.get("description")));
				}
				else if ("deleteRow".equals(type)) {
					int rowNum = getInt(op, "rowNumber");
					RowDefinition toRemove = null;
					for (RowDefinition row : reportDef.getRows()) {
						if (row.getRowNumber() == rowNum) { toRemove = row; break; }
					}
					if (toRemove != null) {
						reportDef.getRows().remove(toRemove);
						// Shift remaining rows down
						for (RowDefinition row : reportDef.getRows()) {
							if (row.getRowNumber() > rowNum) row.setRowNumber(row.getRowNumber() - 1);
						}
						// Remove cells in this row
						List<CellDefinition> toDelete = new ArrayList<CellDefinition>();
						for (CellDefinition cell : reportDef.getCells()) {
							if (cell.getRowNumber() == rowNum) toDelete.add(cell);
							else if (cell.getRowNumber() > rowNum) cell.setRowNumber(cell.getRowNumber() - 1);
						}
						reportDef.getCells().removeAll(toDelete);
						applied.add(makeOpResult("deleteRow", "row " + rowNum, (String)op.get("description")));
					}
				}
				else if ("deleteCol".equals(type)) {
					int colNum = getInt(op, "colNumber");
					ColumnDefinition toRemove = null;
					for (ColumnDefinition col : reportDef.getColumns()) {
						if (col.getColumnNumber() == colNum) { toRemove = col; break; }
					}
					if (toRemove != null) {
						reportDef.getColumns().remove(toRemove);
						for (ColumnDefinition col : reportDef.getColumns()) {
							if (col.getColumnNumber() > colNum) col.setColumnNumber(col.getColumnNumber() - 1);
						}
						List<CellDefinition> toDelete = new ArrayList<CellDefinition>();
						for (CellDefinition cell : reportDef.getCells()) {
							if (cell.getColumnNumber() == colNum) toDelete.add(cell);
							else if (cell.getColumnNumber() > colNum) cell.setColumnNumber(cell.getColumnNumber() - 1);
						}
						reportDef.getCells().removeAll(toDelete);
						applied.add(makeOpResult("deleteCol", "col " + colNum, (String)op.get("description")));
					}
				}
				else if ("setBand".equals(type)) {
					int rowNum = getInt(op, "rowNumber");
					String band = (String) op.get("band");
					for (RowDefinition row : reportDef.getRows()) {
						if (row.getRowNumber() == rowNum) {
							row.setBand(com.kingint.ureportplus.definition.Band.valueOf(band));
							break;
						}
					}
					applied.add(makeOpResult("setBand", "row " + rowNum + "=" + band, (String)op.get("description")));
				}
				else if ("setRowHeight".equals(type)) {
					int rowNum = getInt(op, "rowNumber");
					int height = getInt(op, "height", 25);
					for (RowDefinition row : reportDef.getRows()) {
						if (row.getRowNumber() == rowNum) { row.setHeight(height); break; }
					}
					applied.add(makeOpResult("setRowHeight", "row " + rowNum + "=" + height, (String)op.get("description")));
				}
				else if ("setColWidth".equals(type)) {
					int colNum = getInt(op, "colNumber");
					int width = getInt(op, "width", 100);
					for (ColumnDefinition col : reportDef.getColumns()) {
						if (col.getColumnNumber() == colNum) { col.setWidth(width); break; }
					}
					applied.add(makeOpResult("setColWidth", "col " + colNum + "=" + width, (String)op.get("description")));
				}
			} catch (Exception e) {
				log.warn("Failed to apply structural op {}: {}", type, e.getMessage());
			}
		}
		return applied;
	}

	/** Infer structural ops from cell modifications (e.g. if AI targets cells beyond current table bounds). */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> inferStructuralOps(List<CellModification> mods) {
		List<Map<String, Object>> ops = new ArrayList<Map<String, Object>>();
		int maxRow = reportDef.getRows() != null ? reportDef.getRows().size() : 0;
		int maxCol = reportDef.getColumns() != null ? reportDef.getColumns().size() : 0;

		for (CellModification mod : mods) {
			if (mod.cellName == null) continue;
			// Parse cell name like "B5" to get row/col
			String cellName = mod.cellName.toUpperCase();
			int split = 0;
			while (split < cellName.length() && Character.isLetter(cellName.charAt(split))) split++;
			if (split == 0) continue;
			try {
				String colStr = cellName.substring(0, split);
				int colNum = 0;
				for (int i = 0; i < colStr.length(); i++) {
					colNum = colNum * 26 + (colStr.charAt(i) - 'A' + 1);
				}
				int rowNum = Integer.parseInt(cellName.substring(split));

				// Add rows if needed
				while (rowNum > maxRow) {
					Map<String, Object> op = new HashMap<String, Object>();
					op.put("type", "insertRow");
					op.put("rowNumber", maxRow);
					op.put("height", 25);
					op.put("description", "自动添加行" + (maxRow + 1) + "以容纳单元格" + cellName);
					ops.add(op);
					maxRow++;
				}
				// Add columns if needed
				while (colNum > maxCol) {
					Map<String, Object> op = new HashMap<String, Object>();
					op.put("type", "insertCol");
					op.put("colNumber", maxCol);
					op.put("width", 100);
					op.put("description", "自动添加列以容纳单元格" + cellName);
					ops.add(op);
					maxCol++;
				}
			} catch (NumberFormatException ignored) {}
		}
		return ops;
	}

	private int getInt(Map<String, Object> map, String key) {
		Object v = map.get(key);
		if (v instanceof Number) return ((Number) v).intValue();
		if (v instanceof String) return Integer.parseInt((String) v);
		return 0;
	}
	private int getInt(Map<String, Object> map, String key, int defaultVal) {
		Object v = map.get(key);
		if (v instanceof Number) return ((Number) v).intValue();
		if (v instanceof String) { try { return Integer.parseInt((String) v); } catch (NumberFormatException e) {} }
		return defaultVal;
	}
	private Map<String, Object> makeOpResult(String type, String target, String desc) {
		Map<String, Object> r = new HashMap<String, Object>();
		r.put("type", type);
		r.put("target", target);
		r.put("description", desc != null ? desc : "");
		return r;
	}

	// ═══════════════════════════════════════════
	// Data Classes
	// ═══════════════════════════════════════════

	public static class CellModification {
		public String cellName;
		public String type;
		public String value;
		public String datasetId;
		public String aggregate;
		public String property;
		public String expand;
		public String leftParent;
		public String topParent;
		public String explanation;
	}

	public static class AiResult {
		public List<CellModification> modifications;
		public List<Map<String, Object>> structuralOps;
		public List<String> warnings;
		public String rawResponse;
		public boolean success;
	}

	private static class ValidationResult {
		boolean approved;
		int score;
		String feedback;
		List<String> issues;
	}
}
