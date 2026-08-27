package com.kingint.ureportplus.console;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * On startup, copies built-in example reports from classpath to the
 * configured ureportplus.fileStoreDir so they are available via FileReportProvider.
 */
public class ExampleInitializer implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {
	private static final Logger log = LoggerFactory.getLogger(ExampleInitializer.class);
	private ApplicationContext applicationContext;
	private boolean copied = false;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		if (copied || applicationContext == null) return;
		copied = true;

		try {
			String fileStoreDir = getFileStoreDir();
			if (fileStoreDir == null || fileStoreDir.isEmpty()) {
				fileStoreDir = System.getProperty("user.dir") + File.separator + "ureportfiles";
			}
			Path targetDir = Paths.get(fileStoreDir, "examples");
			Files.createDirectories(targetDir);

			String[] examples = {
				"example01-simple-table", "example02-vertical-group",
				"example03-horizontal-group", "example04-expressions",
				"example05-cross-tab", "example06-more-expressions",
				"example07-url-params", "example08-page-functions",
				"example09-conditional-format", "example10-cross-group",
				"example11-group-detail", "example12-receipt-bill"
			};

			int count = 0;
			for (String name : examples) {
				String resourcePath = "examples/" + name + ".ureportplus.xml";
				InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath);
				if (in == null) continue;

				Path targetFile = targetDir.resolve(name + ".ureportplus.xml");
				Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
				in.close();
				count++;
			}
			log.info("[ExampleInit] Copied {} example reports to {}", count, targetDir);
		} catch (Exception e) {
			log.warn("[ExampleInit] Failed to copy examples: {}", e.getMessage());
		}
	}

	private String getFileStoreDir() {
		try {
			return applicationContext.getEnvironment().getProperty("ureportplus.fileStoreDir");
		} catch (Exception e) {
			return null;
		}
	}
}
