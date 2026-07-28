package com.kingint.ureportplus.console.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.ObjectMapper;

import com.kingint.ureportplus.cache.CacheUtils;
import com.kingint.ureportplus.console.RenderPageServletAction;
import com.kingint.ureportplus.definition.ReportDefinition;
import com.kingint.ureportplus.export.ReportRender;

/**
 * AI-assisted report generation endpoints.
 *
 * Endpoints:
 *   GET  /ai/status    - Check if AI is enabled and configured
 *   GET  /ai/context   - Get report context (datasets, schema, layout)
 *   POST /ai/generate  - Generate cell modifications from natural language
 *   POST /ai/apply     - Apply confirmed modifications to report
 */
public class AiServletAction extends RenderPageServletAction {
	private static final ObjectMapper mapper = new ObjectMapper();

	@Override
	public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String method = retriveMethod(req);
		if (method != null) {
			invokeMethod(method, req, resp);
		} else {
			status(req, resp);
		}
	}

	@Override
	public String url() {
		return "/ai";
	}

	/**
	 * GET /ai/status
	 * Returns AI configuration status.
	 */
	public void status(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		try {
			AiConfig config = AiConfig.getInstance();
			Map<String, Object> result = new HashMap<String, Object>();
			result.put("enabled", config.isEnabled());
			result.put("provider", config.getProvider());
			result.put("model", config.getModel());
			result.put("maxRetries", config.getMaxRetries());
			writeObjectToJson(resp, result);
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}

	/**
	 * GET /ai/context?file=xxx
	 * Returns full report context for AI consumption.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void context(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		try {
			String file = req.getParameter("_u");
			if (file == null) {
				file = req.getParameter("file");
			}

			ReportDefinition reportDef = loadReport(file);
			if (reportDef == null) {
				Map<String, Object> error = new HashMap<String, Object>();
				error.put("error", "Report not found: " + file);
				writeObjectToJson(resp, error);
				return;
			}

			ReportContextBuilder builder = new ReportContextBuilder(reportDef);
			Map<String, Object> context = builder.buildContext();

			// Include selected cells if provided
			String selectedParam = req.getParameter("selected");
			if (selectedParam != null && !selectedParam.isEmpty()) {
				List<String> selected = new ArrayList<String>();
				for (String s : selectedParam.split(",")) {
					selected.add(s.trim());
				}
				context.put("selectedCells", selected);
			}

			writeObjectToJson(resp, context);
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}

	/**
	 * POST /ai/generate
	 * Body: { file, prompt, selectedCells }
	 * Returns: AI-generated modifications with validation warnings.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void generate(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			AiConfig config = AiConfig.getInstance();
			if (!config.isEnabled()) {
				Map<String, Object> error = new HashMap<String, Object>();
				error.put("error", "AI功能未启用。请在config.properties中设置 ureportplus.ai.enabled=true");
				writeObjectToJson(resp, error);
				return;
			}

			// Read request body
			String body = readBody(req);
			Map<String, Object> params = mapper.readValue(body, Map.class);

			String file = (String) params.get("file");
			String prompt = (String) params.get("prompt");
			List<String> selectedCells = (List<String>) params.get("selectedCells");

			if (file == null || prompt == null) {
				Map<String, Object> error = new HashMap<String, Object>();
				error.put("error", "缺少必要参数: file 和 prompt");
				writeObjectToJson(resp, error);
				return;
			}

			ReportDefinition reportDef = loadReport(file);
			if (reportDef == null) {
				Map<String, Object> error = new HashMap<String, Object>();
				error.put("error", "Report not found: " + file);
				writeObjectToJson(resp, error);
				return;
			}

			AiGenerationService service = new AiGenerationService(reportDef);
			AiGenerationService.AiResult result = service.generate(prompt, selectedCells);

			Map<String, Object> response = new HashMap<String, Object>();
			response.put("success", result.success);
			response.put("modifications", result.modifications);
			response.put("warnings", result.warnings);
			response.put("rawResponse", result.rawResponse);

			writeObjectToJson(resp, response);
		} catch (Exception e) {
			Map<String, Object> error = new HashMap<String, Object>();
			error.put("error", "AI generation failed: " + e.getMessage());
			writeObjectToJson(resp, error);
		}
	}

	/**
	 * POST /ai/apply
	 * Body: { file, modifications }
	 * Applies confirmed modifications and returns updated report XML.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void apply(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			String body = readBody(req);
			Map<String, Object> params = mapper.readValue(body, Map.class);

			String file = (String) params.get("file");
			List<Map<String, Object>> modsData = (List<Map<String, Object>>) params.get("modifications");

			if (file == null || modsData == null) {
				Map<String, Object> error = new HashMap<String, Object>();
				error.put("error", "缺少必要参数: file 和 modifications");
				writeObjectToJson(resp, error);
				return;
			}

			ReportDefinition reportDef = loadReport(file);
			if (reportDef == null) {
				Map<String, Object> error = new HashMap<String, Object>();
				error.put("error", "Report not found: " + file);
				writeObjectToJson(resp, error);
				return;
			}

			// Parse modifications
			List<AiGenerationService.CellModification> modifications =
					new ArrayList<AiGenerationService.CellModification>();
			for (Map<String, Object> mod : modsData) {
				AiGenerationService.CellModification cm = new AiGenerationService.CellModification();
				cm.cellName = (String) mod.get("cellName");
				cm.type = (String) mod.get("type");
				cm.value = (String) mod.get("value");
				cm.datasetId = (String) mod.get("datasetId");
				cm.aggregate = (String) mod.get("aggregate");
				cm.property = (String) mod.get("property");
				cm.expand = (String) mod.get("expand");
				cm.leftParent = (String) mod.get("leftParent");
				modifications.add(cm);
			}

			AiGenerationService service = new AiGenerationService(reportDef);
			List<Map<String, Object>> appliedCells = service.applyModifications(modifications);

			// Save report
			saveReport(file, reportDef);

			Map<String, Object> response = new HashMap<String, Object>();
			response.put("success", true);
			response.put("appliedCells", appliedCells);

			writeObjectToJson(resp, response);
		} catch (Exception e) {
			Map<String, Object> error = new HashMap<String, Object>();
			error.put("error", "Apply failed: " + e.getMessage());
			writeObjectToJson(resp, error);
		}
	}

	/**
	 * POST /ai/start
	 * Start a new interactive conversation. Body: { file, prompt }
	 * Returns: { action: "ask"|"apply", question, options, confidence, explanation, testResults, modifications }
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void start(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			AiConfig config = AiConfig.getInstance();
			if (!config.isEnabled()) {
				Map<String, Object> error = new HashMap<String, Object>();
				error.put("error", "AI功能未启用");
				writeObjectToJson(resp, error);
				return;
			}

			String body = readBody(req);
			Map<String, Object> params = mapper.readValue(body, Map.class);
			String file = (String) params.get("file");
			String prompt = (String) params.get("prompt");

			if (file == null || prompt == null) {
				Map<String, Object> error = new HashMap<String, Object>();
				error.put("error", "缺少 file 或 prompt");
				writeObjectToJson(resp, error);
				return;
			}

			ReportDefinition reportDef = loadReport(file);
			if (reportDef == null) {
				Map<String, Object> error = new HashMap<String, Object>();
				error.put("error", "Report not found: " + file);
				writeObjectToJson(resp, error);
				return;
			}

			AiConversationService conv = new AiConversationService(reportDef);
			AiConversationService.TurnResult result = conv.processTurn(prompt);

			Map<String, Object> response = new HashMap<String, Object>();
			response.put("action", result.action);
			response.put("confidence", result.confidence);

			if (result.isQuestion()) {
				response.put("question", result.question);
				response.put("options", result.options);
			} else {
				response.put("explanation", result.explanation);
				response.put("modifications", result.modifications);
				response.put("testResults", result.testResults);
				response.put("allTestsPassed", result.allTestsPassed);
			}

			writeObjectToJson(resp, response);
		} catch (Exception e) {
			Map<String, Object> error = new HashMap<String, Object>();
			error.put("error", "Conversation error: " + e.getMessage());
			writeObjectToJson(resp, error);
		}
	}

	/**
	 * POST /ai/next
	 * Continue conversation with user's answer. Body: { file, answer }
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void next(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			String body = readBody(req);
			Map<String, Object> params = mapper.readValue(body, Map.class);
			String file = (String) params.get("file");
			String answer = (String) params.get("answer");

			ReportDefinition reportDef = loadReport(file);
			AiConversationService conv = new AiConversationService(reportDef);
			AiConversationService.TurnResult result = conv.processTurn(answer);

			Map<String, Object> response = new HashMap<String, Object>();
			response.put("action", result.action);
			response.put("confidence", result.confidence);

			if (result.isQuestion()) {
				response.put("question", result.question);
				response.put("options", result.options);
			} else {
				response.put("explanation", result.explanation);
				response.put("modifications", result.modifications);
				response.put("testResults", result.testResults);
				response.put("allTestsPassed", result.allTestsPassed);
			}

			writeObjectToJson(resp, response);
		} catch (Exception e) {
			Map<String, Object> error = new HashMap<String, Object>();
			error.put("error", "Continue error: " + e.getMessage());
			writeObjectToJson(resp, error);
		}
	}

	private ReportDefinition loadReport(String file) {
		try {
			ReportDefinition reportDef = CacheUtils.getReportDefinition(file);
			if (reportDef == null) {
				ReportRender reportRender = applicationContext.getBean(ReportRender.class);
				reportDef = reportRender.parseReport(file);
				reportRender.rebuildReportDefinition(reportDef);
				CacheUtils.cacheReportDefinition(file, reportDef);
			}
			return reportDef;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void saveReport(String file, ReportDefinition reportDef) throws Exception {
		// Rebuild the definition to update parent-child relationships
		ReportRender reportRender = applicationContext.getBean(ReportRender.class);
		reportRender.rebuildReportDefinition(reportDef);

		// Update cache
		CacheUtils.cacheReportDefinition(file, reportDef);

		// Persist using the appropriate ReportProvider
		Collection<com.kingint.ureportplus.provider.report.ReportProvider> providers =
				applicationContext.getBeansOfType(
						com.kingint.ureportplus.provider.report.ReportProvider.class).values();

		for (com.kingint.ureportplus.provider.report.ReportProvider provider : providers) {
			if (file.startsWith(provider.getPrefix())) {
				// Note: XML serialization is done client-side; here we just rebuild
				// The client will call save after applying modifications
				break;
			}
		}
	}

	private String readBody(HttpServletRequest req) throws Exception {
		StringBuilder sb = new StringBuilder();
		BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream(), "UTF-8"));
		String line;
		while ((line = reader.readLine()) != null) {
			sb.append(line);
		}
		return sb.toString();
	}
}
