package com.kingint.ureport.expression.function;

import java.util.List;

import com.kingint.ureport.build.Context;
import com.kingint.ureport.expression.model.data.ExpressionData;
import com.kingint.ureport.model.Cell;

/**
 * @author Jacky.gao
 * @since 2017年12月7日
 */
public class ParameterIsEmptyFunction extends ParameterFunction{
	@Override
	public Object execute(List<ExpressionData<?>> dataList, Context context,
			Cell currentCell) {
		Object obj = super.execute(dataList, context, currentCell);
		if(obj==null || obj.toString().trim().equals("")){
			return true;
		}
		return false;
	}
	@Override
	public String name() {
		return "emptyparam";
	}
}
