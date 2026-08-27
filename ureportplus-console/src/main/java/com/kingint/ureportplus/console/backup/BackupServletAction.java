package com.kingint.ureportplus.console.backup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.codehaus.jackson.map.ObjectMapper;

import com.kingint.ureportplus.console.RenderPageServletAction;
import com.kingint.ureportplus.provider.report.ReportFile;
import com.kingint.ureportplus.provider.report.ReportProvider;

/**
 * Backup export/import for report files.
 *
 * Export: POST /ureport/backup/export  { names: ["r1","r2"] | "ALL" }
 *   → downloads .ureportplus.bak (ZIP with base64-encoded content)
 *
 * Import: POST /ureport/backup/import (multipart: file + conflictStrategy)
 *   conflictStrategy: "overwrite" | "rename"
 *   → { success, imported: [...], renamed: [...], skipped: [...] }
 *
 * Query: GET /ureport/backup/list
 *   → [ {name, size, updateTime}, ... ]
 */
public class BackupServletAction extends RenderPageServletAction {
	private static final ObjectMapper mapper = new ObjectMapper();
	private static final byte[] MAGIC = new byte[]{(byte)0x55, (byte)0x52, (byte)0x50, (byte)0x42}; // "URPB"

	@Override
	public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String method = retriveMethod(req);
		if ("export".equals(method)) {
			exportBackup(req, resp);
		} else if ("import".equals(method)) {
			importBackup(req, resp);
		} else if (method != null) {
			invokeMethod(method, req, resp);
		} else {
			list(req, resp);
		}
	}

	@Override
	public String url() {
		return "/backup";
	}

	/**
	 * GET /backup/list — list all report files available for export.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void list(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		Collection<ReportProvider> providers = applicationContext.getBeansOfType(ReportProvider.class).values();
		for (ReportProvider provider : providers) {
			if (provider.disabled()) continue;
			List<ReportFile> files = provider.getReportFiles();
			if (files == null) continue;
			for (ReportFile f : files) {
				Map<String, Object> item = new HashMap<String, Object>();
				item.put("name", f.getName());
				item.put("prefix", provider.getPrefix());
				item.put("fullName", provider.getPrefix() + f.getName());
				item.put("updateDate", f.getUpdateDate() != null ? f.getUpdateDate().getTime() : 0);
				result.add(item);
			}
		}
		writeObjectToJson(resp, result);
	}

	/**
	 * POST /backup/export
	 * Body: { names: ["file:r1.xml","file:r2.xml"] | "ALL" }
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void exportBackup(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String body = readBody(req);
		Map<String, Object> params = mapper.readValue(body, Map.class);

		// Collect report providers
		Collection<ReportProvider> providers = applicationContext.getBeansOfType(ReportProvider.class).values();

		// Build export list
		List<String[]> exportList = new ArrayList<String[]>();
		Object namesObj = params.get("names");

		if ("ALL".equals(namesObj)) {
			for (ReportProvider provider : providers) {
				if (provider.disabled()) continue;
				List<ReportFile> files = provider.getReportFiles();
				if (files == null) continue;
				for (ReportFile f : files) {
					exportList.add(new String[]{provider.getPrefix(), f.getName()});
				}
			}
		} else if (namesObj instanceof List) {
			List<String> names = (List<String>) namesObj;
			for (String fullName : names) {
				for (ReportProvider provider : providers) {
					if (provider.disabled()) continue;
					String prefix = provider.getPrefix();
					if (fullName.startsWith(prefix)) {
						String name = fullName.substring(prefix.length());
						exportList.add(new String[]{prefix, name});
						break;
					}
				}
			}
		}

		if (exportList.isEmpty()) {
			Map<String, Object> err = new HashMap<String, Object>();
			err.put("error", "没有可导出的报表");
			writeObjectToJson(resp, err);
			return;
		}

		// Build backup ZIP
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ZipOutputStream zos = new ZipOutputStream(baos);

		for (String[] item : exportList) {
			String prefix = item[0];
			String name = item[1];
			for (ReportProvider provider : providers) {
				if (provider.getPrefix().equals(prefix)) {
					InputStream is = null;
					try {
						is = provider.loadReport(name);
						if (is != null) {
							byte[] rawBytes = readAll(is);
							// Base64 encode to make it non-human-readable in ZIP
							String encoded = Base64.getEncoder().encodeToString(rawBytes);
							ZipEntry entry = new ZipEntry(name);
							zos.putNextEntry(entry);
							zos.write(encoded.getBytes("UTF-8"));
							zos.closeEntry();
						}
					} catch (Exception ignored) {
					} finally {
						if (is != null) try { is.close(); } catch (Exception ignored) {}
					}
					break;
				}
			}
		}
		zos.finish();
		zos.close();

		// Write response with custom magic header
		resp.setContentType("application/octet-stream");
		resp.setHeader("Content-Disposition", "attachment; filename=\"ureportplus-backup.ureportplus.bak\"");
		OutputStream out = resp.getOutputStream();
		out.write(MAGIC); // magic bytes
		out.write(baos.toByteArray());
		out.flush();
	}

	/**
	 * POST /backup/import
	 * Multipart: file (the .bak file), conflictStrategy ("overwrite" | "rename")
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void importBackup(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			DiskFileItemFactory factory = new DiskFileItemFactory();
			ServletFileUpload upload = new ServletFileUpload(factory);
			List<FileItem> items = upload.parseRequest(req);

			byte[] backupData = null;
			String conflictStrategy = "rename";

			for (FileItem item : items) {
				if (item.isFormField()) {
					if ("conflictStrategy".equals(item.getFieldName())) {
						conflictStrategy = item.getString("UTF-8");
					}
				} else {
					backupData = item.get();
				}
			}

			if (backupData == null || backupData.length < MAGIC.length) {
				writeJson(resp, false, "无效的备份文件", null, null);
				return;
			}

			// Verify magic header
			for (int i = 0; i < MAGIC.length; i++) {
				if (backupData[i] != MAGIC[i]) {
					writeJson(resp, false, "无效的备份文件格式", null, null);
					return;
				}
			}

			// Extract ZIP content (skip magic bytes)
			ByteArrayInputStream bais = new ByteArrayInputStream(backupData, MAGIC.length, backupData.length - MAGIC.length);
			ZipInputStream zis = new ZipInputStream(bais);

			Collection<ReportProvider> providers = applicationContext.getBeansOfType(ReportProvider.class).values();
			List<String> imported = new ArrayList<String>();
			List<String> renamed = new ArrayList<String>();
			List<String> skipped = new ArrayList<String>();

			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String originalName = entry.getName();
				if (originalName == null || originalName.isEmpty()) continue;

				byte[] encodedBytes = readAll(zis);
				byte[] xmlBytes;
				try {
					xmlBytes = Base64.getDecoder().decode(new String(encodedBytes, "UTF-8").trim());
				} catch (Exception e) {
					skipped.add(originalName + " (解码失败)");
					continue;
				}

				String saveName = originalName;
				boolean exists = false;

				// Check if file exists
				for (ReportProvider provider : providers) {
					if (provider.disabled()) continue;
					try {
						InputStream check = provider.loadReport(originalName);
						if (check != null) {
							exists = true;
							check.close();
						}
					} catch (Exception ignored) {}
					if (exists) break;
				}

				if (exists) {
					if ("overwrite".equals(conflictStrategy)) {
						// Delete existing first
						for (ReportProvider provider : providers) {
							if (!provider.disabled()) {
								try { provider.deleteReport(provider.getPrefix() + originalName); } catch (Exception ignored) {}
							}
						}
					} else {
						// Rename: append _copy before .ureportplus.xml
						if (saveName.endsWith(".ureportplus.xml")) {
							saveName = saveName.replace(".ureportplus.xml", "_copy.ureportplus.xml");
						} else {
							saveName = saveName + "_copy";
						}
						renamed.add(originalName + " → " + saveName);
					}
				}

				// Save
				boolean saved = false;
				for (ReportProvider provider : providers) {
					if (provider.disabled()) continue;
					try {
						provider.saveReport(saveName, new String(xmlBytes, "UTF-8"));
						saved = true;
						break;
					} catch (Exception ignored) {}
				}

				if (saved) {
					boolean wasRenamed = false;
					for (String r : renamed) {
						if (r.startsWith(originalName)) { wasRenamed = true; break; }
					}
					if (!wasRenamed) {
						imported.add(saveName);
					}
				} else {
					skipped.add(originalName + " (保存失败)");
				}
			}
			zis.close();

			writeJson(resp, true, null, imported, renamed, skipped);
		} catch (Exception e) {
			writeJson(resp, false, "导入失败: " + e.getMessage(), null, null);
		}
	}

	private void writeJson(HttpServletResponse resp, boolean success, String error,
			List<String> imported, List<String> renamed) throws IOException, ServletException {
		writeJson(resp, success, error, imported, renamed, new ArrayList<String>());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void writeJson(HttpServletResponse resp, boolean success, String error,
			List<String> imported, List<String> renamed, List<String> skipped) throws IOException, ServletException {
		Map<String, Object> result = new HashMap<String, Object>();
		result.put("success", success);
		if (error != null) result.put("error", error);
		if (imported != null) result.put("imported", imported);
		if (renamed != null) result.put("renamed", renamed);
		if (skipped != null) result.put("skipped", skipped);
		writeObjectToJson(resp, result);
	}

	private byte[] readAll(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] data = new byte[4096];
		int n;
		while ((n = is.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, n);
		}
		return buffer.toByteArray();
	}

	private String readBody(HttpServletRequest req) throws IOException {
		StringBuilder sb = new StringBuilder();
		java.io.BufferedReader reader = req.getReader();
		String line;
		while ((line = reader.readLine()) != null) sb.append(line);
		return sb.toString();
	}
}
