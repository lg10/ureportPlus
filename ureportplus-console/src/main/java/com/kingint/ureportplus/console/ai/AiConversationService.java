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
import com.kingint.ureportplus.expression.ExpressionUtils;

/**
 * Interactive multi-turn AI conversation with multi-agent generation loop.
 *
 * Flow:
 *   1. Frontend sends prompt + full history
 *   2. Analyzer determines intent/confidence
 *   3. If confidence < 90%: ask clarifying question
 *   4. If confidence >= 90%: run multi-agent Generator→Validator loop
 *   5. After MAX_TURNS: force apply (skip analyzer to save tokens)
 */
public class AiConversationService {
	private static final Logger log = LoggerFactory.getLogger(AiConversationService.class);
	private static final ObjectMapper mapper = new ObjectMapper();
	static {
		mapper.configure(org.codehaus.jackson.JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
	}

	private final OpenAiClient client;
	private final ReportDefinition reportDef;
	private final Map<String, Object> context;
	private final List<Map<String, String>> history = new ArrayList<Map<String, String>>();
	private List<String> currentSelectedCells;
	private final List<Map<String, String>> stages = new ArrayList<Map<String, String>>();
	private java.io.PrintWriter streamOut;
	private int turnCount = 0;

	public void setStreamOutput(java.io.PrintWriter out) { this.streamOut = out; }
	private static final int MAX_TURNS = 20;

	public AiConversationService(ReportDefinition reportDef) {
		this.reportDef = reportDef;
		this.client = new OpenAiClient();
		ReportContextBuilder ctxBuilder = new ReportContextBuilder(reportDef);
		this.context = ctxBuilder.buildContext();
	}

	/** Replace backend history with client-side records (single source of truth). */
	@SuppressWarnings("unchecked")
	public void syncClientHistory(List<Map<String, Object>> clientHistory) {
		history.clear();
		if (clientHistory == null) return;
		for (Map<String, Object> h : clientHistory) {
			String role = (String) h.get("role");
			String content = (String) h.get("content");
			if (role != null && content != null) {
				Map<String, String> entry = new HashMap<String, String>();
				entry.put("role", role);
				entry.put("content", content);
				if (h.containsKey("questionContext")) {
					try { entry.put("questionContext", mapper.writeValueAsString(h.get("questionContext"))); } catch (Exception ignored) {}
				}
				if (h.containsKey("selectedOption")) {
					entry.put("selectedOption", (String) h.get("selectedOption"));
				}
				history.add(entry);
			}
		}
	}

	private void addStage(String icon, String label) { addStage(icon, label, null); }
	private void addStage(String icon, String label, String detail) {
		Map<String, String> s = new HashMap<String, String>();
		s.put("icon", icon); s.put("label", label);
		s.put("detail", detail != null ? detail : "");
		stages.add(s);
		// Stream event if output is connected
		if (streamOut != null) {
			try {
				Map<String, Object> evt = new HashMap<String, Object>();
				evt.put("type", "stage");
				evt.put("icon", icon);
				evt.put("label", label);
				if (detail != null) evt.put("detail", detail);
				streamOut.write(mapper.writeValueAsString(evt) + "\n");
				streamOut.flush();
			} catch (Exception ignored) {}
		}
	}

	@SuppressWarnings("unchecked")
	public TurnResult processTurn(String userInput, List<String> selectedCells) throws Exception {
		this.currentSelectedCells = selectedCells;
		stages.clear();
		turnCount = (history.size() / 2) + 1;
		log.info("[AI Turn {}] Processing: {}", turnCount, userInput);

		Map<String, String> entry = new HashMap<String, String>();
		entry.put("role", "user");
		entry.put("content", userInput);
		if (history.isEmpty() || !userInput.equals(getLastUserContent())) {
			history.add(entry);
		}

		// After MAX_TURNS, force apply (skip analyzer)
		if (turnCount >= MAX_TURNS) {
			log.info("[AI Turn {}] MAX_TURNS reached, forcing apply", turnCount);
			Map<String, String> aiEntry = new HashMap<String, String>();
			aiEntry.put("role", "ai");
			aiEntry.put("content", "达到最大对话轮次，自动生成方案。");
			history.add(aiEntry);
			return forceApply();
		}

		addStage("🧠", "AI 分析需求中…");
		String response = callAnalyzer();
		Map<String, Object> result = parseAnalyzerResponse(response);
		if (result == null) {
			return TurnResult.question("我没有理解你的需求，能换个方式描述吗？", null);
		}
		addStage("✅", "分析完成",
			(String) (result.get("understanding") != null ? result.get("understanding") : "已理解需求"));

		Map<String, String> aiEntry = new HashMap<String, String>();
		aiEntry.put("role", "ai");
		aiEntry.put("content", response);
		history.add(aiEntry);

		int confidence = result.get("confidence") instanceof Number
			? ((Number) result.get("confidence")).intValue() : 0;
		String action = (String) result.get("action");

		if ("ask".equals(action) || confidence < 90) {
			String question = (String) result.get("question");
			List<Map<String, String>> options = (List<Map<String, String>>) result.get("options");
			if (question == null) question = "能提供更多细节吗？";
			TurnResult tr = TurnResult.question(question, options);
			tr.understanding = (String) result.get("understanding");
			tr.stages = new ArrayList<Map<String, String>>(stages);
			return tr;
		}

		TurnResult tr = forceApply();
		tr.understanding = (String) result.get("understanding");
		tr.stages = new ArrayList<Map<String, String>>(stages);
		return tr;
	}

	private TurnResult forceApply() throws Exception {
		String userPrompt = buildUserPromptFromHistory();
		addStage("🔍", "生成方案中…");
		String genSystemPrompt = new ReportContextBuilder(reportDef).buildSystemPrompt(context);
		String genResponse = client.chat(genSystemPrompt, userPrompt);
		AiGenerationService genService = new AiGenerationService(reportDef);
		List<AiGenerationService.CellModification> modifications = genService.parseModifications(genResponse);
		List<Map<String, Object>> structuralOps = genService.inferStructuralOps(modifications);

		addStage("✅", "方案生成完成",
			"生成 " + modifications.size() + " 项修改, " + structuralOps.size() + " 项结构操作");
		addStage("🔧", "自测中…");

		if (modifications.isEmpty() && structuralOps.isEmpty()) {
			return TurnResult.question("无法生成有效的修改方案，请尝试更具体的描述。", null);
		}

		List<Map<String, Object>> testResults = selfTest(modifications);

		TurnResult tr = new TurnResult();
		tr.action = "apply";
		tr.modifications = modifications;
		tr.structuralOps = structuralOps;
		tr.testResults = testResults;
		tr.explanation = "已根据对话理解生成修改方案。";
		tr.stages = new ArrayList<Map<String, String>>(stages);
		tr.allTestsPassed = true;

		for (Map<String, Object> tr2 : testResults) {
			if (Boolean.FALSE.equals(tr2.get("passed"))) { tr.allTestsPassed = false; break; }
		}
		return tr;
	}

	// ═══════════════════ Analyzer ═══════════════════

	private String callAnalyzer() throws Exception {
		String systemPrompt = buildAnalyzerPrompt();
		StringBuilder userPrompt = new StringBuilder();
		userPrompt.append("## 对话历史\n");
		for (Map<String, String> entry : history) {
			userPrompt.append("[").append(entry.get("role")).append("]: ")
				.append(entry.get("content")).append("\n");
			if ("user".equals(entry.get("role")) && entry.containsKey("questionContext")) {
				userPrompt.append("  [用户回答的是以下问题: ").append(entry.get("questionContext")).append("]\n");
			}
		}
		userPrompt.append("\n## 当前状态\n");
		userPrompt.append("轮次: ").append(turnCount).append("/").append(MAX_TURNS).append("\n");
		if (currentSelectedCells != null && !currentSelectedCells.isEmpty()) {
			userPrompt.append("用户当前选中的单元格: ").append(String.join(", ", currentSelectedCells)).append("\n");
		}
		userPrompt.append("请分析用户意图，决定下一步动作。");

		return client.chat(systemPrompt, userPrompt.toString());
	}

	@SuppressWarnings("unchecked")
	private String buildAnalyzerPrompt() {
		StringBuilder sb = new StringBuilder();
		sb.append("你是 UReportPlus 报表设计顾问。通过多轮对话理解需求，90%把握以上给出方案。\n\n");

		sb.append("## 你能操控报表的所有能力\n");
		sb.append("- 修改单元格: 设置文本、表达式、数据集绑定、展开方向、父格\n");
		sb.append("- 插入/删除行/列\n");
		sb.append("- 合并单元格、设置行类型(波段)、调整行高/列宽\n\n");

		sb.append(ExamplePatterns.getPatternReference());
		sb.append("\n");

		sb.append("## 报表上下文\n");
		ReportContextBuilder ctxBuilder = new ReportContextBuilder(reportDef);
		Map<String, Object> ctx = ctxBuilder.buildContext();

		List<Map<String, Object>> datasets = (List<Map<String, Object>>) ctx.get("datasets");
		sb.append("可用数据集:\n");
		for (Map<String, Object> ds : datasets) {
			sb.append("- ").append(ds.get("name")).append(": ").append(ds.get("fields")).append("\n");
		}
		sb.append("总行数: ").append(ctx.get("totalRows"));
		sb.append(", 总列数: ").append(ctx.get("totalColumns")).append("\n");

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

		sb.append("\n## 动作规则 (严格JSON输出)\n");
		sb.append("{\"action\":\"ask\"|\"apply\",\"confidence\":0-100,\"understanding\":\"...\",\n");
		sb.append(" \"question\":\"...\",\"options\":[{\"label\":\"...\",\"value\":\"A\"}],\"explanation\":\"...\"}\n");
		sb.append("confidence<90→ask; 每轮最多1问; options的label要充分描述让用户理解选择含义\n");

		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseAnalyzerResponse(String response) {
		try {
			String json = extractJson(response);
			if (json == null) return null;
			return mapper.readValue(json, Map.class);
		} catch (Exception e) {
			log.debug("Parse analyzer response failed: {}", e.getMessage());
			return null;
		}
	}

	/** Extract the last valid JSON object from a response that may contain reasoning text. */
	private String extractJson(String text) {
		text = text.trim();
		// Remove markdown code fences
		if (text.startsWith("```")) {
			int end = text.lastIndexOf("```");
			if (end > 3) text = text.substring(3, end).trim();
		}
		// Find the outermost JSON object: find the LAST { that has a matching }
		// This handles reasoning models that output text before the JSON
		int lastClose = text.lastIndexOf("}");
		if (lastClose < 0) return null;
		int depth = 0;
		int open = -1;
		for (int i = lastClose; i >= 0; i--) {
			char c = text.charAt(i);
			if (c == '}') depth++;
			else if (c == '{') {
				depth--;
				if (depth == 0) { open = i; break; }
			}
		}
		if (open >= 0 && lastClose > open) {
			return text.substring(open, lastClose + 1);
		}
		return null;
	}

	// ═══════════════════ Helpers ═══════════════════

	private String buildUserPromptFromHistory() {
		StringBuilder sb = new StringBuilder();
		sb.append("根据以下对话理解的需求，生成具体的单元格修改。\n\n");
		sb.append("## 对话理解\n");
		for (Map<String, String> entry : history) {
			sb.append("[").append(entry.get("role")).append("]: ")
				.append(entry.get("content")).append("\n");
		}
		sb.append("\n请输出修改方案的JSON数组。");
		return sb.toString();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private List<Map<String, Object>> selfTest(List<AiGenerationService.CellModification> mods) {
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
		for (AiGenerationService.CellModification mod : mods) {
			Map<String, Object> r = new HashMap<String, Object>();
			r.put("cellName", mod.cellName);
			List<String> checks = new ArrayList<String>();
			boolean passed = true;

			if ("expression".equals(mod.type) && mod.value != null) {
				try {
					ExpressionUtils.parseExpression(mod.value);
					checks.add("语法 ✓");
				} catch (Exception e) {
					checks.add("语法 ✗: " + e.getMessage());
					passed = false;
				}
			}
			if ("dataset".equals(mod.type) && mod.property != null && mod.datasetId != null) {
				boolean fieldOk = fieldExists(mod.datasetId, mod.property);
				checks.add("字段 " + mod.property + (fieldOk ? " ✓" : " ✗"));
				if (!fieldOk) passed = false;
			}
			if (mod.aggregate != null) {
				try {
					AggregateType.valueOf(mod.aggregate);
					checks.add("聚合 " + mod.aggregate + " ✓");
				} catch (IllegalArgumentException e) {
					checks.add("聚合 " + mod.aggregate + " ✗");
					passed = false;
				}
			}
			CellDefinition cell = findCell(mod.cellName);
			checks.add("单元格 " + (cell != null ? "存在 ✓" : "不存在 ✗"));
			if (cell == null) passed = false;

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

	private String getLastUserContent() {
		for (int i = history.size() - 1; i >= 0; i--) {
			if ("user".equals(history.get(i).get("role"))) {
				return history.get(i).get("content");
			}
		}
		return null;
	}

	private CellDefinition findCell(String cellName) {
		if (reportDef.getCells() != null) {
			for (CellDefinition cell : reportDef.getCells()) {
				if (cellName.equals(cell.getName())) return cell;
			}
		}
		return null;
	}

	// ═══════════════════ Data Classes ═══════════════════

	public static class TurnResult {
		public String action;
		public String question;
		public String understanding;
		public List<Map<String, String>> stages;
		public List<Map<String, String>> options;
		public int confidence;
		public String explanation;
		public List<AiGenerationService.CellModification> modifications;
		public List<Map<String, Object>> structuralOps;
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
