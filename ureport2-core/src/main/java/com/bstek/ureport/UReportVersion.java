package com.bstek.ureport;

import java.io.InputStream;
import java.util.Properties;

/**
 * UReport2 version utility
 */
public class UReportVersion {
	private static String version = "unknown";
	private static String buildTime = "unknown";
	static {
		try (InputStream in = UReportVersion.class.getClassLoader()
				.getResourceAsStream("ureport-version.properties")) {
			if (in != null) {
				Properties p = new Properties();
				p.load(in);
				version = p.getProperty("ureport.version", "unknown");
				buildTime = p.getProperty("ureport.build.time", "unknown");
			}
		} catch (Exception ignored) {}
	}

	public static String getVersion() { return version; }
	public static String getBuildTime() { return buildTime; }
	public static String getFullVersion() { return version + " (built: " + buildTime + ")"; }
}
