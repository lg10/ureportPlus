package com.kingint.ureport.console.ai;

import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI configuration loaded from config.properties.
 * Supports any OpenAI-compatible API provider.
 */
public class AiConfig {
	private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

	private boolean enabled;
	private String provider;
	private String apiKey;
	private String apiUrl;
	private String model;
	private int maxRetries;
	private int maxTokens;
	private double temperature;

	private static AiConfig instance;

	private AiConfig() {
		loadConfig();
	}

	public static synchronized AiConfig getInstance() {
		if (instance == null) {
			instance = new AiConfig();
		}
		return instance;
	}

	private void loadConfig() {
		Properties props = new Properties();
		InputStream in = null;
		try {
			in = AiConfig.class.getClassLoader().getResourceAsStream("config.properties");
			if (in != null) {
				props.load(in);

				enabled = "true".equalsIgnoreCase(props.getProperty("ureport.ai.enabled", "false"));
				provider = props.getProperty("ureport.ai.provider", "openai");
				apiKey = props.getProperty("ureport.ai.api-key", "");
				apiUrl = props.getProperty("ureport.ai.api-url", "https://api.openai.com/v1");
				model = props.getProperty("ureport.ai.model", "gpt-4o");

				try {
					maxRetries = Integer.parseInt(props.getProperty("ureport.ai.max-retries", "2"));
				} catch (NumberFormatException e) {
					maxRetries = 2;
				}
				try {
					maxTokens = Integer.parseInt(props.getProperty("ureport.ai.max-tokens", "4096"));
				} catch (NumberFormatException e) {
					maxTokens = 4096;
				}
				try {
					temperature = Double.parseDouble(props.getProperty("ureport.ai.temperature", "0.3"));
				} catch (NumberFormatException e) {
					temperature = 0.3;
				}

				log.info("[AiConfig] Loaded: enabled={}, provider={}, model={}, url={}",
						enabled, provider, model, apiUrl);
			} else {
				log.warn("[AiConfig] config.properties not found, AI disabled by default");
				enabled = false;
			}
		} catch (Exception e) {
			log.error("[AiConfig] Failed to load config: {}", e.getMessage());
			enabled = false;
		} finally {
			if (in != null) {
				try { in.close(); } catch (Exception ignored) {}
			}
		}
	}

	public boolean isEnabled() { return enabled && apiKey != null && !apiKey.isEmpty(); }
	public String getProvider() { return provider; }
	public String getApiKey() { return apiKey; }
	public String getApiUrl() { return apiUrl; }
	public String getModel() { return model; }
	public int getMaxRetries() { return maxRetries; }
	public int getMaxTokens() { return maxTokens; }
	public double getTemperature() { return temperature; }
}
