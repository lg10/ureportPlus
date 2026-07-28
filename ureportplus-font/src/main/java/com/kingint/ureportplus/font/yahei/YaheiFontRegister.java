package com.kingint.ureportplus.font.yahei;

import com.kingint.ureportplus.export.pdf.font.FontRegister;

/**
 * @author Jacky.gao
 * @since 2014年5月7日
 */
public class YaheiFontRegister implements FontRegister {

	public String getFontName() {
		return "微软雅黑";
	}

	public String getFontPath() {
		return "com/kingint/ureportplus/font/yahei/msyh.ttc";
	}
}
