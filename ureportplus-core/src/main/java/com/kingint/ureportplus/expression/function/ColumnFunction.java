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
package com.kingint.ureportplus.expression.function;

import java.util.List;

import com.kingint.ureportplus.build.Context;
import com.kingint.ureportplus.expression.model.data.ExpressionData;
import com.kingint.ureportplus.model.Cell;
import com.kingint.ureportplus.model.Column;

/**
 * @author Jacky.gao
 * @since 2017年4月25日
 */
public class ColumnFunction implements Function{
	@Override
	public Object execute(List<ExpressionData<?>> dataList, Context context,Cell currentCell) {
		Column col=currentCell.getColumn();
		return col.getColumnNumber();
	}
	@Override
	public String name() {
		return "column";
	}
}
