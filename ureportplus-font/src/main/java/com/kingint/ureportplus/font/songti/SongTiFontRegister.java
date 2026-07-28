package com.kingint.ureportplus.font.songti;

import com.kingint.ureportplus.export.pdf.font.FontRegister;

/**
 * @author Jacky.gao
 * @since 2014年5月7日
 */
public class SongTiFontRegister implements FontRegister {

	public String getFontName() {
		return "宋体";
	}

	public String getFontPath() {
		return "com/kingint/ureportplus/font/songti/SIMSUN.TTC";
	}
}
