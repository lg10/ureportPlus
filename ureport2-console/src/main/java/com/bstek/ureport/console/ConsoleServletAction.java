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
package com.bstek.ureport.console;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
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

import com.bstek.ureport.provider.report.ReportFile;
import com.bstek.ureport.provider.report.ReportProvider;

/**
 * UReport2 报表管理中心控制台
 * 列出所有已保存报表，支持预览/编辑跳转
 *
 * @author Jacky.gao
 * @since 2017年1月25日
 */
public class ConsoleServletAction extends RenderPageServletAction {

    private Collection<ReportProvider> reportProviders;

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
                    // 去掉 .ureport.xml 后缀显示
                    item.put("name", fullName.replace(".ureport.xml", ""));
                    // 构建完整文件名（带前缀，供预览/编辑链接使用）
                    if (fullName.startsWith(provider.getPrefix())) {
                        item.put("file", fullName);
                    } else {
                        item.put("file", provider.getPrefix() + fullName);
                    }
                    item.put("path", provider.getName());
                    reports.add(item);
                }
            }
        }

        VelocityContext context = new VelocityContext();
        context.put("contextPath", req.getContextPath());
        context.put("reports", reports);
        context.put("totalReports", reports.size());

        resp.setContentType("text/html");
        resp.setCharacterEncoding("utf-8");
        Template template = ve.getTemplate("ureport-html/console.html", "utf-8");
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
