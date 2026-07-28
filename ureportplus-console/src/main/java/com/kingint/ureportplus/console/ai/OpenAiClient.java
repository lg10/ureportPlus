package com.kingint.ureportplus.console.ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI-compatible API client. Supports any provider (OpenAI, Claude API proxy, etc.)
 * Uses java.net.HttpURLConnection for Java 1.7 compatibility.
 */
public class OpenAiClient {
	private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	private final AiConfig config;

	public OpenAiClient() {
		this.config = AiConfig.getInstance();
	}

	/**
	 * Send a chat completion request and return the response text.
	 */
	@SuppressWarnings("unchecked")
	public String chat(String systemPrompt, String userPrompt) throws Exception {
		if (!config.isEnabled()) {
			throw new IllegalStateException("AI is not enabled. Set ureportplus.ai.enabled=true and ureportplus.ai.api-key in config.properties");
		}

		List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
		Map<String, String> sysMsg = new HashMap<String, String>();
		sysMsg.put("role", "system");
		sysMsg.put("content", systemPrompt);
		messages.add(sysMsg);

		Map<String, String> userMsg = new HashMap<String, String>();
		userMsg.put("role", "user");
		userMsg.put("content", userPrompt);
		messages.add(userMsg);

		Map<String, Object> body = new HashMap<String, Object>();
		body.put("model", config.getModel());
		body.put("messages", messages);
		body.put("max_tokens", config.getMaxTokens());
		body.put("temperature", config.getTemperature());

		String jsonBody = mapper.writeValueAsString(body);
		log.debug("[OpenAiClient] Request to {}: {}", config.getApiUrl(), jsonBody);

		String apiUrl = config.getApiUrl();
		if (!apiUrl.endsWith("/")) {
			apiUrl += "/";
		}
		apiUrl += "chat/completions";

		URL url = new URL(apiUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
		conn.setConnectTimeout(30000);
		conn.setReadTimeout(60000);

		try {
			OutputStream os = conn.getOutputStream();
			os.write(jsonBody.getBytes("UTF-8"));
			os.flush();
			os.close();

			int responseCode = conn.getResponseCode();
			if (responseCode != 200) {
				BufferedReader errorReader = new BufferedReader(
						new InputStreamReader(conn.getErrorStream(), "UTF-8"));
				StringBuilder errorBody = new StringBuilder();
				String line;
				while ((line = errorReader.readLine()) != null) {
					errorBody.append(line);
				}
				errorReader.close();
				throw new RuntimeException("AI API error " + responseCode + ": " + errorBody.toString());
			}

			BufferedReader reader = new BufferedReader(
					new InputStreamReader(conn.getInputStream(), "UTF-8"));
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			reader.close();

			Map<String, Object> responseMap = mapper.readValue(response.toString(), Map.class);
			List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
			if (choices == null || choices.isEmpty()) {
				throw new RuntimeException("AI API returned empty choices: " + response.toString());
			}

			Map<String, Object> choice = choices.get(0);
			Map<String, String> message = (Map<String, String>) choice.get("message");
			String content = message.get("content");

			log.debug("[OpenAiClient] Response: {}", content);
			return content;

		} finally {
			conn.disconnect();
		}
	}

	/**
	 * Chat with retry on validation failure.
	 * Returns the final response after all retries.
	 */
	public String chatWithRetry(String systemPrompt, String userPrompt,
			ResponseValidator validator) throws Exception {
		String lastResponse = null;
		String lastError = null;
		String currentUserPrompt = userPrompt;

		for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
			try {
				String response = chat(systemPrompt, currentUserPrompt);
				lastResponse = response;

				if (validator == null) {
					return response;
				}

				String validationError = validator.validate(response);
				if (validationError == null) {
					return response;
				}

				lastError = validationError;
				log.warn("[OpenAiClient] Attempt {} validation failed: {}", attempt + 1, validationError);

				// Build retry prompt with error feedback
				if (attempt < config.getMaxRetries()) {
					currentUserPrompt = userPrompt
							+ "\n\n[前一次回复校验失败: " + validationError + "]"
							+ "\n请修正上述问题后重新输出。";
				}

			} catch (Exception e) {
				lastError = e.getMessage();
				log.warn("[OpenAiClient] Attempt {} failed: {}", attempt + 1, e.getMessage());
				if (attempt >= config.getMaxRetries()) {
					throw e;
				}
			}
		}

		throw new RuntimeException("AI generation failed after " + (config.getMaxRetries() + 1)
				+ " attempts. Last error: " + lastError);
	}

	/**
	 * Interface for validating AI responses.
	 */
	public interface ResponseValidator {
		String validate(String jsonResponse);
	}
}
