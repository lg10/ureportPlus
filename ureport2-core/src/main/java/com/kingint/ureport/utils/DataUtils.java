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
package com.kingint.ureport.utils;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kingint.ureport.Utils;
import com.kingint.ureport.build.Context;
import com.kingint.ureport.definition.value.DatasetValue;
import com.kingint.ureport.definition.value.ExpressionValue;
import com.kingint.ureport.definition.value.Value;
import com.kingint.ureport.exception.ReportComputeException;
import com.kingint.ureport.expression.model.Expression;
import com.kingint.ureport.expression.model.expr.BaseExpression;
import com.kingint.ureport.expression.model.expr.JoinExpression;
import com.kingint.ureport.expression.model.expr.ParenExpression;
import com.kingint.ureport.expression.model.expr.dataset.DatasetExpression;
import com.kingint.ureport.model.Cell;

/**
 * @author Jacky.gao
 * @since 2017年6月12日
 */
public class DataUtils {
	private static final Logger log = LoggerFactory.getLogger(DataUtils.class);
	public static List<?> fetchData(Cell cell, Context context,String datasetName) {
		Cell leftCell=fetchLeftCell(cell, context, datasetName);
		Cell topCell=fetchTopCell(cell, context, datasetName);
		List<Object> leftList=null,topList=null;
		if(leftCell!=null){
			leftList=leftCell.getBindData();
		}
		if(topCell!=null){
			topList=topCell.getBindData();
		}
		if(leftCell!=null || topCell!=null){
			log.warn("[DEBUG fetchData] cell={}, leftCell={}, topCell={}",
					cell.getName(),
					leftCell!=null?leftCell.getName()+":"+leftCell.getData()+":"+(leftList!=null?leftList.size():0):"null",
					topCell!=null?topCell.getName()+":"+topCell.getData()+":"+(topList!=null?topList.size():0):"null");
		}
		if(leftList==null && topList==null){
			List<?> data=context.getDatasetData(datasetName);
			return data;
		}else if(leftList==null){
			return topList;
		}else if(topList==null){
			return leftList;
		}else{
			List<Object> list=null;
			Object data=null;
			String prop=null;
			if(leftList.size()>topList.size()){
				list=topList;
				data=leftCell.getData();
				Value value=leftCell.getValue();
				DatasetExpression de=fetchDatasetExpression(value);
				if(de==null){
					throw new ReportComputeException("Unsupport value : "+value);
				}
				prop=de.getProperty();
			}else{
				list=leftList;
				data=topCell.getData();
				Value value=topCell.getValue();
				DatasetExpression de=fetchDatasetExpression(value);
				if(de==null){
					throw new ReportComputeException("Unsupport value : "+value);
				}
				prop=de.getProperty();
			}
			List<Object> result=new ArrayList<Object>();
			for(Object obj:list){
				Object o=Utils.getProperty(obj, prop);
				if((o==null && data==null)){
					result.add(obj);
				}else if(o!=null && o.equals(data)){
					result.add(obj);
				}else if(data!=null && data.equals(o)){
					result.add(obj);
				}
			}
			return result;
		}
	}
	public static Cell fetchLeftCell(Cell cell, Context context,String datasetName){
		Cell leftCell=cell.getLeftParentCell();
		while(leftCell!=null){
			Value leftCellValue=leftCell.getValue();
			DatasetExpression leftDSValue=fetchDatasetExpression(leftCellValue);
			if(leftDSValue!=null){
				String leftDatasetName=leftDSValue.getDatasetName();
				if(leftDatasetName.equals(datasetName)){
					if(leftCell.getBindData()!=null){
						return leftCell;
					}
				}
			}
			leftCell=leftCell.getLeftParentCell();
		}
		return null;
	}
	public static Cell fetchTopCell(Cell cell, Context context,String datasetName){
		Cell topCell=cell.getTopParentCell();
		while(topCell!=null){
			Value topCellValue=topCell.getValue();
			DatasetExpression leftDSValue=fetchDatasetExpression(topCellValue);
			if(leftDSValue!=null){
				String leftDatasetName=leftDSValue.getDatasetName();
				if(leftDatasetName.equals(datasetName)){
					if(topCell.getBindData()!=null){
						return topCell;
					}
				}
			}
			topCell=topCell.getTopParentCell();
		}
		return null;
	}
	public static DatasetExpression fetchDatasetExpression(Value value){
		if(value instanceof ExpressionValue){
			ExpressionValue exprValue=(ExpressionValue)value;
			Expression expr=exprValue.getExpression();
			if(expr instanceof DatasetExpression){
				return (DatasetExpression)expr;
			}else if(expr instanceof ParenExpression){
				ParenExpression parenExpr=(ParenExpression)expr;
				DatasetExpression targetExpr=buildDatasetExpression(parenExpr);
				return targetExpr;
			}else{				
				return null;
			}
		}else if(value instanceof DatasetValue){
			return (DatasetValue)value;
		}
		return null;
	}
	
	private static DatasetExpression buildDatasetExpression(JoinExpression joinExpr){
		List<BaseExpression> expressions=joinExpr.getExpressions();
		for(BaseExpression baseExpr:expressions){
			if(baseExpr instanceof DatasetExpression){
				return (DatasetExpression)baseExpr;
			}else if(baseExpr instanceof JoinExpression){
				JoinExpression childExpr=(JoinExpression)baseExpr;
				return buildDatasetExpression(childExpr);
			}
		}
		return null;
	}
}
