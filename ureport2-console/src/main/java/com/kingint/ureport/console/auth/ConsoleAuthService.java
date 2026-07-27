package com.kingint.ureport.console.auth;

import java.io.InputStream;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Console authentication service.
 * Enabled by simply configuring ureport.console.username in config.properties.
 * If password is configured, it's required; otherwise any password is accepted.
 */
public class ConsoleAuthService {
	private static final Logger log = LoggerFactory.getLogger(ConsoleAuthService.class);
	private static final String SESSION_KEY = "ureport_console_authed";

	private String username;
	private String password;
	private boolean enabled;

	private static ConsoleAuthService instance;

	private ConsoleAuthService() {
		loadConfig();
	}

	public static synchronized ConsoleAuthService getInstance() {
		if (instance == null) {
			instance = new ConsoleAuthService();
		}
		return instance;
	}

	private void loadConfig() {
		Properties props = new Properties();
		InputStream in = null;
		try {
			in = getClass().getClassLoader().getResourceAsStream("config.properties");
			if (in != null) {
				props.load(in);
				username = props.getProperty("ureport.console.username", "").trim();
				password = props.getProperty("ureport.console.password", "").trim();
				enabled = !username.isEmpty();
				log.info("[ConsoleAuth] enabled={}, username={}", enabled, username);
			}
		} catch (Exception e) {
			log.warn("[ConsoleAuth] Failed to load config: {}", e.getMessage());
			enabled = false;
		} finally {
			if (in != null) { try { in.close(); } catch (Exception ignored) {} }
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isAuthenticated(HttpServletRequest req) {
		if (!enabled) return true;
		HttpSession session = req.getSession(false);
		return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY));
	}

	public boolean authenticate(String user, String pass) {
		if (!enabled) return true;
		if (!username.equals(user)) return false;
		if (password.isEmpty()) return true;
		return password.equals(pass);
	}

	public void setAuthenticated(HttpServletRequest req) {
		req.getSession(true).setAttribute(SESSION_KEY, Boolean.TRUE);
	}

	public void logout(HttpServletRequest req) {
		HttpSession session = req.getSession(false);
		if (session != null) session.removeAttribute(SESSION_KEY);
	}
}
