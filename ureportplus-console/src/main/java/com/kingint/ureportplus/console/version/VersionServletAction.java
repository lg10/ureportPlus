package com.kingint.ureportplus.console.version;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.kingint.ureportplus.UReportPlusVersion;
import com.kingint.ureportplus.console.BaseServletAction;

/**
 * Version info endpoint: GET /ureport/version
 */
public class VersionServletAction extends BaseServletAction {

	@Override
	public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("application/json;charset=UTF-8");
		PrintWriter writer = resp.getWriter();
		writer.write("{\"version\":\"" + UReportPlusVersion.getVersion() + "\","
				+ "\"buildTime\":\"" + UReportPlusVersion.getBuildTime() + "\","
				+ "\"fullVersion\":\"" + UReportPlusVersion.getFullVersion() + "\"}");
		writer.flush();
	}

	@Override
	public String url() {
		return "/version";
	}
}
