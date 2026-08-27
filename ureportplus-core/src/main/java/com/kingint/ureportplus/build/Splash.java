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
package com.kingint.ureportplus.build;

/**
 * @author Jacky.gao
 * @since 2017年6月19日
 */
public class Splash {
	public void doPrint(){
		StringBuilder sb=new StringBuilder();
		sb.append("\n");
		sb.append("  _  ___           _       _     _____  ");
		sb.append("\n");
		sb.append(" | |/ (_)         (_)     | |   |_   _| ");
		sb.append("\n");
		sb.append(" | ' / _ _ __   __ _ _ __ | |_    | |   ");
		sb.append("\n");
		sb.append(" |  < | | '_ \\ / _` | '_ \\| __|   | |   ");
		sb.append("\n");
		sb.append(" | . \\| | | | | (_| | | | | |_    | |   ");
		sb.append("\n");
		sb.append(" |_|\\_\\_|_| |_|\\__, |_| |_|\\__|   |_|   ");
		sb.append("\n");
		sb.append("                __/ |                    ");
		sb.append("\n");
		sb.append("               |___/                     ");
		sb.append("\n");
		sb.append("================================================");
		sb.append("\n");
		sb.append("    KinginT 报表模板系统");
		sb.append("\n");
		sb.append("================================================");
		sb.append("\n");
		System.out.println(sb.toString());
	}
}
