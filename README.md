<p align="center">
  <img src="docs/images/ureportPlusLogo.svg" alt="UReportPlus" width="200"/>
</p>

<h1 align="center">UReportPlus</h1>

<p align="center">
  <strong>High-performance pure-Java Chinese-style reporting engine</strong>
  <br/>
  Visual Web Designer · Multi-format Export · Open Source & Free
</p>

<p align="center">
  🇬🇧 English &nbsp;|&nbsp; <a href="README-zh_CN.md">🇨🇳 中文</a>
</p>

<p align="center">
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License"/></a>
  <a href="https://central.sonatype.com/artifact/com.kingint.ureportplus/ureportplus-console"><img src="https://img.shields.io/maven-central/v/com.kingint.ureportplus/ureportplus-console?label=Maven%20Central&logo=apache-maven" alt="Maven Central"/></a>
  <a href="#"><img src="https://img.shields.io/badge/java-1.7%2B-orange" alt="Java"/></a>
  <a href="https://gitee.com/lg10/ureport-plus"><img src="https://img.shields.io/badge/gitee-ureport--plus-red" alt="Gitee"/></a>
</p>

---

## 📖 Table of Contents

- [Introduction](#-introduction)
- [Screenshots](#-screenshots)
- [Integration](#-integration)
- [Features](#-features)
- [Quick Start](#-quick-start)
- [Configuration](#-configuration)
- [Documentation](#-documentation)
- [Extension Development (SPI)](#-extension-development-spi)
- [Version History](#-version-history)
- [FAQ](#-faq)

---

## 📌 Introduction

**UReportPlus** is a new-generation Chinese-style reporting engine built on Spring. It carries on the design philosophy of UReport2 with a comprehensive upgrade. You can design, preview and export complex reports right in your browser — no coding required, fully visual.

### Why UReportPlus

| Aspect | Traditional BI Tools | UReportPlus |
| :--- | :--- | :--- |
| Deployment | Dedicated server, heavy | **Embedded JAR**, lightweight |
| Report design | Desktop client | **Browser-based visual designer** |
| License | Commercial / GPL | **Apache 2.0**, fully open |
| Extensibility | Limited plugins | **SPI interfaces** — customize storage / auth / image sources |
| Expressions | Proprietary syntax | **ANTLR4-based**, JavaScript-like syntax |
| Customization | Black box | Pure Java source, deeply customizable |

### Typical Use Cases

- 🏨 Hotel industry: daily / monthly / yearly revenue analysis
- 🏭 Manufacturing: production reports / quality inspection reports
- 💰 Finance: income statement, balance sheet, cash flow statement
- 📊 Data dashboards: cross tables, grouped summaries, YoY / MoM comparison
- 🧾 Document printing: invoices, receipts, vouchers (pixel-accurate PDF layout)

---

## 📸 Screenshots

| Report Console | Visual Designer |
| :---: | :---: |
| <img src="docs/images/screenshot-console.png" alt="Report console" width="460"/> | <img src="docs/images/screenshot-designer.png" alt="Visual designer" width="460"/> |

<p align="center">
  <img src="docs/images/screenshot-preview.png" alt="HTML preview of a designed report" width="640"/>
  <br/>
  <sub>HTML preview of a report designed in the browser</sub>
</p>

---

## 🔧 Integration

### Maven Dependency

```xml
<!-- Recommended: console module, transitive dependencies managed by Maven -->
<dependency>
    <groupId>com.kingint.ureportplus</groupId>
    <artifactId>ureportplus-console</artifactId>
    <version>1.0.8</version>
</dependency>
```

```xml
<!-- Alternative: Fat JAR (all dependencies bundled, ~80MB) -->
<dependency>
    <groupId>com.kingint.ureportplus</groupId>
    <artifactId>ureportplus-all</artifactId>
    <version>1.0.8</version>
</dependency>
```

> **Which one?** Use `console` in production for precise dependency control; use `all` for quick prototypes to avoid dependency conflicts. The latest version is always shown in the Maven Central badge above.

### Gradle

```groovy
implementation 'com.kingint.ureportplus:ureportplus-console:1.0.8'
```

### Spring Boot Integration (Recommended)

Works with Spring Boot 2.x (javax.servlet based — see the [FAQ](#-faq) for Spring Boot 3.x).

```java
package com.example;

import com.kingint.ureportplus.console.UReportPlusServlet;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
@ImportResource("classpath:ureportplus-console-context.xml")
public class ReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportApplication.class, args);
    }

    @Bean
    public ServletRegistrationBean<UReportPlusServlet> ureportServlet() {
        ServletRegistrationBean<UReportPlusServlet> reg =
            new ServletRegistrationBean<>(new UReportPlusServlet(), "/ureport/*");
        reg.setLoadOnStartup(1);
        return reg;
    }
}
```

```properties
# application.properties - minimal configuration
ureportplus.fileStoreDir=/opt/reports
```

### Traditional web.xml

```xml
<!-- 1. Servlet -->
<servlet>
    <servlet-name>ureportplusServlet</servlet-name>
    <servlet-class>com.kingint.ureportplus.console.UReportPlusServlet</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>ureportplusServlet</servlet-name>
    <url-pattern>/ureport/*</url-pattern>         <!-- Must be /ureport/* -->
</servlet-mapping>

<!-- 2. Spring context (only if you don't use Spring yet) -->
<listener>
    <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>
<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>classpath:ureportplus-console-context.xml</param-value>
</context-param>
```

If your project already uses Spring, import it into your existing configuration instead:

```xml
<import resource="classpath:ureportplus-console-context.xml"/>
```

---

## ✨ Features

<table>
  <tr>
    <td width="50%">
      <h4>🎨 Visual Report Designer</h4>
      <p>Professional web designer based on Handsontable. Supports merged cells, conditional styles, multi-level headers and diagonal headers. Excel-like experience with undo/redo (100 steps).</p>
    </td>
    <td width="50%">
      <h4>📊 Complex Chinese-style Reports</h4>
      <p>Native support for cross tables, grouped tables, detail tables and sliced tables. Parent-child cell dependency tree drives iterative expansion, with automatic grouping, subtotals and grand totals.</p>
    </td>
  </tr>
  <tr>
    <td>
      <h4>📤 Multi-format Export</h4>
      <p>One-click export to <strong>PDF</strong> (pixel-accurate layout), <strong>Excel</strong> (.xlsx / .xls, page/sheet splitting), <strong>Word</strong> (.docx) and <strong>HTML</strong> (online preview). PDF ships with built-in Chinese fonts, zero configuration.</p>
    </td>
    <td>
      <h4>🗄️ Multiple Data Sources</h4>
      <p>Direct JDBC connections (MySQL / Oracle / PostgreSQL / H2...), Spring Bean injection, built-in demo datasets. Dual mode: SQL queries and visual datasets.</p>
    </td>
  </tr>
  <tr>
    <td>
      <h4>🧮 Powerful Expression Engine</h4>
      <p>ANTLR4-driven DSL. Supports cell references (relative/absolute), ternary expressions, if/else/case conditionals and 40+ built-in functions. Includes formal Chinese numeral conversion (<code>chn</code> / <code>chnMoney</code>).</p>
    </td>
    <td>
      <h4>📈 Charts & Barcodes</h4>
      <p>10 built-in chart types (bar, line, pie, area, radar, scatter, bubble and more). Barcode and QR code generation via ZXing.</p>
    </td>
  </tr>
  <tr>
    <td>
      <h4>🔌 SPI Extensibility</h4>
      <p>Custom report storage (local files / database / OSS / S3), custom image loading (local / HTTP / HTTPS), custom authorization checks. All via standard Spring Bean injection.</p>
    </td>
    <td>
      <h4>🤖 AI Assistant</h4>
      <p>Optional AI features: describe your needs in natural language → report templates generated automatically. Supports OpenAI-compatible APIs. Model, temperature and token limits are configurable.</p>
    </td>
  </tr>
  <tr>
    <td>
      <h4>📐 Query Forms</h4>
      <p>Visually design query parameter forms. Supports text inputs, dropdowns, date pickers, radio buttons, checkboxes and submit/reset buttons. Parameters are used in expressions via the <code>param()</code> function.</p>
    </td>
    <td>
      <h4>🔐 Access Control</h4>
      <p>Console login authentication. Integrate your existing enterprise permission systems (LDAP / OAuth / SSO) through SPI interfaces. The designer can be disabled in production.</p>
    </td>
  </tr>
</table>

---

## 🚀 Quick Start

### Prerequisites

- JDK 1.7+ (JDK 8 recommended)
- Maven 3.0+
- A modern browser (Chrome / Firefox / Edge)

### Option A: Add to your application (fastest)

1. Create a Spring Boot 2.x application and add the Maven dependency shown in [Integration](#-integration).
2. Register the servlet with the configuration class above (~10 lines of code).
3. Start your application and open **http://localhost:8080/ureport/designer** 🎉

### Option B: Build from source

```bash
# 1. Clone the project
git clone https://gitee.com/lg10/ureport-plus.git
cd ureport-plus

# 2. Build all modules into your local repository
for m in ureportplus-parent ureportplus-core ureportplus-font ureportplus-console ureportplus-all; do
  mvn -f "$m/pom.xml" clean install -DskipTests
done
```

Then integrate the built `ureportplus-console` artifact into your application as in Option A.

---

## ⚙️ Configuration

### Core Settings

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `ureportplus.fileStoreDir` | String | `ureportfiles/` | Report file storage directory (use an absolute path in production) |
| `ureportplus.disableFileProvider` | boolean | `false` | Set `true` to disable file-system storage |
| `ureportplus.disableHttpSessionReportCache` | boolean | `false` | Disable session-level report cache |
| `ureportplus.debug` | boolean | `false` | Debug mode (verbose logging + cache disabled) |
| `ureportplus.disableDesigner` | boolean | `false` | Disable the designer entry point in production |

### AI Assistant Settings

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `ureportplus.ai.enabled` | boolean | `false` | Enable AI report generation |
| `ureportplus.ai.provider` | String | `openai` | AI provider identifier |
| `ureportplus.ai.api-key` | String | — | API key |
| `ureportplus.ai.api-url` | String | `https://api.openai.com/v1` | API endpoint |
| `ureportplus.ai.model` | String | `gpt-4o` | Model name |
| `ureportplus.ai.max-tokens` | int | `4096` | Max tokens |
| `ureportplus.ai.temperature` | double | `0.3` | Generation temperature (0-2) |
| `ureportplus.ai.max-retries` | int | `2` | Retry count on failure |

### Console Authentication

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `ureportplus.console.username` | String | — | Setting this enables console login |
| `ureportplus.console.password` | String | — | Login password |

---

## 📚 Documentation

In-depth references live in [`docs/`](docs/):

| Topic | Document |
| :--- | :--- |
| 🧮 Expression language: syntax, cell references, conditionals and 40+ built-in functions | [EXPRESSION-LANGUAGE.md](docs/EXPRESSION-LANGUAGE.md) |
| 📄 Report XML format: full annotated `.ureportplus.xml` example + cell attributes | [REPORT-XML.md](docs/REPORT-XML.md) |
| 🌐 URL reference: designer, preview, export and API endpoints | [URL-REFERENCE.md](docs/URL-REFERENCE.md) |
| 🏗 Architecture & tech stack: module layout, report lifecycle, dependencies | [ARCHITECTURE.md](docs/ARCHITECTURE.md) |

---

## 🔌 Extension Development (SPI)

### ReportProvider — Custom Report Storage

```java
public interface ReportProvider {
    InputStream loadReport(String file);   // Load a report
    void saveReport(String file, String content);  // Save a report
    void deleteReport(String file);         // Delete a report
    List<ReportFile> getReportFiles();      // List files
    String getName();                       // Display name
    String getPrefix();                     // URL prefix
    boolean disabled();                     // Whether disabled
}
```

**Example: database storage**

```java
@Component
public class DatabaseReportProvider implements ReportProvider {
    @Override
    public InputStream loadReport(String file) {
        // SELECT content FROM reports WHERE name = ?
    }
    @Override
    public void saveReport(String file, String content) {
        // INSERT INTO reports ... ON DUPLICATE KEY UPDATE
    }
    // ... other methods
}
```

Implement the interface and register it as a Spring Bean — no changes to UReportPlus source code required.

### ImageProvider — Custom Image Sources

```java
public interface ImageProvider {
    InputStream getImage(String path);  // Get the image stream
    boolean support(String path);       // Whether this path is supported
}
```

Built-in implementations:
- `DefaultImageProvider` — local files
- `HttpImageProvider` / `HttpsImageProvider` — remote images

### ReportAuthCheck — Authorization Checks

```java
public interface ReportAuthCheck {
    boolean isPermitted(String action, String reportFile);
}
```

`action` values include: `designer`, `preview`, `pdf`, `excel`, `word`, `delete`, `save`

---

## 📋 Version History

Latest release: **v1.0.8** (2026-09-03) — full release notes in [VERSION_HISTORY.md](VERSION_HISTORY.md).

---

## ❓ FAQ

<details>
<summary><b>Can I change the /ureport path?</b></summary>

No. `/ureport/*` is a hard-coded prefix for the Servlet's internal routing. Changing it breaks routing for all sub-paths (`/designer`, `/preview`, etc.).
</details>

<details>
<summary><b>Does it work with Spring Boot 3.x?</b></summary>

Partially. The engine is built on Spring 4.x + javax.servlet, while Spring Boot 3.x uses Jakarta EE. Use the `ureportplus-all` Fat JAR to avoid class-loading conflicts, or watch for a future Jakarta migration release.
</details>

<details>
<summary><b>How do I display database images in a report?</b></summary>

Implement the `ImageProvider` SPI interface, read the BLOB from your database and return it as an `InputStream`. Then point a report cell's image URL at your provider's path.
</details>

<details>
<summary><b>How do I disable the designer for production safety?</b></summary>

```properties
ureportplus.disableDesigner=true
```
</details>

<details>
<summary><b>Is Internet Explorer supported?</b></summary>

No. The designer relies on modern browser APIs (Canvas, ES6, CSS Grid). Chrome, Firefox and Edge are recommended.
</details>

<details>
<summary><b>Can report files be stored in a database?</b></summary>

Yes. Implement the `ReportProvider` interface to read/write your database. See the [Extension Development](#-extension-development-spi) section.
</details>

<details>
<summary><b>Can data source passwords be stored encrypted?</b></summary>

Yes. Data source passwords are injected via Spring property placeholders, so you can wrap the sensitive values in `ureportplus.properties` with Jasypt or a similar encryption scheme.
</details>

---

## 📎 Resources

| Document | Link |
| :--- | :--- |
| Version History | [VERSION_HISTORY.md](VERSION_HISTORY.md) |
| Expression Language & Built-in Functions | [docs/EXPRESSION-LANGUAGE.md](docs/EXPRESSION-LANGUAGE.md) |
| Report XML Format | [docs/REPORT-XML.md](docs/REPORT-XML.md) |
| URL Reference | [docs/URL-REFERENCE.md](docs/URL-REFERENCE.md) |
| Architecture & Tech Stack | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Report Storage & Data Source Configuration | [docs/STORAGE-DATASOURCE.md](docs/STORAGE-DATASOURCE.md) |
| Report Calculation Model in Depth | [docs/REPORT-MODEL.md](docs/REPORT-MODEL.md) |
| UReport2 2.x Changelog (legacy, 2017) | [CHANGELOG.md](CHANGELOG.md) |
| Issue Template | [ISSUE_TEMPLATE.md](ISSUE_TEMPLATE.md) |

---

## 🤝 Contributing

Issues and Pull Requests are welcome!

1. Fork this repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)

Copyright © 2017-2026 **KinginT** · Powered by [Gitee](https://gitee.com/lg10/ureport-plus)
