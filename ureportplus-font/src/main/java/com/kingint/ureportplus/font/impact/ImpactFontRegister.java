package com.kingint.ureportplus.font.impact;

import com.kingint.ureportplus.export.pdf.font.FontRegister;

/**
 * @author Jacky.gao
 * @since 2014年5月7日
 */
public class ImpactFontRegister implements FontRegister {

	public String getFontName() {
		return "Impact";
	}

	public String getFontPath() {
		return "com/kingint/ureportplus/font/impact/IMPACT.TTF";
	}
}
