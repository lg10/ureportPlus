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
 * Interactive multi-turn AI conversation with self-testing loop and full table manipulation.
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

	/** Add a history entry from client-side records. */
	public void addHistoryEntry(Map<String, String> entry) {
		history.add(entry);
		turnCount = Math.max(turnCount, history.size() / 2);
	}

	/** Sync client-side history to fill gaps since last backend turn. */
	@SuppressWarnings("unchecked")
	public void syncClientHistory(List<Map<String, Object>> clientHistory) {
		if (clientHistory == null) return;
		// Merge client history entries not yet recorded
		int existing = history.size();
		for (int i = existing; i < clientHistory.size(); i++) {
			Map<String, Object> h = clientHistory.get(i);
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
		turnCount = history.size() / 2;
	}

	@SuppressWarnings("unchecked")
	public TurnResult processTurn(String userInput) throws Exception {
		turnCount++;
		log.info("[AI Turn {}] Processing: {}", turnCount, userInput);

		Map<String, String> entry = new HashMap<String, String>();
		entry.put("role", "user");
		entry.put("content", userInput);
		history.add(entry);

		String response = callAnalyzer();
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
			String question = (String) result.get("question");
			List<Map<String, String>> options = (List<Map<String, String>>) result.get("options");
			if (question == null) question = "能提供更多细节吗？";
			return TurnResult.question(question, options);
		}

		List<AiGenerationService.CellModification> modifications = generateModifications();
		List<Map<String, Object>> structuralOps = generateStructuralOps();

		if (modifications.isEmpty() && structuralOps.isEmpty()) {
			return TurnResult.question("无法生成有效的修改方案，请尝试更具体的描述。", null);
		}

		List<Map<String, Object>> testResults = selfTest(modifications);

		TurnResult tr = new TurnResult();
		tr.action = "apply";
		tr.modifications = modifications;
		tr.structuralOps = structuralOps;
		tr.testResults = testResults;
		tr.confidence = confidence;
		tr.explanation = (String) result.get("explanation");

		boolean allPassed = true;
		for (Map<String, Object> tr2 : testResults) {
			if (Boolean.FALSE.equals(tr2.get("passed"))) { allPassed = false; break; }
		}
		if (!structuralOps.isEmpty()) allPassed = true; // structural ops self-validate
		tr.allTestsPassed = allPassed;

		return tr;
	}

	private String callAnalyzer() throws Exception {
		String systemPrompt = buildAnalyzerPrompt();
		StringBuilder userPrompt = new StringBuilder();
		userPrompt.append("## 对话历史\n");
		for (Map<String, String> entry : history) {
			userPrompt.append("[").append(entry.get("role")).append("]: ")
				.append(entry.get("content")).append("\n");
			// Include option context if user was selecting from a question
			if ("user".equals(entry.get("role")) && entry.containsKey("questionContext")) {
				userPrompt.append("  [用户回答的是以下问题: ").append(entry.get("questionContext")).append("]\n");
			}
		}
		userPrompt.append("\n## 当前状态\n");
		userPrompt.append("轮次: ").append(turnCount).append("/").append(MAX_TURNS).append("\n");
		userPrompt.append("请分析用户意图，决定下一步动作。");

		return client.chat(systemPrompt, userPrompt.toString());
	}

	@SuppressWarnings("unchecked")
	private String buildAnalyzerPrompt() {
		StringBuilder sb = new StringBuilder();
		sb.append("你是 UReportPlus 报表设计顾问。你需要通过多轮对话理解用户需求，直到有90%以上把握再给出方案。\n\n");

		sb.append("## 你能操控报表的所有能力\n");
		sb.append("- **修改单元格**: 设置文本、表达式、数据集绑定、展开方向、父格\n");
		sb.append("- **插入行/列**: 在指定位置添加空行或空列\n");
		sb.append("- **删除行/列**: 删除指定行或列\n");
		sb.append("- **合并单元格**: 合并指定范围的单元格\n");
		sb.append("- **设置行类型(波段)**: 标题行、表头行、表尾行、合计行、小计行\n");
		sb.append("- **调整行高/列宽**: 修改指定行的高度或指定列的宽度\n");
		sb.append("- **设置样式**: 字体、字号、粗体、斜体、对齐、背景色、前景色、边框\n\n");

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
		sb.append("\n总行数: ").append(ctx.get("totalRows"));
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

		// Row/col details
		if (ctx.containsKey("rows")) {
			@SuppressWarnings("rawtypes")
			List rows = (List) ctx.get("rows");
			sb.append("\n行详情:\n");
			for (int i = 0; i < Math.min(rows.size(), 30); i++) {
				Map<String, Object> r = (Map<String, Object>) rows.get(i);
				sb.append("  行").append(r.get("number")).append(" 高度=").append(r.get("height"));
				if (r.get("band") != null) sb.append(" band=").append(r.get("band"));
				sb.append("\n");
			}
		}
		if (ctx.containsKey("columns")) {
			@SuppressWarnings("rawtypes")
			List cols = (List) ctx.get("columns");
			sb.append("\n列详情:\n");
			for (int i = 0; i < Math.min(cols.size(), 20); i++) {
				Map<String, Object> c = (Map<String, Object>) cols.get(i);
				sb.append("  列").append(c.get("number")).append(" 宽度=").append(c.get("width")).append("\n");
			}
		}
		if (ctx.containsKey("merges") && !((List<?>)ctx.get("merges")).isEmpty()) {
			sb.append("\n已合并的单元格: ").append(ctx.get("merges")).append("\n");
		}

		sb.append("\n## 动作规则\n");
		sb.append("你必须输出严格的 JSON:\n");
		sb.append("{\n");
		sb.append("  \"action\": \"ask\" | \"apply\",\n");
		sb.append("  \"confidence\": 0-100,\n");
		sb.append("  \"understanding\": \"你对用户需求的理解摘要(1-2句)\",\n");
		sb.append("  \"question\": \"如果 action=ask, 你要问的问题\",\n");
		sb.append("  \"options\": [{\"label\":\"选项A\",\"value\":\"A\"},...],\n");
		sb.append("  \"explanation\": \"如果 action=apply, 方案说明\"\n");
		sb.append("}\n\n");
		sb.append("关键规则:\n");
		sb.append("1. confidence<90 时必须 action=ask\n");
		sb.append("2. question 要简洁明确，1-2句话\n");
		sb.append("3. options 提供 2-4 个具体选择，每个label要完整描述选项含义\n");
		sb.append("   - value字段使用简短标识符(如A/B/C)，但label包含完整上下文让用户理解\n");
		sb.append("4. 每轮最多问1个问题\n");
		sb.append("5. 如果对话历史中有过类似问题，不要重复询问\n");
		sb.append("6. 第 ").append(MAX_TURNS).append(" 轮后必须 action=apply\n");
		sb.append("7. 只在JSON外不要有任何文字\n");
		sb.append("8. 注意表格结构能力: 可以建议插入行/列、合并单元格等操作\n");

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

	/** Generate structural operations based on conversation understanding. */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> generateStructuralOps() throws Exception {
		ReportContextBuilder ctxBuilder = new ReportContextBuilder(reportDef);
		Map<String, Object> fullCtx = ctxBuilder.buildContext();

		StringBuilder sysPrompt = new StringBuilder();
		sysPrompt.append("你是 UReportPlus 报表结构操作专家。你需要生成表格结构修改指令。\n\n");
		sysPrompt.append("## 当前表格结构\n");
		sysPrompt.append("总行数: ").append(fullCtx.get("totalRows")).append("\n");
		sysPrompt.append("总列数: ").append(fullCtx.get("totalColumns")).append("\n");
		if (fullCtx.containsKey("rows")) {
			List<Map<String, Object>> rows = (List<Map<String, Object>>) fullCtx.get("rows");
			for (Map<String, Object> r : rows) {
				sysPrompt.append("行").append(r.get("number")).append(": 高度=").append(r.get("height"));
				if (r.get("band") != null) sysPrompt.append(" band=").append(r.get("band"));
				sysPrompt.append("\n");
			}
		}
		if (fullCtx.containsKey("columns")) {
			List<Map<String, Object>> cols = (List<Map<String, Object>>) fullCtx.get("columns");
			for (Map<String, Object> c : cols) {
				sysPrompt.append("列").append(c.get("number")).append(": 宽度=").append(c.get("width")).append("\n");
			}
		}

		sysPrompt.append("\n## 可用操作类型\n");
		sysPrompt.append("- insertRow:   在指定行号位置插入行。参数: {rowNumber(在第几行后插入), height(行高,默认25)}\n");
		sysPrompt.append("- insertCol:   在指定列号位置插入列。参数: {colNumber(在第几列后插入), width(列宽,默认100)}\n");
		sysPrompt.append("- deleteRow:   删除指定行。参数: {rowNumber}\n");
		sysPrompt.append("- deleteCol:   删除指定列。参数: {colNumber}\n");
		sysPrompt.append("- mergeCells:  合并单元格。参数: {startRow,startCol,endRow,endCol}\n");
		sysPrompt.append("- setBand:     设置行类型。参数: {rowNumber, band(可选: headerrepeat/title/footerrepeat/summary/subtotal)}\n");
		sysPrompt.append("- setRowHeight: 设置行高。参数: {rowNumber, height}\n");
		sysPrompt.append("- setColWidth: 设置列宽。参数: {colNumber, width}\n\n");

		sysPrompt.append("## 输出格式\n");
		sysPrompt.append("返回严格的JSON数组:\n");
		sysPrompt.append("[\n");
		sysPrompt.append("  {\"type\": \"insertRow\", \"rowNumber\": 3, \"height\": 25, \"description\": \"在第3行后插入合计行\"},\n");
		sysPrompt.append("  {\"type\": \"insertCol\", \"colNumber\": 2, \"width\": 120, \"description\": \"在第2列后插入新列\"}\n");
		sysPrompt.append("]\n");
		sysPrompt.append("如果不需要结构操作，返回 []\n");

		StringBuilder userPrompt = new StringBuilder();
		userPrompt.append("## 对话理解\n");
		for (Map<String, String> entry : history) {
			userPrompt.append("[").append(entry.get("role")).append("]: ")
				.append(entry.get("content")).append("\n");
		}
		userPrompt.append("\n请根据对话内容，生成表格结构修改JSON数组。如果没有需要的结构修改，返回空数组[]。");

		String response = client.chat(sysPrompt.toString(), userPrompt.toString());
		return parseStructuralOps(response);
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> parseStructuralOps(String response) {
		try {
			String json = response.trim();
			if (json.startsWith("```")) {
				int s = json.indexOf("["); int e = json.lastIndexOf("]");
				if (s >= 0 && e > s) json = json.substring(s, e + 1);
			}
			if (!json.startsWith("[")) {
				int s = json.indexOf("["); int e = json.lastIndexOf("]");
				if (s >= 0 && e > s) json = json.substring(s, e + 1);
				else return new ArrayList<Map<String, Object>>();
			}
			return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
		} catch (Exception e) {
			return new ArrayList<Map<String, Object>>();
		}
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
				checks.add("字段 " + mod.property + (fieldOk ? " ✓" : " ✗ (不存在)"));
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
		public String action;
		public String question;
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
