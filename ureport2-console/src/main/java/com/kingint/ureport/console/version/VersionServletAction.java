package com.kingint.ureport.console.version;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.kingint.ureport.UReportVersion;
import com.kingint.ureport.console.BaseServletAction;

/**
 * Version info endpoint: GET /ureport/version
 */
public class VersionServletAction extends BaseServletAction {

	@Override
	public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("application/json;charset=UTF-8");
		PrintWriter writer = resp.getWriter();
		writer.write("{\"version\":\"" + UReportVersion.getVersion() + "\","
				+ "\"buildTime\":\"" + UReportVersion.getBuildTime() + "\","
				+ "\"fullVersion\":\"" + UReportVersion.getFullVersion() + "\"}");
		writer.flush();
	}

	@Override
	public String url() {
		return "/version";
	}
}
