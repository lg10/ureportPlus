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
package com.kingint.ureportplus.export;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.kingint.ureportplus.build.ReportBuilder;
import com.kingint.ureportplus.cache.CacheUtils;
import com.kingint.ureportplus.definition.CellDefinition;
import com.kingint.ureportplus.definition.Expand;
import com.kingint.ureportplus.definition.ReportDefinition;
import com.kingint.ureportplus.exception.ReportException;
import com.kingint.ureportplus.exception.ReportParseException;
import com.kingint.ureportplus.export.builder.down.DownCellbuilder;
import com.kingint.ureportplus.export.builder.right.RightCellbuilder;
import com.kingint.ureportplus.model.Report;
import com.kingint.ureportplus.parser.ReportParser;
import com.kingint.ureportplus.provider.report.ReportProvider;

/**
 * @author Jacky.gao
 * @since 2016年12月4日
 */
public class ReportRender implements ApplicationContextAware{
	private ReportParser reportParser;
	private ReportBuilder reportBuilder;
	private Collection<ReportProvider> reportProviders;
	private DownCellbuilder downCellParentbuilder=new DownCellbuilder();
	private RightCellbuilder rightCellParentbuilder=new RightCellbuilder();
	public Report render(String file,Map<String,Object> parameters){
		ReportDefinition reportDefinition=getReportDefinition(file);
		return reportBuilder.buildReport(reportDefinition,parameters);
	}
	
	public Report render(ReportDefinition reportDefinition,Map<String,Object> parameters){
		return reportBuilder.buildReport(reportDefinition,parameters);
	}
	
	public ReportDefinition getReportDefinition(String file){
		ReportDefinition reportDefinition=CacheUtils.getReportDefinition(file);
		if(reportDefinition==null){
			reportDefinition=parseReport(file);
			rebuildReportDefinition(reportDefinition);
			CacheUtils.cacheReportDefinition(file, reportDefinition);
		}
		return reportDefinition;
	}
	
	public void rebuildReportDefinition(ReportDefinition reportDefinition){
		List<CellDefinition> cells=reportDefinition.getCells();
		for(CellDefinition cell:cells){
			addRowChildCell(cell,cell);
			addColumnChildCell(cell,cell);
		}
		for(CellDefinition cell:cells){
			Expand expand=cell.getExpand();
			if(expand.equals(Expand.Down)){
				downCellParentbuilder.buildParentCell(cell,cells);
			}else if(expand.equals(Expand.Right)){
				rightCellParentbuilder.buildParentCell(cell,cells);
			}
		}
	}
	
	public ReportDefinition parseReport(String file){
		InputStream inputStream=null;
		try {
			inputStream=buildReportFile(file);
			ReportDefinition reportDefinition=reportParser.parse(inputStream,file);
			return reportDefinition;
		}finally{
			try {
				if(inputStream!=null){
					inputStream.close();					
				}
			} catch (IOException e) {
				throw new ReportParseException(e);
			}
		}
	}
	
	private InputStream buildReportFile(String file){
		InputStream inputStream=null;
		for(ReportProvider provider:reportProviders){
			if(file.startsWith(provider.getPrefix())){
				inputStream=provider.loadReport(file);
			}
		}
		if(inputStream==null){
			throw new ReportException("Report ["+file+"] not support.");
		}
		return inputStream;
	}
	
	private void addRowChildCell(CellDefinition cell,CellDefinition childCell){
		addRowChildCell(cell,childCell,new HashSet<String>());
	}
	private void addRowChildCell(CellDefinition cell,CellDefinition childCell,HashSet<String> visited){
		CellDefinition leftCell=cell.getLeftParentCell();
		if(leftCell==null){
			return;
		}
		String name=leftCell.getName();
		if(visited.contains(name)){
			throw new com.kingint.ureportplus.exception.ReportException("Circular left-parent-cell reference detected at cell ["+name+"].");
		}
		visited.add(name);
		List<CellDefinition> childrenCells=leftCell.getRowChildrenCells();
		childrenCells.add(childCell);
		addRowChildCell(leftCell,childCell,visited);
	}
	private void addColumnChildCell(CellDefinition cell,CellDefinition childCell){
		addColumnChildCell(cell,childCell,new HashSet<String>());
	}
	private void addColumnChildCell(CellDefinition cell,CellDefinition childCell,HashSet<String> visited){
		CellDefinition topCell=cell.getTopParentCell();
		if(topCell==null){
			return;
		}
		String name=topCell.getName();
		if(visited.contains(name)){
			throw new com.kingint.ureportplus.exception.ReportException("Circular top-parent-cell reference detected at cell ["+name+"].");
		}
		visited.add(name);
		List<CellDefinition> childrenCells=topCell.getColumnChildrenCells();
		childrenCells.add(childCell);
		addColumnChildCell(topCell,childCell,visited);
	}
	public void setReportParser(ReportParser reportParser) {
		this.reportParser = reportParser;
	}
	public void setReportBuilder(ReportBuilder reportBuilder) {
		this.reportBuilder = reportBuilder;
	}
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		reportProviders=applicationContext.getBeansOfType(ReportProvider.class).values();
	}
}
