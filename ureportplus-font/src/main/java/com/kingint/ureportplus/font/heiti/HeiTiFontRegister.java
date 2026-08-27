package com.kingint.ureportplus.font.heiti;

import com.kingint.ureportplus.export.pdf.font.FontRegister;

/**
 * @author Jacky.gao
 * @since 2014年5月7日
 */
public class HeiTiFontRegister implements FontRegister {

	public String getFontName() {
		return "黑体";
	}

	public String getFontPath() {
		return "com/kingint/ureportplus/font/heiti/SIMHEI.TTF";
	}
}
