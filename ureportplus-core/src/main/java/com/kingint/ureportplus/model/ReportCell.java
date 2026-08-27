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
package com.kingint.ureportplus.model;

import java.util.List;

import com.kingint.ureportplus.definition.CellStyle;
import com.kingint.ureportplus.definition.Expand;
import com.kingint.ureportplus.definition.value.Value;

/**
 * @author Jacky.gao
 * @since 2017年1月19日
 */
public interface ReportCell {
	CellStyle getCellStyle();
	String getName();
	int getRowSpan();
	int getColSpan();
	Row getRow();
	Column getColumn();
	Object getData();
	Value getValue();
	Expand getExpand();
	List<Object> getBindData();
}
