package com.kingint.ureportplus.font.kaiti;

import com.kingint.ureportplus.export.pdf.font.FontRegister;

/**
 * @author Jacky.gao
 * @since 2014年5月7日
 */
public class KaiTiFontRegister implements FontRegister {

	public String getFontName() {
		return "楷体";
	}

	public String getFontPath() {
		return "com/kingint/ureportplus/font/kaiti/SIMKAI.TTF";
	}
}
