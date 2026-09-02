package com.kingint.ureportplus.console.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.ObjectMapper;

import com.kingint.ureportplus.cache.CacheUtils;
import com.kingint.ureportplus.console.RenderPageServletAction;
import com.kingint.ureportplus.definition.ReportDefinition;
import com.kingint.ureportplus.export.ReportRender;

/**
 * AI-assisted report generation endpoints with conversation session support.
 *
 * Endpoints:
 *   GET  /ai/status    - Check if AI is enabled and configured
 *   GET  /ai/context   - Get report context (datasets, schema, layout)
 *   POST /ai/generate  - Generate cell/structural modifications from NL (single-shot)
 *   POST /ai/start     - Start interactive multi-turn conversation
 *   POST /ai/next      - Continue conversation with answer
 *   POST /ai/apply     - Apply confirmed modifications to report
 */
public class AiServletAction extends RenderPageServletAction {
	private static final ObjectMapper mapper = new ObjectMapper();
	static { mapper.configure(org.codehaus.jackson.JsonGenerator.Feature.ESCAPE_NON_ASCII, false); }

	/** Conversation sessions, keyed by UUID. Expired entries cleaned periodically. */
	private static final ConcurrentHashMap<String, ConversationSession> sessions = new ConcurrentHashMap<String, ConversationSession>();
	private static final long SESSION_TTL = 30 * 60 * 1000; // 30 minutes
	private static long lastCleanup = System.currentTimeMillis();

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
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void context(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		try {
			String file = req.getParameter("_u");
			if (file == null) file = req.getParameter("file");
			ReportDefinition reportDef = loadReport(file);
			if (reportDef == null) {
				Map<String, Object> error = new HashMap<String, Object>();
				error.put("error", "Report not found: " + file);
				writeObjectToJson(resp, error);
				return;
			}
			ReportContextBuilder builder = new ReportContextBuilder(reportDef);
			Map<String, Object> context = builder.buildContext();
			String selectedParam = req.getParameter("selected");
			if (selectedParam != null && !selectedParam.isEmpty()) {
				List<String> selected = new ArrayList<String>();
				for (String s : selectedParam.split(",")) selected.add(s.trim());
				context.put("selectedCells", selected);
			}
			writeObjectToJson(resp, context);
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}

	/**
	 * POST /ai/generate — single-shot multi-agent generation
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
			response.put("structuralOps", result.structuralOps);
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
	 * Body: { file, modifications, structuralOps }
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void start(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		AiConversationService conv = null;
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
				List<String> selectedCells = (List<String>) params.get("selectedCells");
			List<Map<String, Object>> clientHistory = (List<Map<String, Object>>) params.get("history");

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

			cleanupSessions();

			// Setup streaming response
			resp.setContentType("text/plain; charset=UTF-8");
			resp.setHeader("Cache-Control", "no-cache");
			resp.setHeader("X-Content-Type-Options", "nosniff");
			java.io.PrintWriter streamWriter = resp.getWriter();

			conv = new AiConversationService(reportDef);
			conv.setStreamOutput(streamWriter);
			conv.syncClientHistory(clientHistory);

			AiConversationService.TurnResult result = conv.processTurn(prompt, selectedCells);

			String convId = UUID.randomUUID().toString();
			ConversationSession session = new ConversationSession();
			session.service = conv;
			session.createdAt = System.currentTimeMillis();
			session.reportFile = file;
			sessions.put(convId, session);

			Map<String, Object> done = buildTurnResponse(result, convId);
		done.put("type", "done");
			streamWriter.write(mapper.writeValueAsString(done) + "\n");
		streamWriter.flush();
		} catch (Exception e) {
			Map<String, Object> error = new HashMap<String, Object>();
			error.put("type","error"); error.put("error", "Conversation error: " + e.getMessage());
			writeStreamingError(resp, error);
		} finally {
			if (conv != null) {
				conv.setStreamOutput(null);
			}
		}
	}

	/**
	 * POST /ai/next — continue conversation.
	 * Body: { conversationId, file, answer, selectedCells, history }
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void next(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		java.io.PrintWriter streamWriter = null;
		AiConversationService conv = null;
		try {
			String body = readBody(req);
			Map<String, Object> params = mapper.readValue(body, Map.class);
			String convId = (String) params.get("conversationId");
			String file = (String) params.get("file");
			String answer = (String) params.get("answer");
				List<String> selectedCells = (List<String>) params.get("selectedCells");
			List<Map<String, Object>> clientHistory = (List<Map<String, Object>>) params.get("history");

			// Find existing session or create new one
			if (convId != null) {
				ConversationSession session = sessions.get(convId);
				if (session != null) {
					conv = session.service;
					session.createdAt = System.currentTimeMillis(); // refresh TTL
				}
			}

			if (conv == null) {
				// Fallback: create new conversation but reload report
				ReportDefinition reportDef = loadReport(file);
				if (reportDef == null) {
					Map<String, Object> error = new HashMap<String, Object>();
					error.put("error", "Report not found: " + file);
					writeObjectToJson(resp, error);
					return;
				}
				conv = new AiConversationService(reportDef);
				convId = UUID.randomUUID().toString();
				ConversationSession session = new ConversationSession();
				session.service = conv;
				session.createdAt = System.currentTimeMillis();
				session.reportFile = file;
				sessions.put(convId, session);
			}

			// Setup streaming response (charset required, default ISO-8859-1 corrupts Chinese)
			resp.setContentType("text/plain; charset=UTF-8");
			resp.setHeader("Cache-Control", "no-cache");
			resp.setHeader("X-Content-Type-Options", "nosniff");
			streamWriter = resp.getWriter();
			// Rebind stream output to the current response; the session may still hold
			// a writer from a previous request, which would leak stage events.
			conv.setStreamOutput(streamWriter);

			// Sync client history
			conv.syncClientHistory(clientHistory);

			AiConversationService.TurnResult result = conv.processTurn(answer, selectedCells);
			Map<String, Object> done = buildTurnResponse(result, convId);
		done.put("type", "done");
			streamWriter.write(mapper.writeValueAsString(done) + "\n");
		streamWriter.flush();
		} catch (Exception e) {
			Map<String, Object> error = new HashMap<String, Object>();
			error.put("type","error"); error.put("error", "Continue error: " + e.getMessage());
			if (streamWriter != null) {
				try {
					streamWriter.write(mapper.writeValueAsString(error) + "\n");
					streamWriter.flush();
				} catch (Exception ignored) {}
			} else {
				writeObjectToJson(resp, error);
			}
		} finally {
			if (conv != null) {
				conv.setStreamOutput(null);
			}
		}
	}

	/** Write an NDJSON error line through the streaming writer when it is already in use. */
	private void writeStreamingError(HttpServletResponse resp, Map<String, Object> error) {
		try {
			resp.setContentType("text/plain; charset=UTF-8");
			java.io.PrintWriter writer = resp.getWriter();
			writer.write(mapper.writeValueAsString(error) + "\n");
			writer.flush();
		} catch (Exception ignored) {}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Map<String, Object> buildTurnResponse(AiConversationService.TurnResult result, String convId) {
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("conversationId", convId);
		response.put("action", result.action);
		response.put("confidence", result.confidence);
		response.put("understanding", result.understanding);
		response.put("stages", result.stages);
		if (result.isQuestion()) {
			response.put("question", result.question);
			response.put("options", result.options);
		} else {
			response.put("explanation", result.explanation);
			response.put("modifications", result.modifications);
			response.put("structuralOps", result.structuralOps);
			response.put("testResults", result.testResults);
			response.put("allTestsPassed", result.allTestsPassed);
		}
		return response;
	}

	private void cleanupSessions() {
		long now = System.currentTimeMillis();
		if (now - lastCleanup < 60000) return; // every 1 min max
		lastCleanup = now;
		Iterator<Map.Entry<String, ConversationSession>> it = sessions.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, ConversationSession> e = it.next();
			if (now - e.getValue().createdAt > SESSION_TTL) it.remove();
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
		ReportRender reportRender = applicationContext.getBean(ReportRender.class);
		reportRender.rebuildReportDefinition(reportDef);
		CacheUtils.cacheReportDefinition(file, reportDef);
	}


	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void apply(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			String body = readBody(req);
			Map<String, Object> params = mapper.readValue(body, Map.class);
			String file = (String) params.get("file");
			List<Map<String, Object>> modsData = (List<Map<String, Object>>) params.get("modifications");
			List<Map<String, Object>> opsData = (List<Map<String, Object>>) params.get("structuralOps");

			if (file == null) {
				Map<String, Object> error = new HashMap<String, Object>();
				error.put("error", "缺少必要参数: file");
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

			// Phase 1: Record original counts for structural verification
			int origRowCount = reportDef.getRows() != null ? reportDef.getRows().size() : 0;
			int origColCount = reportDef.getColumns() != null ? reportDef.getColumns().size() : 0;

			List<Map<String, Object>> appliedCells = new ArrayList<Map<String, Object>>();
			List<Map<String, Object>> appliedOps = new ArrayList<Map<String, Object>>();
			List<String> verifyErrors = new ArrayList<String>();

			AiGenerationService service = new AiGenerationService(reportDef);

			// Phase 2: Apply cell mods
			if (modsData != null && !modsData.isEmpty()) {
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
				appliedCells = service.applyModifications(modifications);
				rebuildDef(reportDef);
			}

			// Phase 3: Apply structural ops
			if (opsData != null && !opsData.isEmpty()) {
				appliedOps = service.applyStructuralOps(opsData);
			}

			// Phase 4: Verify
			for (Map<String, Object> applied : appliedCells) {
				String cellName = (String) applied.get("cellName");
				String expectedType = (String) applied.get("type");
				com.kingint.ureportplus.definition.CellDefinition cell = service.findCellByName(cellName);
				if (cell == null) {
					verifyErrors.add(cellName + ": 修改后不存在");
					continue;
				}
				if (cell.getValue() == null) {
					verifyErrors.add(cellName + ": 值为空");
					continue;
				}
				String actualType = cell.getValue().getType().name().toLowerCase();
				if (!actualType.equals(expectedType)) {
					verifyErrors.add(cellName + ": 期望=" + expectedType + " 实际=" + actualType);
				}
			}
			int newRowCount = reportDef.getRows() != null ? reportDef.getRows().size() : 0;
			int newColCount = reportDef.getColumns() != null ? reportDef.getColumns().size() : 0;
			for (Map<String, Object> op : appliedOps) {
				String opType = (String) op.get("type");
				if ("insertRow".equals(opType) && newRowCount <= origRowCount)
					verifyErrors.add("insertRow: 行数未增 (" + origRowCount + "->" + newRowCount + ")");
				if ("deleteRow".equals(opType) && newRowCount >= origRowCount)
					verifyErrors.add("deleteRow: 行数未减 (" + origRowCount + "->" + newRowCount + ")");
				if ("insertCol".equals(opType) && newColCount <= origColCount)
					verifyErrors.add("insertCol: 列数未增 (" + origColCount + "->" + newColCount + ")");
				if ("deleteCol".equals(opType) && newColCount >= origColCount)
					verifyErrors.add("deleteCol: 列数未减 (" + origColCount + "->" + newColCount + ")");
			}

			// Phase 5: Commit or rollback
			if (verifyErrors.isEmpty()) {
				persistReport(file, reportDef);
				Map<String, Object> response = new HashMap<String, Object>();
				response.put("success", true);
				response.put("verified", true);
				response.put("appliedCells", appliedCells);
				response.put("appliedOps", appliedOps);
				writeObjectToJson(resp, response);
			} else {
				// Rollback: reload original from disk (memory was not persisted)
				ReportDefinition original = loadReport(file);
				if (original != null) {
					CacheUtils.cacheReportDefinition(file, original);
					com.kingint.ureportplus.console.cache.TempObjectCache.putObject(file, original);
				}
				Map<String, Object> response = new HashMap<String, Object>();
				response.put("success", false);
				response.put("error", "验证失败，已回滚: " + String.join("; ", verifyErrors));
				response.put("verifyErrors", verifyErrors);
				response.put("appliedCells", appliedCells);
				response.put("appliedOps", appliedOps);
				writeObjectToJson(resp, response);
			}
		} catch (Exception e) {
			Map<String, Object> error = new HashMap<String, Object>();
			error.put("error", "Apply failed: " + e.getMessage());
			writeObjectToJson(resp, error);
		}
	}

	private void rebuildDef(ReportDefinition reportDef) throws Exception {
		ReportRender reportRender = applicationContext.getBean(ReportRender.class);
		reportRender.rebuildReportDefinition(reportDef);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void persistReport(String file, ReportDefinition reportDef) throws Exception {
		// Cache in memory
		CacheUtils.cacheReportDefinition(file, reportDef);
		// Put into TempObjectCache so /designer/loadReport picks it up
		com.kingint.ureportplus.console.cache.TempObjectCache.putObject(file, reportDef);
	}

	private String readBody(HttpServletRequest req) throws Exception {
		StringBuilder sb = new StringBuilder();
		BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream(), "UTF-8"));
		String line;
		while ((line = reader.readLine()) != null) sb.append(line);
		return sb.toString();
	}

	private static class ConversationSession {
		AiConversationService service;
		long createdAt;
		String reportFile;
	}
}
