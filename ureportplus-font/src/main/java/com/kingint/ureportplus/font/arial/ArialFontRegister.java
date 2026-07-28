package com.kingint.ureportplus.font.arial;

import com.kingint.ureportplus.export.pdf.font.FontRegister;


/**
 * @author Jacky.gao
 * @since 2014年5月7日
 */
public class ArialFontRegister implements FontRegister {

	public String getFontName() {
		return "Arial";
	}

	public String getFontPath() {
		return "com/kingint/ureportplus/font/arial/ARIAL.TTF";
	}
}
