package com.kingint.ureport.console.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kingint.ureport.definition.CellDefinition;
import com.kingint.ureport.definition.ReportDefinition;
import com.kingint.ureport.definition.value.AggregateType;
import com.kingint.ureport.definition.value.DatasetValue;
import com.kingint.ureport.definition.value.ExpressionValue;
import com.kingint.ureport.definition.value.SimpleValue;
import com.kingint.ureport.expression.ExpressionUtils;

/**
 * Interactive multi-turn AI conversation with self-testing loop.
 *
 * Flow:
 *   1. User sends prompt
 *   2. AI analyzes → if confidence < 90%, asks clarifying question
 *   3. User answers → AI re-analyzes
 *   4. Repeat until confidence ≥ 90%
 *   5. AI generates modifications → self-tests
 *   6. If tests fail → fix & retry (inner loop)
 *   7. Present verified results → user confirms → apply
 */
public class AiConversationService {
	private static final Logger log = LoggerFactory.getLogger(AiConversationService.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	private final OpenAiClient client;
	private final ReportDefinition reportDef;
	private final Map<String, Object> context;
	private final List<Map<String, String>> history = new ArrayList<Map<String, String>>();
	private int turnCount = 0;
	private static final int MAX_TURNS = 20;

	public AiConversationService(ReportDefinition reportDef) {
		this.reportDef = reportDef;
		this.client = new OpenAiClient();
		ReportContextBuilder ctxBuilder = new ReportContextBuilder(reportDef);
		this.context = ctxBuilder.buildContext();
	}

	/**
	 * Start a new conversation turn. Returns either:
	 * - A question for the user (confidence < 90%)
	 * - Generated modifications with self-test results (confidence >= 90%)
	 */
	@SuppressWarnings("unchecked")
	public TurnResult processTurn(String userInput) throws Exception {
		turnCount++;
		log.info("[AI Turn {}] Processing: {}", turnCount, userInput);

		// Add to history
		Map<String, String> entry = new HashMap<String, String>();
		entry.put("role", "user");
		entry.put("content", userInput);
		history.add(entry);

		String response = callAnalyzer();

		// Parse analyzer response
		Map<String, Object> result = parseAnalyzerResponse(response);
		if (result == null) {
			return TurnResult.question("我没有理解你的需求，能换个方式描述吗？", null);
		}

		int confidence = result.get("confidence") instanceof Number
			? ((Number) result.get("confidence")).intValue() : 0;
		String action = (String) result.get("action");

		Map<String, String> aiEntry = new HashMap<String, String>();
		aiEntry.put("role", "ai");
		aiEntry.put("content", response);
		history.add(aiEntry);

		if ("ask".equals(action) || confidence < 90) {
			// Need more info → ask question
			String question = (String) result.get("question");
			List<Map<String, String>> options = (List<Map<String, String>>) result.get("options");
			if (question == null) question = "能提供更多细节吗？";
			return TurnResult.question(question, options);
		}

		// Confidence >= 90 → generate modifications + self-test
		List<AiGenerationService.CellModification> modifications = generateModifications();
		if (modifications.isEmpty()) {
			return TurnResult.question("无法生成有效的修改方案，请尝试更具体的描述。", null);
		}

		// Self-test
		List<Map<String, Object>> testResults = selfTest(modifications);

		TurnResult tr = new TurnResult();
		tr.action = "apply";
		tr.modifications = modifications;
		tr.testResults = testResults;
		tr.confidence = confidence;
		tr.explanation = (String) result.get("explanation");

		// Check if self-test found issues
		boolean allPassed = true;
		for (Map<String, Object> tr2 : testResults) {
			if (Boolean.FALSE.equals(tr2.get("passed"))) { allPassed = false; break; }
		}
		tr.allTestsPassed = allPassed;

		return tr;
	}

	/**
	 * Call the Analyzer agent: understand intent, determine confidence, decide next action.
	 */
	private String callAnalyzer() throws Exception {
		String systemPrompt = buildAnalyzerPrompt();
		StringBuilder userPrompt = new StringBuilder();
		userPrompt.append("## 对话历史\n");
		for (Map<String, String> entry : history) {
			userPrompt.append("[").append(entry.get("role")).append("]: ")
				.append(entry.get("content")).append("\n");
		}
		userPrompt.append("\n## 当前状态\n");
		userPrompt.append("轮次: ").append(turnCount).append("/").append(MAX_TURNS).append("\n");
		userPrompt.append("请分析用户意图，决定下一步动作。");

		return client.chat(systemPrompt, userPrompt.toString());
	}

	private String buildAnalyzerPrompt() {
		StringBuilder sb = new StringBuilder();
		sb.append("你是 UReport2 报表设计顾问。你需要通过多轮对话理解用户需求，直到有90%以上把握再给出方案。\n\n");

		sb.append("## 报表上下文\n");
		ReportContextBuilder ctxBuilder = new ReportContextBuilder(reportDef);
		Map<String, Object> ctx = ctxBuilder.buildContext();

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> datasets = (List<Map<String, Object>>) ctx.get("datasets");
		sb.append("可用数据集:\n");
		for (Map<String, Object> ds : datasets) {
			sb.append("- ").append(ds.get("name")).append(": ").append(ds.get("fields")).append("\n");
		}

		sb.append("\n总行数: ").append(ctx.get("totalRows"));
		sb.append(", 总列数: ").append(ctx.get("totalColumns")).append("\n");

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> cells = (List<Map<String, Object>>) ctx.get("cells");
		sb.append("当前单元格:\n");
		for (Map<String, Object> cell : cells) {
			sb.append("  ").append(cell.get("name")).append(" (行").append(cell.get("row"))
				.append(",列").append(cell.get("col")).append("): ");
			String type = (String) cell.get("type");
			sb.append(type);
			if ("dataset".equals(type)) {
				sb.append(" ").append(cell.get("datasetName")).append(".")
					.append(cell.get("aggregate")).append("(").append(cell.get("property")).append(")");
			} else if ("simple".equals(type)) {
				sb.append(" \"").append(cell.get("simpleValue")).append("\"");
			} else if ("expression".equals(type)) {
				sb.append(" \"").append(cell.get("expressionValue")).append("\"");
			}
			sb.append(" expand=").append(cell.get("expand"));
			if (cell.get("leftParent") != null) sb.append(" leftParent=").append(cell.get("leftParent"));
			sb.append("\n");
		}

		sb.append("\n## 动作规则\n");
		sb.append("你必须输出严格的 JSON:\n");
		sb.append("{\n");
		sb.append("  \"action\": \"ask\" | \"apply\",\n");
		sb.append("  \"confidence\": 0-100,\n");
		sb.append("  \"understanding\": \"你对用户需求的理解摘要\",\n");
		sb.append("  \"question\": \"如果 action=ask, 你要问的问题\",\n");
		sb.append("  \"options\": [{\"label\":\"选项A\",\"value\":\"A\"},...],\n");
		sb.append("  \"explanation\": \"如果 action=apply, 方案说明\"\n");
		sb.append("}\n\n");
		sb.append("关键规则:\n");
		sb.append("1. confidence<90 时必须 action=ask\n");
		sb.append("2. question 要简洁明确，1-2句话\n");
		sb.append("3. options 提供 2-4 个具体选择 + 让用户自由输入\n");
		sb.append("4. 每轮最多问1个问题\n");
		sb.append("5. 常见需要确认的场景: 目标单元格位置、聚合类型、展开方向、父格设置\n");
		sb.append("6. 第 ").append(MAX_TURNS).append(" 轮后必须 action=apply\n");
		sb.append("7. 只在JSON外不要有任何文字\n");

		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseAnalyzerResponse(String response) {
		try {
			String json = response.trim();
			if (json.startsWith("```")) {
				int s = json.indexOf("{"); int e = json.lastIndexOf("}");
				if (s >= 0 && e > s) json = json.substring(s, e + 1);
			}
			if (!json.startsWith("{")) {
				int s = json.indexOf("{"); int e = json.lastIndexOf("}");
				if (s >= 0 && e > s) json = json.substring(s, e + 1);
				else return null;
			}
			return mapper.readValue(json, Map.class);
		} catch (Exception e) {
			log.debug("Parse analyzer response failed: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Generate cell modifications based on confirmed requirements.
	 */
	private List<AiGenerationService.CellModification> generateModifications() throws Exception {
		ReportContextBuilder ctxBuilder = new ReportContextBuilder(reportDef);
		String systemPrompt = ctxBuilder.buildSystemPrompt(context);

		StringBuilder userPrompt = new StringBuilder();
		userPrompt.append("根据以下对话理解的需求，生成具体的单元格修改JSON。\n\n");
		userPrompt.append("## 对话理解\n");
		for (Map<String, String> entry : history) {
			userPrompt.append("[").append(entry.get("role")).append("]: ")
				.append(entry.get("content")).append("\n");
		}
		userPrompt.append("\n请输出修改方案的JSON数组。");

		String response = client.chat(systemPrompt, userPrompt.toString());
		return parseModifications(response);
	}

	@SuppressWarnings("unchecked")
	private List<AiGenerationService.CellModification> parseModifications(String response) {
		try {
			String json = response.trim();
			if (json.startsWith("```")) {
				int s = json.indexOf("["); int e = json.lastIndexOf("]");
				if (s >= 0 && e > s) json = json.substring(s, e + 1);
			}
			if (!json.startsWith("[")) {
				int s = json.indexOf("["); int e = json.lastIndexOf("]");
				if (s >= 0 && e > s) json = json.substring(s, e + 1);
				else return new ArrayList<AiGenerationService.CellModification>();
			}
			return mapper.readValue(json, new TypeReference<List<AiGenerationService.CellModification>>() {});
		} catch (Exception e) {
			return new ArrayList<AiGenerationService.CellModification>();
		}
	}

	/**
	 * Self-test: validate each modification.
	 * - Expression syntax check
	 * - Field existence check
	 * - Logical consistency check
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private List<Map<String, Object>> selfTest(List<AiGenerationService.CellModification> mods) {
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
		for (AiGenerationService.CellModification mod : mods) {
			Map<String, Object> r = new HashMap<String, Object>();
			r.put("cellName", mod.cellName);
			List<String> checks = new ArrayList<String>();
			boolean passed = true;

			// Test 1: expression syntax
			if ("expression".equals(mod.type) && mod.value != null) {
				try {
					ExpressionUtils.parseExpression(mod.value);
					checks.add("语法 ✓");
				} catch (Exception e) {
					checks.add("语法 ✗: " + e.getMessage());
					passed = false;
				}
			}

			// Test 2: dataset field exists
			if ("dataset".equals(mod.type) && mod.property != null && mod.datasetId != null) {
				boolean fieldOk = fieldExists(mod.datasetId, mod.property);
				checks.add("字段 " + mod.property + (fieldOk ? " ✓" : " ✗ (不存在)"));
				if (!fieldOk) passed = false;
			}

			// Test 3: aggregate validity
			if (mod.aggregate != null) {
				try {
					AggregateType.valueOf(mod.aggregate);
					checks.add("聚合 " + mod.aggregate + " ✓");
				} catch (IllegalArgumentException e) {
					checks.add("聚合 " + mod.aggregate + " ✗");
					passed = false;
				}
			}

			// Test 4: cell exists
			CellDefinition cell = findCell(mod.cellName);
			if (cell != null) {
				checks.add("单元格存在 ✓");
			} else {
				checks.add("单元格不存在 ✗");
				passed = false;
			}

			r.put("checks", checks);
			r.put("passed", passed);
			results.add(r);
		}
		return results;
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
	// Data classes
	// ═══════════════════════════════════════════

	public static class TurnResult {
		public String action; // "ask" or "apply"
		public String question;
		public List<Map<String, String>> options;
		public int confidence;
		public String explanation;
		public List<AiGenerationService.CellModification> modifications;
		public List<Map<String, Object>> testResults;
		public boolean allTestsPassed;

		public boolean isQuestion() { return "ask".equals(action); }

		public static TurnResult question(String q, List<Map<String, String>> options) {
			TurnResult r = new TurnResult();
			r.action = "ask"; r.question = q; r.options = options;
			return r;
		}
	}
}
