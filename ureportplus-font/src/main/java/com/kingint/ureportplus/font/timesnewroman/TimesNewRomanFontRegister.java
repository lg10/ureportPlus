package com.kingint.ureportplus.font.timesnewroman;

import com.kingint.ureportplus.export.pdf.font.FontRegister;

/**
 * @author Jacky.gao
 * @since 2014年5月7日
 */
public class TimesNewRomanFontRegister implements FontRegister {

	public String getFontName() {
		return "Times New Roman";
	}

	public String getFontPath() {
		return "com/kingint/ureportplus/font/timesnewroman/TIMES.TTF";
	}
}
