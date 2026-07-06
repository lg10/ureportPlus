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
package com.kingint.ureport.export.builder.right;

import java.util.List;
import java.util.Map;

import com.kingint.ureport.Range;
import com.kingint.ureport.definition.BlankCellInfo;
import com.kingint.ureport.definition.CellDefinition;
import com.kingint.ureport.parser.BuildUtils;

/**
 * @author Jacky.gao
 * @since 2017年2月24日
 */
public class RightCellbuilder {
	private TopParentCellCreator topParentCellCreator=new TopParentCellCreator();
	public void buildParentCell(CellDefinition cell,List<CellDefinition> cells){
		List<Range> rangeList=topParentCellCreator.buildParentCells(cell);
		Range childRange=buildChildrenCells(cell,rangeList);
		buildChildrenBlankCells(cell,cells,childRange);
		Range rowChildRange=buildRowChildrenCells(cell,rangeList);
		buildRowChildrenBlankCells(cell,cells,rowChildRange);
		Range colRange=buildColumnRange(rangeList);
		if(rowChildRange.getStart()!=-1 && (colRange.getStart()==-1 || rowChildRange.getStart()<colRange.getStart())){
			colRange.setStart(rowChildRange.getStart());
		}
		if(rowChildRange.getEnd()>colRange.getEnd()){
			colRange.setEnd(rowChildRange.getEnd());
		}
		buildColumnsBlankCells(cell,cells,colRange);
		int start=colRange.getStart(),end=colRange.getEnd();
		int colNumberStart=cell.getColumnNumber(),colNumberEnd=cell.getColumnNumber();
		int colSpan=cell.getColSpan();
		if(colSpan>0){
			colNumberEnd+=colSpan-1;
		}
		int rangeStart=0,rangeEnd=0;
		if(start!=-1){
			rangeStart=start-colNumberStart;
		}
		if(end>colNumberStart && end>colNumberEnd){
			rangeEnd=end-colNumberStart;
		}else{
			rangeEnd=colNumberEnd-colNumberStart;
		}
		Range duplicateRange=new Range(rangeStart,rangeEnd);
		cell.setDuplicateRange(duplicateRange);
	}
	

	private void buildColumnsBlankCells(CellDefinition cell,List<CellDefinition> cells,Range range){
		Map<String,BlankCellInfo> blankCellNamesMap=cell.getNewBlankCellsMap();
		int start=range.getStart(),end=range.getEnd();
		int nextEnd=0;
		for(int i=start;i<=end;i++){
			for(CellDefinition cellDef:cells){
				String name=cellDef.getName();
				if(cellPrcessed(cell, name)){
					continue;
				}
				int colNumber=cellDef.getColumnNumber();
				if(colNumber==i){
					int offset=colNumber-cell.getColumnNumber();
					blankCellNamesMap.put(name, new BlankCellInfo(offset,cellDef.getColSpan(),false));
				}else if(colNumber<i){
					int endColNumber=BuildUtils.buildColNumberEnd(cellDef, colNumber);
					if(endColNumber>=i){
						int offset=colNumber-cell.getColumnNumber();
						blankCellNamesMap.put(name, new BlankCellInfo(offset,cellDef.getColSpan(),false));
						if(i>end && i>nextEnd){
							nextEnd=i;
						}
					}
				}
			}
		}
		if(nextEnd>end){
			buildColumnsBlankCells(cell,cells,new Range(end,nextEnd));
		}
	}
	

	private Range buildColumnRange(List<Range> rangeList){
		Range colRange=new Range();
		for(Range range:rangeList){
			for(int i=range.getStart();i<=range.getEnd();i++){
				if(colRange.getStart()==-1 || i<colRange.getStart()){
					colRange.setStart(i);
				}
				if(colRange.getEnd()<i){
					colRange.setEnd(i);
				}
			}
		}
		return colRange;
	}
	
	private Range buildChildrenCells(CellDefinition cell,List<Range> rangeList){
		Range range=new Range();
		List<CellDefinition> childrenCells=cell.getColumnChildrenCells();
		for(CellDefinition childCell:childrenCells){
			cell.getNewCellNames().add(childCell.getName());
			int colNumber=childCell.getColumnNumber();
			int endColNumber=BuildUtils.buildColNumberEnd(childCell, colNumber);
			rangeList.add(new Range(colNumber,endColNumber));
			if(endColNumber>range.getEnd()){
				range.setEnd(endColNumber);
			}
			if(range.getStart()==-1 || colNumber<range.getStart()){
				range.setStart(colNumber);
			}
		}
		return range;
	}

	/**
	 * 处理当前单元格的行子格（右侧同行的子单元格），将它们加入newCellNames，
	 * 使得向右展开时这些子格所在的列也会被复制，从而支持列级分组。
	 */
	private Range buildRowChildrenCells(CellDefinition cell,List<Range> rangeList){
		Range range=new Range();
		List<CellDefinition> rowChildrenCells=cell.getRowChildrenCells();
		for(CellDefinition childCell:rowChildrenCells){
			String childName=childCell.getName();
			if(!cell.getNewCellNames().contains(childName)){
				cell.getNewCellNames().add(childName);
			}
			int colNumber=childCell.getColumnNumber();
			int endColNumber=BuildUtils.buildColNumberEnd(childCell, colNumber);
			rangeList.add(new Range(colNumber,endColNumber));
			if(endColNumber>range.getEnd()){
				range.setEnd(endColNumber);
			}
			if(range.getStart()==-1 || colNumber<range.getStart()){
				range.setStart(colNumber);
			}
		}
		return range;
	}

	/**
	 * 处理行子格范围内的空白单元格
	 */
	private void buildRowChildrenBlankCells(CellDefinition cell,List<CellDefinition> cells,Range childRange){
		int startColNumber=cell.getColumnNumber();
		int endColNumber=BuildUtils.buildColNumberEnd(cell, startColNumber);
		int start=childRange.getStart(),end=childRange.getEnd();
		if(start!=-1 && start<startColNumber){
			startColNumber=start;
		}
		if(end>endColNumber){
			endColNumber=end;
		}
		Map<String,BlankCellInfo> blankCellNamesMap=cell.getNewBlankCellsMap();
		for(int i=startColNumber;i<=endColNumber;i++){
			for(CellDefinition c : cells){
				if(c.getColumnNumber()!=i){
					continue;
				}
				if(c.equals(cell)){
					continue;
				}
				String name=c.getName();
				boolean contain=cellPrcessed(cell,name);
				if(contain){
					continue;
				}
				int offset=c.getColumnNumber()-cell.getColumnNumber();
				blankCellNamesMap.put(name, new BlankCellInfo(offset,c.getColSpan(),false));
			}
		}
	}
	
	
	private void buildChildrenBlankCells(CellDefinition cell,List<CellDefinition> cells,Range childRange){
		int startColNumber=cell.getColumnNumber();
		int endColNumber=BuildUtils.buildColNumberEnd(cell, startColNumber);
		int start=childRange.getStart(),end=childRange.getEnd();
		if(start!=-1 && start<startColNumber){
			startColNumber=start;
		}
		if(end>endColNumber){
			endColNumber=end;
		}
		Map<String,BlankCellInfo> blankCellNamesMap=cell.getNewBlankCellsMap();
		for(int i=startColNumber;i<=endColNumber;i++){
			for(CellDefinition c : cells){
				if(c.getColumnNumber()!=i){
					continue;
				}
				if(c.equals(cell)){
					continue;
				}
				String name=c.getName();
				boolean contain=cellPrcessed(cell,name);
				if(contain){
					continue;
				}
				int offset=c.getColumnNumber()-cell.getColumnNumber();
				blankCellNamesMap.put(name, new BlankCellInfo(offset,c.getColSpan(),false));
			}			
		}
	}
	
	private boolean cellPrcessed(CellDefinition cell,String name){
		List<String> newCellNames=cell.getNewCellNames();
		List<String> increaseCellNames=cell.getIncreaseSpanCellNames();
		Map<String,BlankCellInfo> blankCellNamesMap=cell.getNewBlankCellsMap();
		boolean contain=false;
		if(cell.getName().equals(name)){
			contain=true;			
		}
		if(newCellNames.contains(name)){
			contain=true;
		}
		if(increaseCellNames.contains(name)){
			contain=true;
		}
		if(blankCellNamesMap.containsKey(name)){
			contain=true;					
		}
		return contain;
	}
}
