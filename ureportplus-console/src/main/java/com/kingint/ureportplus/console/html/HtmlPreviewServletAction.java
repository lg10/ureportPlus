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
package com.kingint.ureportplus.console.html;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.codehaus.jackson.map.ObjectMapper;

import com.kingint.ureportplus.build.Context;
import com.kingint.ureportplus.build.ReportBuilder;
import com.kingint.ureportplus.build.paging.Page;
import com.kingint.ureportplus.cache.CacheUtils;
import com.kingint.ureportplus.chart.ChartData;
import com.kingint.ureportplus.console.MobileUtils;
import com.kingint.ureportplus.console.RenderPageServletAction;
import com.kingint.ureportplus.console.cache.TempObjectCache;
import com.kingint.ureportplus.console.exception.ReportDesignException;
import com.kingint.ureportplus.definition.Paper;
import com.kingint.ureportplus.definition.ReportDefinition;
import com.kingint.ureportplus.definition.searchform.FormPosition;
import com.kingint.ureportplus.exception.ReportComputeException;
import com.kingint.ureportplus.export.ExportManager;
import com.kingint.ureportplus.export.FullPageData;
import com.kingint.ureportplus.export.PageBuilder;
import com.kingint.ureportplus.export.ReportRender;
import com.kingint.ureportplus.export.SinglePageData;
import com.kingint.ureportplus.export.html.HtmlProducer;
import com.kingint.ureportplus.export.html.HtmlReport;
import com.kingint.ureportplus.export.html.SearchFormData;
import com.kingint.ureportplus.model.Report;

/**
 * @author Jacky.gao
 * @since 2017年2月15日
 */
public class HtmlPreviewServletAction extends RenderPageServletAction {
	private ExportManager exportManager;
	private ReportBuilder reportBuilder;
	private ReportRender reportRender;
	private HtmlProducer htmlProducer=new HtmlProducer();
	@Override
	public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String method=retriveMethod(req);
		if(method!=null){
			invokeMethod(method, req, resp);
		}else{
			VelocityContext context = new VelocityContext();
			HtmlReport htmlReport=null;
			String errorMsg=null;
			try{
				htmlReport=loadReport(req);
			}catch(Exception ex){
				if(!(ex instanceof ReportDesignException)){
					ex.printStackTrace();					
				}
				errorMsg=buildExceptionMessage(ex);
			}
			String title=buildTitle(req);
			context.put("title", title);
			if(htmlReport==null){
				context.put("content", "<div style='color:red'><strong>报表计算出错，错误信息如下：</strong><br><div style=\"margin:10px\">"+errorMsg+"</div></div>");
				context.put("error", true);
				context.put("searchFormJs", "");
				context.put("downSearchFormHtml", "");
				context.put("upSearchFormHtml", "");
			}else{
				SearchFormData formData=htmlReport.getSearchFormData();
				if(formData!=null){
					context.put("searchFormJs", formData.getJs());
					if(formData.getFormPosition().equals(FormPosition.up)){
						context.put("upSearchFormHtml", formData.getHtml());						
						context.put("downSearchFormHtml", "");						
					}else{
						context.put("downSearchFormHtml", formData.getHtml());						
						context.put("upSearchFormHtml", "");						
					}
				}else{
					context.put("searchFormJs", "");
					context.put("downSearchFormHtml", "");
					context.put("upSearchFormHtml", "");	
				}
				context.put("content", htmlReport.getContent());
				context.put("style", htmlReport.getStyle());
				context.put("reportAlign", htmlReport.getReportAlign());				
				context.put("totalPage", htmlReport.getTotalPage()); 
				context.put("totalPageWithCol", htmlReport.getTotalPageWithCol()); 
				context.put("pageIndex", htmlReport.getPageIndex());
				context.put("chartDatas", convertJson(htmlReport.getChartDatas()));
				context.put("error", false);
				context.put("file", req.getParameter("_u"));
				context.put("intervalRefreshValue",htmlReport.getHtmlIntervalRefreshValue());
				String customParameters=buildCustomParameters(req);
				context.put("customParameters", customParameters);
				context.put("_t", "");
				Tools tools=null;
				if(MobileUtils.isMobile(req)){
					tools=new Tools(false);
					tools.setShow(false);
				}else{
					String toolsInfo=req.getParameter("_t");
					if(StringUtils.isNotBlank(toolsInfo)){
						tools=new Tools(false);
						if(toolsInfo.equals("0")){
							tools.setShow(false);
						}else{
							String[] infos=toolsInfo.split(",");
							for(String name:infos){
								tools.doInit(name);
							}						
						}
						context.put("_t", toolsInfo);
						context.put("hasTools", true);
					}else{
						tools=new Tools(true);
					}
				}
				context.put("tools", tools);
			}
			if(htmlReport!=null){
			context.put("showPageNumber", htmlReport.isShowPageNumber());
			context.put("pageNumPos", htmlReport.getPageNumPos());
			context.put("pageNumAlign", htmlReport.getPageNumAlign());
			context.put("paperWidth", htmlReport.getPaperWidth());
			context.put("paperHeight", htmlReport.getPaperHeight());
			context.put("paperMarginLeft", htmlReport.getPaperMarginLeft());
			context.put("paperMarginRight", htmlReport.getPaperMarginRight());
			context.put("paperMarginTop", htmlReport.getPaperMarginTop());
			context.put("paperMarginBottom", htmlReport.getPaperMarginBottom());
			context.put("paperOrientation", htmlReport.getPaperOrientation());
			context.put("receipt", htmlReport.isReceipt());
			context.put("headerHtml", htmlReport.getHeaderHtml());
			context.put("footerHtml", htmlReport.getFooterHtml());
		}else{
			context.put("paperWidth", 210);
			context.put("paperHeight", 297);
			context.put("paperMarginLeft", 15);
			context.put("paperMarginRight", 15);
			context.put("paperMarginTop", 20);
			context.put("paperMarginBottom", 20);
			context.put("showPageNumber", false);
			context.put("pageNumPos", "footer");
			context.put("pageNumAlign", "center");
			context.put("receipt", false);
			context.put("headerHtml", "");
			context.put("footerHtml", "");
		}
		// 小票模式 _w 参数：目标显示宽度(mm)，按纸张宽度等比缩放
		String customWidthParam=req.getParameter("_w");
		if(StringUtils.isNotBlank(customWidthParam) && Boolean.TRUE.equals(context.get("receipt"))){
			try{
				float customWidth=Float.parseFloat(customWidthParam.trim());
				Object paperWidthObj=context.get("paperWidth");
				int paperWidthMm=paperWidthObj instanceof Number ? ((Number)paperWidthObj).intValue() : 0;
				if(customWidth>0 && paperWidthMm>0){
					context.put("customWidth", customWidthParam.trim());
					context.put("receiptScale", customWidth/paperWidthMm);
				}
			}catch(NumberFormatException ignored){
			}
		}
		context.put("contextPath", req.getContextPath());
			resp.setContentType("text/html");
			resp.setCharacterEncoding("utf-8");
			Template template=ve.getTemplate("ureportplus-html/html-preview.html","utf-8");
			PrintWriter writer=resp.getWriter();
			template.merge(context, writer);
			writer.close();
		}
	}
	
	private String buildTitle(HttpServletRequest req){
		String title=req.getParameter("_title");
		if(StringUtils.isBlank(title)){
			title=req.getParameter("_u");
			title=decode(title);
			int point=title.lastIndexOf(".ureportplus.xml");
			if(point>-1){
				title=title.substring(0,point);
			}
			if(title.equals("p")){
				title="设计中报表";
			}
		}else{
			title=decode(title);
		}
		return title+"-ureportplus";
	}
	
	private String convertJson(Collection<ChartData> data){
		if(data==null || data.size()==0){
			return "";
		}
		ObjectMapper mapper=new ObjectMapper();
		try {
			String json = mapper.writeValueAsString(data);
			return json;
		} catch (Exception e) {
			throw new ReportComputeException(e);
		}
	}
	
	public void loadData(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		HtmlReport htmlReport=loadReport(req);
		writeObjectToJson(resp, htmlReport);
	}

	public void loadPrintPages(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String file=req.getParameter("_u");
		file=decode(file);
		if(StringUtils.isBlank(file)){
			throw new ReportComputeException("Report file can not be null.");
		}
		Map<String, Object> parameters = buildParameters(req);
		ReportDefinition reportDefinition=null;
		if(file.equals(PREVIEW_KEY)){
			reportDefinition=(ReportDefinition)TempObjectCache.getObject(PREVIEW_KEY);
			if(reportDefinition==null){
				throw new ReportDesignException("Report data has expired,can not do export excel.");
			}
		}else{
			reportDefinition=reportRender.getReportDefinition(file);
		}
		checkAuth(reportDefinition, req);
		Report report=reportBuilder.buildReport(reportDefinition, parameters);
		Map<String, ChartData> chartMap=report.getContext().getChartDataMap();
		if(chartMap.size()>0){
			CacheUtils.storeChartDataMap(chartMap);				
		}
		FullPageData pageData=PageBuilder.buildFullPageData(report);
		StringBuilder sb=new StringBuilder();
		List<List<Page>> list=pageData.getPageList();
		Context context=report.getContext();
		if(list.size()>0){
			for(int i=0;i<list.size();i++){
				List<Page> columnPages=list.get(i);
				String html=htmlProducer.produce(context,columnPages,pageData.getColumnMargin(),false);
				sb.append(html);
			}
		}else{
			List<Page> pages=report.getPages();
			for(Page page:pages){
				String html=htmlProducer.produce(context,page, false);
				sb.append(html);
			}
		}
		// 提取页眉/页脚纯文本（用于CSS @page margin boxes）
		String hfHeaderLeft="", hfHeaderCenter="", hfHeaderRight="";
		String hfFooterLeft="", hfFooterCenter="", hfFooterRight="";
		if(report.getHeader()!=null){
			try{
				com.kingint.ureportplus.build.paging.HeaderFooter hf=report.getHeader().buildHeaderFooter(1,report.getContext());
				hfHeaderLeft=hf.getLeft()!=null?hf.getLeft():"";
				hfHeaderCenter=hf.getCenter()!=null?hf.getCenter():"";
				hfHeaderRight=hf.getRight()!=null?hf.getRight():"";
			}catch(Exception e){}
		}
		if(report.getFooter()!=null){
			try{
				com.kingint.ureportplus.build.paging.HeaderFooter hf=report.getFooter().buildHeaderFooter(1,report.getContext());
				hfFooterLeft=hf.getLeft()!=null?hf.getLeft():"";
				hfFooterCenter=hf.getCenter()!=null?hf.getCenter():"";
				hfFooterRight=hf.getRight()!=null?hf.getRight():"";
			}catch(Exception e){}
		}
		Map<String,String> map=new HashMap<String,String>();
		map.put("html", sb.toString());
		map.put("headerLeft", hfHeaderLeft);
		map.put("headerCenter", hfHeaderCenter);
		map.put("headerRight", hfHeaderRight);
		map.put("footerLeft", hfFooterLeft);
		map.put("footerCenter", hfFooterCenter);
		map.put("footerRight", hfFooterRight);
		writeObjectToJson(resp, map);
	}
	
	public void loadPagePaper(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String file=req.getParameter("_u");
		file=decode(file);
		if(StringUtils.isBlank(file)){
			throw new ReportComputeException("Report file can not be null.");
		}
		ReportDefinition report=null;
		if(file.equals(PREVIEW_KEY)){
			report=(ReportDefinition)TempObjectCache.getObject(PREVIEW_KEY);
			if(report==null){
				throw new ReportDesignException("Report data has expired.");
			}
		}else{
			report=reportRender.getReportDefinition(file);
		}
		checkAuth(report, req);
		Paper paper=report.getPaper();
		writeObjectToJson(resp, paper);
	}
	
	private HtmlReport loadReport(HttpServletRequest req) {
		Map<String, Object> parameters = buildParameters(req);
		HtmlReport htmlReport=null;
		String file=req.getParameter("_u");
		file=decode(file);
		String pageIndex=req.getParameter("_i");
		if(StringUtils.isBlank(file)){
			throw new ReportComputeException("Report file can not be null.");
		}
		if(file.equals(PREVIEW_KEY)){
			ReportDefinition reportDefinition=(ReportDefinition)TempObjectCache.getObject(PREVIEW_KEY);
			if(reportDefinition==null){
				throw new ReportDesignException("Report data has expired,can not do preview.");
			}
			checkAuth(reportDefinition, req);
			Report report=reportBuilder.buildReport(reportDefinition, parameters);
			Map<String, ChartData> chartMap=report.getContext().getChartDataMap();
			if(chartMap.size()>0){
				CacheUtils.storeChartDataMap(chartMap);				
			}
			htmlReport=new HtmlReport();
			String html=null;
			if(StringUtils.isNotBlank(pageIndex) && !pageIndex.equals("0")){
				Context context=report.getContext();
				int index=Integer.valueOf(pageIndex);
				SinglePageData pageData=PageBuilder.buildSinglePageData(index, report);
				List<Page> pages=pageData.getPages();
				if(pages.size()==1){
					Page page=pages.get(0);
					html=htmlProducer.produce(context,page,false);					
				}else{
					html=htmlProducer.produce(context,pages,pageData.getColumnMargin(),false);					
				}
				htmlReport.setTotalPage(pageData.getTotalPages());
				htmlReport.setPageIndex(index);
			}else{
				// 打印预览模式：全部纸张页面堆叠展示，每页用 report-page-sheet 包裹
				html=buildAllPagesHtml(report);
				htmlReport.setPageIndex(0);
			}
			if(report.getPaper().isColumnEnabled()){
				htmlReport.setColumn(report.getPaper().getColumnCount());
			}
			htmlReport.setChartDatas(report.getContext().getChartDataMap().values());
			htmlReport.setContent(html);
			htmlReport.setTotalPage(report.getPages().size());
			htmlReport.setStyle(reportDefinition.getStyle());
			htmlReport.setSearchFormData(reportDefinition.buildSearchFormData(report.getContext().getDatasetMap(),parameters));
			htmlReport.setReportAlign(report.getPaper().getHtmlReportAlign().name());
			htmlReport.setHtmlIntervalRefreshValue(report.getPaper().getHtmlIntervalRefreshValue());
			htmlReport.setShowPageNumber(report.getPaper().isShowPageNumber());
			htmlReport.setPageNumPos(report.getPaper().getPageNumPos());
			htmlReport.setPageNumAlign(report.getPaper().getPageNumAlign());
				applyPaperToHtmlReport(report.getPaper(), report, htmlReport);
		}else{
			ReportDefinition reportDefinition=reportRender.getReportDefinition(file);
			checkAuth(reportDefinition, req);
			if(StringUtils.isNotBlank(pageIndex) && !pageIndex.equals("0")){
				int index=Integer.valueOf(pageIndex);
				htmlReport=exportManager.exportHtml(file,req.getContextPath(),parameters,index);
			}else{
				// 打印预览模式：全部纸张页面堆叠展示
				Report report=reportRender.render(reportDefinition, parameters);
				Map<String, ChartData> chartMap=report.getContext().getChartDataMap();
				if(chartMap.size()>0){
					CacheUtils.storeChartDataMap(chartMap);
				}
				htmlReport=new HtmlReport();
				String html=buildAllPagesHtml(report);
				htmlReport.setContent(html);
				if(reportDefinition.getPaper().isColumnEnabled()){
					htmlReport.setColumn(reportDefinition.getPaper().getColumnCount());
				}
				htmlReport.setTotalPage(report.getPages().size());
				htmlReport.setPageIndex(0);
				htmlReport.setStyle(reportDefinition.getStyle());
				htmlReport.setSearchFormData(reportDefinition.buildSearchFormData(report.getContext().getDatasetMap(),parameters));
				htmlReport.setReportAlign(report.getPaper().getHtmlReportAlign().name());
				htmlReport.setChartDatas(report.getContext().getChartDataMap().values());
				htmlReport.setHtmlIntervalRefreshValue(report.getPaper().getHtmlIntervalRefreshValue());
				applyPaperToHtmlReport(report.getPaper(), report, htmlReport);
			}
		}
		return htmlReport;
	}
	
	
	private String buildCustomParameters(HttpServletRequest req){
		StringBuilder sb=new StringBuilder();
		Enumeration<?> enumeration=req.getParameterNames();
		while(enumeration.hasMoreElements()){
			Object obj=enumeration.nextElement();
			if(obj==null){
				continue;
			}
			String name=obj.toString();
			String value=req.getParameter(name);
			if(name==null || value==null || (name.startsWith("_") && !name.equals("_n"))){
				continue;
			}
			if(sb.length()>0){
				sb.append("&");
			}
			sb.append(name);
			sb.append("=");
			sb.append(value);
		}
		return sb.toString();
	}
	
	private String buildExceptionMessage(Throwable throwable){
		Throwable root=buildRootException(throwable);
		StringWriter sw=new StringWriter();
		PrintWriter pw=new PrintWriter(sw);
		root.printStackTrace(pw);
		String trace=sw.getBuffer().toString();
		trace=trace.replaceAll("\n", "<br>");
		pw.close();
		return trace;
	}
	
	/**
	 * 将Paper配置(mm)和页眉页脚复制到HtmlReport
	 */
	private void applyPaperToHtmlReport(Paper paper, Report report, HtmlReport htmlReport) {
		int pw = paper.getWidth();
		int ph = paper.getHeight();
		if (pw == 0) pw = 595;
		if (ph == 0) ph = 842;
		// 横向时交换宽高
		if (paper.getOrientation() != null && "landscape".equals(paper.getOrientation().name())) {
			int tmp = pw; pw = ph; ph = tmp;
		}
		htmlReport.setPaperWidth(Math.round(pw * 100f / 283f));
		htmlReport.setPaperHeight(Math.round(ph * 100f / 283f));
		htmlReport.setPaperMarginLeft(Math.round(paper.getLeftMargin() * 100f / 283f));
		htmlReport.setPaperMarginRight(Math.round(paper.getRightMargin() * 100f / 283f));
		htmlReport.setPaperMarginTop(Math.round(paper.getTopMargin() * 100f / 283f));
		htmlReport.setPaperMarginBottom(Math.round(paper.getBottomMargin() * 100f / 283f));
		htmlReport.setPaperOrientation(paper.getOrientation() != null ? paper.getOrientation().name() : "portrait");
		// 小票模式：预览不分页，高度自适应，仅保证宽度一致
		htmlReport.setReceipt(paper.getPagingMode() == com.kingint.ureportplus.definition.PagingMode.receipt);
		htmlReport.setShowPageNumber(paper.isShowPageNumber());
		htmlReport.setPageNumPos(paper.getPageNumPos() != null ? paper.getPageNumPos() : "footer");
		htmlReport.setPageNumAlign(paper.getPageNumAlign() != null ? paper.getPageNumAlign() : "center");

		// 渲染页眉页脚
		if (report.getHeader() != null && report.getPages().size() > 0) {
			com.kingint.ureportplus.build.paging.HeaderFooter hf = report.getHeader()
				.buildHeaderFooter(1, report.getContext());
			htmlReport.setHeaderHtml(buildHfHtml(hf, true));
		}
		if (report.getFooter() != null && report.getPages().size() > 0) {
			com.kingint.ureportplus.build.paging.HeaderFooter hf = report.getFooter()
				.buildHeaderFooter(1, report.getContext());
			htmlReport.setFooterHtml(buildHfHtml(hf, false));
		}
	}

	private String buildHfHtml(com.kingint.ureportplus.build.paging.HeaderFooter hf, boolean isHeader) {
		StringBuilder sb = new StringBuilder();
		String borderStyle = isHeader ? "border-bottom:1px solid #000;" : "border-top:1px solid #000;";
		sb.append("<div class='report-hf' style='display:flex;justify-content:space-between;font-family:");
		sb.append(hf.getFontFamily() != null ? hf.getFontFamily() : "宋体");
		sb.append(";font-size:").append(hf.getFontSize()).append("pt;");
		sb.append("color:rgb(").append(hf.getForecolor() != null ? hf.getForecolor() : "0,0,0").append(");");
		if (hf.isBold()) sb.append("font-weight:bold;");
		if (hf.isItalic()) sb.append("font-style:italic;");
		if (hf.isUnderline()) sb.append("text-decoration:underline;");
		sb.append(borderStyle);
		sb.append("padding:4px 0;margin-bottom:4px;");
		sb.append("'>");
		sb.append("<span>").append(hf.getLeft() != null ? hf.getLeft() : "").append("</span>");
		sb.append("<span>").append(hf.getCenter() != null ? hf.getCenter() : "").append("</span>");
		sb.append("<span>").append(hf.getRight() != null ? hf.getRight() : "").append("</span>");
		sb.append("</div>");
		return sb.toString();
	}

	/**
	 * 构建全部纸张页面的HTML（打印预览模式），每页用 report-page-sheet 包裹，实现A4纸张堆叠展示。
	 * 每张纸独立渲染页眉/页脚。页眉页脚位置：上边距=页眉分割线上方空白，下边距=页脚分割线下方空白。
	 */
	private String buildAllPagesHtml(Report report) {
		FullPageData pageData = PageBuilder.buildFullPageData(report);
		Context context = report.getContext();
		com.kingint.ureportplus.definition.Paper paper = report.getPaper();
		int mt = Math.round(paper.getTopMargin() * 100f / 283f);
		int mb = Math.round(paper.getBottomMargin() * 100f / 283f);
		int ml = Math.round(paper.getLeftMargin() * 100f / 283f);
		int mr = Math.round(paper.getRightMargin() * 100f / 283f);

		StringBuilder sb = new StringBuilder();
		List<List<Page>> list = pageData.getPageList();
		int pageNum = 0;
		if (list != null && list.size() > 0) {
			for (int i = 0; i < list.size(); i++) {
				pageNum++;
				List<Page> columnPages = list.get(i);
				String pageHtml = htmlProducer.produce(context, columnPages, pageData.getColumnMargin(), false);
				sb.append(buildPageSheet(pageHtml, report, pageNum, mt, mb, ml, mr));
			}
		} else {
			List<Page> pages = report.getPages();
			if (pages != null) {
				for (Page page : pages) {
					pageNum++;
					String pageHtml = htmlProducer.produce(context, page, false);
					sb.append(buildPageSheet(pageHtml, report, pageNum, mt, mb, ml, mr));
				}
			}
		}
		return sb.toString();
	}

	private String buildPageSheet(String pageHtml, Report report, int pageNum, int mt, int mb, int ml, int mr) {
		StringBuilder sb = new StringBuilder();
		// 纸张使用CSS类统一padding，不重复inline（避免与CSS叠加）
		sb.append("<div class='report-page-sheet'>");
		boolean hasHeader = report.getHeader() != null;
		boolean hasFooter = report.getFooter() != null;
		if (hasHeader) {
			// 负margin拉入上边距区，不挤占内容空间
			sb.append("<div class='report-hf' style='margin:-").append(mt).append("mm 0 4px 0;padding:0'>");
			try {
				com.kingint.ureportplus.build.paging.HeaderFooter hf =
					report.getHeader().buildHeaderFooter(pageNum, report.getContext());
				sb.append(buildHfHtml(hf, true));
			} catch (Exception e) {}
			sb.append("</div>");
		}
		sb.append(pageHtml);
		if (hasFooter) {
			// 负margin拉入下边距区
			sb.append("<div class='report-hf' style='margin:4px 0 -").append(mb).append("mm 0;padding:0'>");
			try {
				com.kingint.ureportplus.build.paging.HeaderFooter hf =
					report.getFooter().buildHeaderFooter(pageNum, report.getContext());
				sb.append(buildHfHtml(hf, false));
			} catch (Exception e) {}
			sb.append("</div>");
		}
		sb.append("</div>");
		return sb.toString();
	}

	private String buildPageHeader(Report report, int pageNum) {
		if (report.getHeader() == null) return "";
		try {
			com.kingint.ureportplus.build.paging.HeaderFooter hf =
				report.getHeader().buildHeaderFooter(pageNum, report.getContext());
			return buildHfHtml(hf, true);
		} catch (Exception e) { return ""; }
	}

	private String buildPageFooter(Report report, int pageNum) {
		if (report.getFooter() == null) return "";
		try {
			com.kingint.ureportplus.build.paging.HeaderFooter hf =
				report.getFooter().buildHeaderFooter(pageNum, report.getContext());
			return buildHfHtml(hf, false);
		} catch (Exception e) { return ""; }
	}

	public void setExportManager(ExportManager exportManager) {
		this.exportManager = exportManager;
	}

	public void setReportBuilder(ReportBuilder reportBuilder) {
		this.reportBuilder = reportBuilder;
	}
	public void setReportRender(ReportRender reportRender) {
		this.reportRender = reportRender;
	}

	@Override
	public String url() {
		return "/preview";
	}
}
