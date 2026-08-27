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
package com.kingint.ureportplus.build.paging;

import java.util.ArrayList;
import java.util.List;

import com.kingint.ureportplus.definition.Band;
import com.kingint.ureportplus.model.Report;
import com.kingint.ureportplus.model.Row;

/**
 * 小票模式分页：不做分页，全部内容放入同一页，忽略纸张高度，仅保证宽度一致。
 */
public class ReceiptPagination extends BasePagination implements Pagination {
	@Override
	public List<Page> doPaging(Report report) {
		List<Row> rows=report.getRows();
		List<Row> headerRows=report.getHeaderRepeatRows();
		List<Row> footerRows=report.getFooterRepeatRows();
		List<Row> titleRows=report.getTitleRows();
		List<Row> summaryRows=report.getSummaryRows();
		List<Page> pages=new ArrayList<Page>();
		List<Row> pageRows=new ArrayList<Row>();
		List<Row> pageRepeatHeaders=new ArrayList<Row>();
		List<Row> pageRepeatFooters=new ArrayList<Row>();
		pageRepeatHeaders.addAll(headerRows);
		pageRepeatFooters.addAll(footerRows);
		int rowSize=rows.size();
		for(int i=0;i<rowSize;i++){
			Row row=rows.get(i);
			int rowRealHeight=row.getRealHeight();
			if(rowRealHeight==0){
				continue;
			}
			Band band=row.getBand();
			if(band!=null){
				String rowKey=row.getRowKey();
				int index=-1;
				if(band.equals(Band.headerrepeat)){
					for(int j=0;j<pageRepeatHeaders.size();j++){
						Row headerRow=pageRepeatHeaders.get(j);
						if(headerRow.getRowKey().equals(rowKey)){
							index=j;
							break;
						}
					}
					if(index>-1){
						pageRepeatHeaders.remove(index);
						pageRepeatHeaders.add(index,row);
					}
				}else if(band.equals(Band.footerrepeat)){
					for(int j=0;j<pageRepeatFooters.size();j++){
						Row footerRow=pageRepeatFooters.get(j);
						if(footerRow.getRowKey().equals(rowKey)){
							index=j;
							break;
						}
					}
					if(index>-1){
						pageRepeatFooters.remove(index);
						pageRepeatFooters.add(index,row);
					}
				}
				if(!Band.subtotal.equals(band)){
					continue;
				}
			}
			pageRows.add(row);
			row.setPageIndex(1);
		}
		if(pageRows.size()>0){
			Page newPage=buildPage(pageRows,pageRepeatHeaders,pageRepeatFooters,titleRows,1,report);
			pages.add(newPage);
		}
		report.getContext().setTotalPages(pages.size());
		buildPageHeaderFooter(pages, report);
		if(pages.size()>0){
			buildSummaryRows(summaryRows, pages);
		}
		return pages;
	}
}
