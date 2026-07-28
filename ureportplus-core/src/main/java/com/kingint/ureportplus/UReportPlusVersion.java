package com.kingint.ureportplus;

import java.io.InputStream;
import java.util.Properties;

/**
 * UReportPlus version utility
 */
public class UReportPlusVersion {
	private static String version = "unknown";
	private static String buildTime = "unknown";
	static {
		try (InputStream in = UReportPlusVersion.class.getClassLoader()
				.getResourceAsStream("ureportplus-version.properties")) {
			if (in != null) {
				Properties p = new Properties();
				p.load(in);
				version = p.getProperty("ureportplus.version", "unknown");
				buildTime = p.getProperty("ureportplus.build.time", "unknown");
			}
		} catch (Exception ignored) {}
	}

	public static String getVersion() { return version; }
	public static String getBuildTime() { return buildTime; }
	public static String getFullVersion() { return version + " (built: " + buildTime + ")"; }
}
