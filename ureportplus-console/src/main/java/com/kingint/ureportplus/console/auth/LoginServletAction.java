package com.kingint.ureportplus.console.auth;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import com.kingint.ureportplus.console.RenderPageServletAction;

public class LoginServletAction extends RenderPageServletAction {

	@Override
	public String url() {
		return "/login";
	}

	@Override
	public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String method = retriveMethod(req);
		if (method != null && method.equals("doLogin")) {
			doLogin(req, resp);
		} else if (method != null && method.equals("logout")) {
			doLogout(req, resp);
		} else {
			showLoginPage(req, resp, null);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void showLoginPage(HttpServletRequest req, HttpServletResponse resp, String error) throws ServletException, IOException {
		ConsoleAuthService auth = ConsoleAuthService.getInstance();
		if (auth.isAuthenticated(req)) {
			resp.sendRedirect(req.getContextPath() + "/ureport/");
			return;
		}

		resp.setContentType("text/html");
		resp.setCharacterEncoding("utf-8");
		PrintWriter writer = resp.getWriter();

		writer.println("<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>");
		writer.println("<title>UReportPlus 控制台登录</title>");
		writer.println("<style>");
		writer.println("*{margin:0;padding:0;box-sizing:border-box}");
		writer.println("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;background:#f1f5f9;display:flex;align-items:center;justify-content:center;min-height:100vh}");
		writer.println(".login-box{background:#fff;padding:40px;border-radius:12px;box-shadow:0 4px 24px rgba(0,0,0,.08);width:380px}");
		writer.println(".login-box h2{text-align:center;color:#0f172a;margin-bottom:8px;font-size:20px}");
		writer.println(".login-box .sub{text-align:center;color:#94a3b8;font-size:13px;margin-bottom:28px}");
		writer.println(".field{margin-bottom:16px}");
		writer.println(".field label{display:block;font-size:13px;color:#475569;margin-bottom:4px;font-weight:500}");
		writer.println(".field input{width:100%;padding:10px 12px;border:1px solid #e2e8f0;border-radius:8px;font-size:14px;outline:none;transition:border-color .2s}");
		writer.println(".field input:focus{border-color:#2563eb;box-shadow:0 0 0 3px rgba(37,99,235,.1)}");
		writer.println(".btn{width:100%;padding:10px;background:#2563eb;color:#fff;border:none;border-radius:8px;font-size:14px;font-weight:500;cursor:pointer;transition:background .2s}");
		writer.println(".btn:hover{background:#1d4ed8}");
		writer.println(".error{color:#dc2626;font-size:13px;text-align:center;margin-bottom:16px;padding:8px;background:#fef2f2;border-radius:6px}");
		writer.println("</style></head><body>");
		writer.println("<div class='login-box'>");
		writer.println("<h2>UReportPlus</h2><div class='sub'>报表控制台</div>");

		if (error != null) {
			writer.println("<div class='error'>" + error + "</div>");
		}

		writer.println("<form method='post' action='" + req.getContextPath() + "/ureport/login/doLogin'>");
		writer.println("<div class='field'><label>用户名</label><input type='text' name='username' placeholder='输入用户名' autofocus></div>");
		writer.println("<div class='field'><label>密码</label><input type='password' name='password' placeholder='输入密码'></div>");
		writer.println("<button type='submit' class='btn'>登 录</button>");
		writer.println("</form></div></body></html>");
		writer.close();
	}

	public void doLogin(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String username = req.getParameter("username");
		String password = req.getParameter("password");

		ConsoleAuthService auth = ConsoleAuthService.getInstance();
		if (auth.authenticate(username != null ? username : "", password != null ? password : "")) {
			auth.setAuthenticated(req);
			resp.sendRedirect(req.getContextPath() + "/ureport/");
		} else {
			showLoginPage(req, resp, "用户名或密码错误");
		}
	}

	public void doLogout(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		ConsoleAuthService.getInstance().logout(req);
		resp.sendRedirect(req.getContextPath() + "/ureport/login");
	}
}
