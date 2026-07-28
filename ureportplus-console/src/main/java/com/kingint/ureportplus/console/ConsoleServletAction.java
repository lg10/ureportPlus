/*******************************************************************************
 * Copyright 2017 Bstek
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.kingint.ureportplus.console;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import com.kingint.ureportplus.console.auth.ConsoleAuthService;
import com.kingint.ureportplus.provider.report.ReportFile;
import com.kingint.ureportplus.provider.report.ReportProvider;

/**
 * UReportPlus 报表管理中心控制台
 * 列出所有已保存报表，支持预览/编辑跳转
 *
 * @author Jacky.gao
 * @since 2017年1月25日
 */
public class ConsoleServletAction extends RenderPageServletAction {

    private Collection<ReportProvider> reportProviders;

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!ConsoleAuthService.getInstance().isAuthenticated(req)) {
            resp.sendRedirect(req.getContextPath() + "/ureport/login");
            return;
        }

        List<Map<String, String>> reports = new ArrayList<>();
        for (ReportProvider provider : reportProviders) {
            if (provider.disabled()) {
                continue;
            }
            List<ReportFile> files = provider.getReportFiles();
            if (files != null) {
                for (ReportFile file : files) {
                    Map<String, String> item = new HashMap<>();
                    String fullName = file.getName();
                    // 去掉 .ureportplus.xml 后缀显示
                    item.put("name", fullName.replace(".ureportplus.xml", ""));
                    // 构建完整文件名（带前缀，供预览/编辑链接使用）
                    if (fullName.startsWith(provider.getPrefix())) {
                        item.put("file", fullName);
                    } else {
                        item.put("file", provider.getPrefix() + fullName);
                    }
                    item.put("path", provider.getName());
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    Date createDate = file.getCreateDate();
                    Date updateDate = file.getUpdateDate();
                    item.put("createTime", createDate != null ? sdf.format(createDate) : "-");
                    item.put("updateTime", updateDate != null ? sdf.format(updateDate) : "-");
                    reports.add(item);
                }
            }
        }

        // Build example reports list
        List<Map<String, String>> examples = new ArrayList<Map<String, String>>();
        String[][] exampleDefs = {
            {"example01-simple-table",      "简单表格 — 基础数据展示，select聚合"},
            {"example02-vertical-group",    "纵向分层分组 — 集团→酒店→小计→合计"},
            {"example03-horizontal-group",  "纵向分组+合并行 — 酒店名合并+房型明细"},
            {"example04-expressions",       "表达式基础 — row(), column()函数"},
            {"example05-cross-tab",         "交叉表 — 行×列营收矩阵"},
            {"example06-more-expressions",  "表达式大全 — if/else, round, abs"},
            {"example07-url-params",        "URL传参 — param()函数"},
            {"example08-page-functions",    "页函数 — page(), pages()"},
            {"example09-conditional-format","条件逻辑 — if/else + 链接URL"},
            {"example10-cross-group",       "纵×横交叉分组 — 酒店(行)×房型(列)"},
            {"example11-group-detail",      "分层+合并 — 集团→酒店(合并)→房型明细→小计"},
        };
        for (String[] def : exampleDefs) {
            Map<String, String> ex = new HashMap<String, String>();
            ex.put("name", def[0]);
            ex.put("desc", def[1]);
            ex.put("file", "file:examples/" + def[0] + ".ureportplus.xml");
            examples.add(ex);
        }

        int providerCount = 0;
        for (ReportProvider provider : reportProviders) {
            if (!provider.disabled()) {
                List<ReportFile> files = provider.getReportFiles();
                if (files != null && !files.isEmpty()) {
                    providerCount++;
                }
            }
        }

        VelocityContext context = new VelocityContext();
        context.put("contextPath", req.getContextPath());
        context.put("reports", reports);
        context.put("totalReports", reports.size());
        context.put("totalExamples", examples.size());
        context.put("providerCount", providerCount);
        context.put("examples", examples);

        resp.setContentType("text/html");
        resp.setCharacterEncoding("utf-8");
        Template template = ve.getTemplate("ureportplus-html/console.html", "utf-8");
        PrintWriter writer = resp.getWriter();
        template.merge(context, writer);
        writer.close();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        super.setApplicationContext(applicationContext);
        reportProviders = applicationContext.getBeansOfType(ReportProvider.class).values();
    }

    @Override
    public String url() {
        return "/";
    }
}
