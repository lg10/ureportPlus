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
package com.kingint.ureportplus.console.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;

import com.itextpdf.text.Document;
import com.itextpdf.text.RectangleReadOnly;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import com.kingint.ureportplus.build.ReportBuilder;
import com.kingint.ureportplus.console.BaseServletAction;
import com.kingint.ureportplus.console.cache.TempObjectCache;
import com.kingint.ureportplus.console.exception.ReportDesignException;
import com.kingint.ureportplus.definition.Orientation;
import com.kingint.ureportplus.definition.PagingMode;
import com.kingint.ureportplus.definition.Paper;
import com.kingint.ureportplus.definition.ReportDefinition;
import com.kingint.ureportplus.exception.ReportComputeException;
import com.kingint.ureportplus.exception.ReportException;
import com.kingint.ureportplus.export.ExportConfigure;
import com.kingint.ureportplus.export.ExportConfigureImpl;
import com.kingint.ureportplus.export.ExportManager;
import com.kingint.ureportplus.export.ReportRender;
import com.kingint.ureportplus.export.pdf.PdfProducer;
import com.kingint.ureportplus.model.Report;

/**
 * @author Jacky.gao
 * @since 2017年3月20日
 */
public class ExportPdfServletAction extends BaseServletAction{
	private ReportBuilder reportBuilder;
	private ExportManager exportManager;
	private ReportRender reportRender;
	private PdfProducer pdfProducer=new PdfProducer();
	@Override
	public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String method=retriveMethod(req);
		if(method!=null){
			invokeMethod(method, req, resp);
		}else{			
			buildPdf(req, resp,false);
		}
	}
	public void show(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		buildPdf(req, resp,true);
	}

	public void buildPdf(HttpServletRequest req, HttpServletResponse resp,boolean forPrint) throws IOException {
		String file=req.getParameter("_u");
		file=decode(file);
		if(StringUtils.isBlank(file)){
			throw new ReportComputeException("Report file can not be null.");
		}
		float customWidth=parseCustomWidth(req);
		OutputStream outputStream=null;
		try {
			String fileName=req.getParameter("_n");
			fileName=buildDownloadFileName(file, fileName, ".pdf");
			fileName=new String(fileName.getBytes("UTF-8"),"ISO8859-1");
			if(forPrint){
				resp.setContentType("application/pdf");
				resp.setHeader("Content-Disposition","inline;filename=\"" + fileName + "\"");
			}else{
				resp.setContentType("application/octet-stream;charset=ISO8859-1");
				resp.setHeader("Content-Disposition","attachment;filename=\"" + fileName + "\"");
			}
			outputStream=resp.getOutputStream();
			Map<String, Object> parameters = buildParameters(req);
			if(file.equals(PREVIEW_KEY)){
				ReportDefinition reportDefinition=(ReportDefinition)TempObjectCache.getObject(PREVIEW_KEY);
				if(reportDefinition==null){
					throw new ReportDesignException("Report data has expired,can not do export pdf.");
				}
				checkAuth(reportDefinition, req);
				Report report=reportBuilder.buildReport(reportDefinition, parameters);
				float scale=buildReceiptScale(report.getPaper(),customWidth);
				if(scale>0){
					produceScaledPdf(report,scale,outputStream);
				}else{
					pdfProducer.produce(report, outputStream);
				}
			}else{
				ReportDefinition reportDef=reportRender.getReportDefinition(file);
				checkAuth(reportDef, req);
				float scale=buildReceiptScale(reportDef.getPaper(),customWidth);
				if(scale>0){
					Report report=reportRender.render(reportDef, parameters);
					produceScaledPdf(report,scale,outputStream);
				}else{
					ExportConfigure configure=new ExportConfigureImpl(file,parameters,outputStream);
					exportManager.exportPdf(configure);
				}
			}
		}catch(Exception ex) {
			throw new ReportException(ex);
		}finally {
			outputStream.flush();
			outputStream.close();
		}
	}

	private float parseCustomWidth(HttpServletRequest req){
		String w=req.getParameter("_w");
		if(StringUtils.isBlank(w)){
			return 0;
		}
		try{
			float v=Float.parseFloat(w.trim());
			return v>0 ? v : 0;
		}catch(NumberFormatException e){
			return 0;
		}
	}

	// 小票模式 _w：目标宽度(mm)/纸张宽度(mm) 的等比缩放系数，非小票模式返回0
	private float buildReceiptScale(Paper paper,float customWidth){
		if(customWidth<=0 || paper==null){
			return 0;
		}
		if(paper.getPagingMode()!=PagingMode.receipt){
			return 0;
		}
		int width=paper.getWidth();
		if(paper.getOrientation()!=null && paper.getOrientation().equals(Orientation.landscape)){
			width=paper.getHeight();
		}
		int paperWidthMm=Math.round(width*100f/283f);
		if(paperWidthMm<=0){
			return 0;
		}
		return customWidth/paperWidthMm;
	}

	private void produceScaledPdf(Report report,float scale,OutputStream outputStream) throws Exception{
		ByteArrayOutputStream buffer=new ByteArrayOutputStream();
		pdfProducer.produce(report, buffer);
		PdfReader reader=new PdfReader(buffer.toByteArray());
		try{
			com.itextpdf.text.Rectangle first=reader.getPageSizeWithRotation(1);
			Document document=new Document(new RectangleReadOnly(first.getWidth()*scale,first.getHeight()*scale));
			PdfWriter writer=PdfWriter.getInstance(document,outputStream);
			document.open();
			PdfContentByte cb=writer.getDirectContent();
			for(int i=1;i<=reader.getNumberOfPages();i++){
				com.itextpdf.text.Rectangle box=reader.getPageSizeWithRotation(i);
				if(i>1){
					document.setPageSize(new RectangleReadOnly(box.getWidth()*scale,box.getHeight()*scale));
					document.newPage();
				}
				PdfImportedPage page=writer.getImportedPage(reader,i);
				cb.addTemplate(page,scale,0,0,scale,0,0);
			}
			document.close();
		}finally{
			reader.close();
		}
	}
	
	public void newPaging(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String file=req.getParameter("_u");
		if(StringUtils.isBlank(file)){
			throw new ReportComputeException("Report file can not be null.");
		}
		Report report=null;
		Map<String, Object> parameters = buildParameters(req);
		if(file.equals(PREVIEW_KEY)){
			ReportDefinition reportDefinition=(ReportDefinition)TempObjectCache.getObject(PREVIEW_KEY);
			if(reportDefinition==null){
				throw new ReportDesignException("Report data has expired,can not do export pdf.");
			}
			report=reportBuilder.buildReport(reportDefinition, parameters);	
		}else{
			ReportDefinition reportDefinition=reportRender.getReportDefinition(file);
			report=reportRender.render(reportDefinition, parameters);
		}
		String paper=req.getParameter("_paper");
		ObjectMapper mapper=new ObjectMapper();
		Paper newPaper=mapper.readValue(paper, Paper.class);
		report.rePaging(newPaper);
	}
	
	public void setReportRender(ReportRender reportRender) {
		this.reportRender = reportRender;
	}
	
	public void setExportManager(ExportManager exportManager) {
		this.exportManager = exportManager;
	}
	
	public void setReportBuilder(ReportBuilder reportBuilder) {
		this.reportBuilder = reportBuilder;
	}

	@Override
	public String url() {
		return "/pdf";
	}
}
