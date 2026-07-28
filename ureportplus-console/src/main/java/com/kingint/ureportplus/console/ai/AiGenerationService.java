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
import com.kingint.ureportplus.definition.ReportDefinition;
import com.kingint.ureportplus.definition.value.AggregateType;
import com.kingint.ureportplus.definition.value.DatasetValue;
import com.kingint.ureportplus.definition.value.ExpressionValue;
import com.kingint.ureportplus.definition.value.SimpleValue;
import com.kingint.ureportplus.expression.ExpressionUtils;

/**
 * Multi-agent Loop AI generation service.
 *
 * Architecture:
 *   ┌─────────────┐     ┌──────────────┐     ┌─────────────┐
 *   │ Generator    │────▶│ Validator     │────▶│ Applicator  │
 *   │ (generate)   │     │ (verify)      │     │ (apply)     │
 *   └──────┬───────┘     └──────┬────────┘     └─────────────┘
 *          │                    │
 *          │    ┌───────────────┘
 *          │    │ validation fails
 *          ▼    ▼
 *   ┌──────────────────┐
 *   │  Feedback Loop   │  ◀── up to maxRetries iterations
 *   │  (error → retry) │
 *   └──────────────────┘
 *
 * Each agent uses an independent system prompt for unbiased verification.
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
		ReportContextBuilder contextBuilder = new ReportContextBuilder(reportDef);
		AiConfig config = AiConfig.getInstance();
		int maxLoops = config.getMaxRetries() + 1; // 1 initial + N retries

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
		List<String> allWarnings = new ArrayList<String>();

		// === MULTI-AGENT LOOP ===
		for (int loop = 0; loop < maxLoops; loop++) {
			log.info("[AI Loop {}/{}] Starting iteration", loop + 1, maxLoops);

			// --- STEP 1: Generator Agent ---
			String genPrompt = userInput.toString();
			if (lastFeedback != null) {
				genPrompt += "\n\n[上一轮验证反馈 — 请修正以下问题]\n" + lastFeedback;
			}
			lastResponse = client.chat(genSystemPrompt, genPrompt);
			log.info("[AI Loop {}/{}] Generator produced {} chars", loop + 1, maxLoops, lastResponse.length());

			// --- STEP 2: Parse ---
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

			// --- STEP 3: Validator Agent (independent) ---
			String valInput = buildValidatorInput(modifications);
			String valResponse = client.chat(valSystemPrompt, valInput);
			log.info("[AI Loop {}/{}] Validator response: {}", loop + 1, maxLoops,
					valResponse.substring(0, Math.min(200, valResponse.length())));

			ValidationResult valResult = parseValidationResult(valResponse);

			// --- STEP 4: Check validation ---
			if (valResult != null && valResult.approved) {
				// --- STEP 5: Syntax + Semantic check ---
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
				bestModifications = modifications; // keep best so far
			} else {
				lastFeedback = "验证器返回格式异常，请重新生成。";
				allWarnings.add("Loop " + (loop + 1) + ": validator parse error");
				bestModifications = modifications;
			}
		}

		// Build result
		AiResult result = new AiResult();
		result.modifications = bestModifications != null ? bestModifications : new ArrayList<CellModification>();
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

		// Include dataset context for field validation
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
		String json = response.trim();
		if (json.startsWith("```")) {
			int start = json.indexOf("[");
			int end = json.lastIndexOf("]");
			if (start >= 0 && end > start) json = json.substring(start, end + 1);
		}
		if (!json.startsWith("[")) {
			int start = json.indexOf("[");
			int end = json.lastIndexOf("]");
			if (start >= 0 && end > start) json = json.substring(start, end + 1);
			else if (json.startsWith("{")) json = "[" + json + "]";
			else throw new RuntimeException("Response is not valid JSON array");
		}
		return mapper.readValue(json, new TypeReference<List<CellModification>>() {});
	}

	private ValidationResult parseValidationResult(String response) {
		try {
			String json = response.trim();
			if (json.startsWith("```")) {
				int start = json.indexOf("{");
				int end = json.lastIndexOf("}");
				if (start >= 0 && end > start) json = json.substring(start, end + 1);
			}
			if (!json.startsWith("{")) {
				int start = json.indexOf("{");
				int end = json.lastIndexOf("}");
				if (start >= 0 && end > start) json = json.substring(start, end + 1);
			}
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

	private CellDefinition findCell(String cellName) {
		if (reportDef.getCells() != null) {
			for (CellDefinition cell : reportDef.getCells()) {
				if (cellName.equals(cell.getName())) return cell;
			}
		}
		return null;
	}

	// ═══════════════════════════════════════════
	// Apply Modifications
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
		public String explanation;
	}

	public static class AiResult {
		public List<CellModification> modifications;
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
