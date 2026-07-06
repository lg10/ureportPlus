package com.kingint.ureport.font.comicsansms;

import com.kingint.ureport.export.pdf.font.FontRegister;

/**
 * @author Jacky.gao
 * @since 2014年5月7日
 */
public class ComicSansMSFontRegister implements FontRegister {

	public String getFontName() {
		return "Comic Sans MS";
	}

	public String getFontPath() {
		return "com/bstek/ureport/font/comicsansms/COMIC.TTF";
	}
}
