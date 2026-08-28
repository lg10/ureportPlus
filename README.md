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
  <a href="https://central.sonatype.com/"><img src="https://img.shields.io/badge/maven--central-v1.0.6-blue" alt="Maven Central"/></a>
  <a href="#"><img src="https://img.shields.io/badge/java-1.7%2B-orange" alt="Java"/></a>
  <a href="https://gitee.com/lg10/ureport-plus"><img src="https://img.shields.io/badge/gitee-ureport--plus-red" alt="Gitee"/></a>
</p>

---

## 📖 Table of Contents

- [Introduction](#-introduction)
- [Integration](#-integration)
- [Features](#-features)
- [Quick Start](#-quick-start)
- [Configuration](#-configuration)
- [Expression Language](#-expression-language)
- [Built-in Functions](#-built-in-functions)
- [Report XML Format](#-report-xml-format)
- [URL Reference](#-url-reference)
- [Extension Development (SPI)](#-extension-development-spi)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
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

## 🔧 Integration

### Maven Dependency

```xml
<!-- Recommended: console module, transitive dependencies managed by Maven -->
<dependency>
    <groupId>com.kingint.ureportplus</groupId>
    <artifactId>ureportplus-console</artifactId>
    <version>1.0.6</version>
</dependency>
```

```xml
<!-- Alternative: Fat JAR (all dependencies bundled, ~80MB) -->
<dependency>
    <groupId>com.kingint.ureportplus</groupId>
    <artifactId>ureportplus-all</artifactId>
    <version>1.0.6</version>
</dependency>
```

> **Which one?** Use `console` in production for precise dependency control; use `all` for quick prototypes to avoid dependency conflicts.

### Gradle

```groovy
implementation 'com.kingint.ureportplus:ureportplus-console:1.0.6'
```

### Spring Boot Integration (Recommended)

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

- JDK 1.7+
- Maven 3.0+

### Up and Running in One Minute

```bash
# 1. Clone the project
git clone https://gitee.com/lg10/ureport-plus.git
cd ureport-plus

# 2. Build and install to your local repository
cd ureportplus-parent
mvn clean install -DskipTests

# 3. Start the demo application
cd ../../test-app
mvn spring-boot:run
```

Open **http://localhost:8080/ureport/designer** in your browser 🎉

> The demo ships with an H2 database containing hotel revenue sample data. Console login: `admin` / `admin`

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

## 🧮 Expression Language

UReportPlus uses a custom ANTLR4-based expression engine. Its syntax follows mainstream programming language conventions, so the learning curve is minimal.

### Data Types

| Type | Examples | Description |
| :--- | :--- | :--- |
| Number | `1`, `3.14`, `-20` | Integer or decimal |
| String | `'hello'`, `"world"` | Single or double quotes |
| Boolean | `true`, `false` | — |

### Operators

| Operator | Example | Result |
| :--- | :--- | :--- |
| `+` | `21 + 31` | `52` |
| `+` | `"Value:" + 331` | `"Value:331"` |
| `-` | `21 - 31` | `-10` |
| `*` | `3 * 6` | `18` |
| `/` | `6 / 3` | `2` |
| `%` | `5 % 3` | `2` |

### Cell References

Cell references in expressions are evaluated **relative to the current cell** — a key feature of Chinese-style report engines.

| Syntax | Meaning | Example |
| :--- | :--- | :--- |
| `A1` | Relative reference: resolved along the parent-child tree | `A1 * 0.13` |
| `&A1` | Absolute reference: always points to A1 | `&A1` |
| `$A1` | Row-relative, column-absolute | `$A1 + B1` |

```javascript
// Real scenario: compute tax for each detail row
B1 * 0.13                          // B1 is the amount, 13% tax rate

// Real scenario: aggregate child cells
sum(C1)                            // Sum over all child cells C1
```

### Conditionals

#### Ternary Expression

```
condition ? trueValue : falseValue
```

```javascript
A1 > 1000 ? "Normal" : "Low"
A1 > 1000 && A1 < 20000 ? "Moderate" : "Adjusted:" + (A1 + 100)
```

#### If / Else If / Else

```javascript
if (A1 > 1000) {
    return "High"
} else if (A1 > 500) {
    return "Medium"
} else {
    return "Low"
}
```

#### Case Expression

```javascript
case {
    A1 == 100  return "Exact match",
    A1 > 100 && A1 < 1000  return "Normal range",
    A1 >= 1000  return "Out of range"
}
```

### Variables and Return

```javascript
// Define variables
var total = ds.sum(revenue);
var tax = total * 0.13;

// Return the final value
return total - tax;
```

---

## 📚 Built-in Functions

### Dataset Aggregation

Statistical calculations over SQL query result sets.

| Function | Syntax | Description |
| :--- | :--- | :--- |
| `sum` | `ds.sum(field)` | Sum |
| `avg` | `ds.avg(field)` | Average |
| `count` | `ds.count(field)` | Count |
| `max` | `ds.max(field)` | Maximum |
| `min` | `ds.min(field)` | Minimum |
| `list` | `ds.list(field)` | Comma-separated list |
| `order` | `ds.order(field)` | Ordered list |

### Math Functions

`abs(n)` · `ceil(n)` · `floor(n)` · `round(n, precision)` · `pow(n, exp)` · `sqrt(n)` · `exp(n)` · `log(n)` · `log10(n)` · `sin(n)` · `cos(n)` · `tan(n)` · `random()` · `median(field)` · `mode(field)` · `stdevp(field)` · `vara(field)`

### String Functions

`length(s)` · `lower(s)` · `upper(s)` · `trim(s)` · `substring(s, begin, end)` · `replace(s, old, new)` · `indexOf(s, sub)`

### Date Functions

`date(year, month, day)` · `day(date)` · `month(date)` · `year(date)` · `week(date)` · `formatDate(date, pattern)`

### Pagination Functions (for printing)

| Function | Description |
| :--- | :--- |
| `page()` | Current page number |
| `pages()` | Total pages |
| `pageSum(field)` | Sum on current page |
| `pageAvg(field)` | Average on current page |
| `pageMax(field)` / `pageMin(field)` | Max/min on current page |
| `pageCount(field)` | Count on current page |
| `pageRows()` | Row count on current page |

### Utility Functions

| Function | Description | Example |
| :--- | :--- | :--- |
| `row()` | Current row number (1-based) | `row()` |
| `column()` | Current column number (1-based) | `column()` |
| `param(name)` | Get a URL query parameter | `param("hotelId")` |
| `param(name, default)` | Parameter with default value | `param("year", "2025")` |
| `json(path)` | Parse JSON data | `json("data.items[0].name")` |
| `formatNumber(n, pattern)` | Number formatting | `formatNumber(12345.6, "#,##0.00")` → `12,345.60` |
| `get(cell, index)` | Get the Nth value of a cell | `get(C1, 0)` |

### Chinese Formal Numeral Conversion

Converts amounts into formal Chinese numerals (used on invoices and checks):

```javascript
chn(12345.67)       // → "壹万贰仟叁佰肆拾伍元陆角柒分"
chnMoney(12345.67)  // → "壹万贰仟叁佰肆拾伍元陆角柒分"
```

---

## 📄 Report XML Format

Report definition files use the `.ureportplus.xml` extension. A complete example:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ureportplus xmlns="http://www.example.org/ureportplus"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <!-- ===== Data source: JDBC connection ===== -->
    <datasource name="demo" type="jdbc"
        driver="org.h2.Driver"
        url="jdbc:h2:mem:ureportplus_demo"
        username="sa" password=""/>

    <!-- ===== Dataset: SQL query ===== -->
    <dataset name="ds" type="sql" datasource="demo">
        <sql><![CDATA[
            SELECT hotel_name, room_type, revenue
            FROM hotel_revenue
            ORDER BY hotel_name
        ]]></sql>
    </dataset>

    <!-- ===== Row definitions ===== -->
    <row row-number="1" height="30"/>      <!-- Header row -->
    <row row-number="2" height="25"/>      <!-- Data row -->

    <!-- ===== Column definitions ===== -->
    <column col-number="1" width="120"/>   <!-- Hotel name column -->
    <column col-number="2" width="100"/>   <!-- Revenue column -->

    <!-- ===== Cell A1: static header ===== -->
    <cell row="1" col="1">
        <value>酒店名称</value>
        <cell-style font-size="14" bold="true"
            align="center" bgcolor="#f0f0f0"/>
    </cell>

    <!-- ===== Cell A2: bound to a dataset field ===== -->
    <cell row="2" col="1">
        <dataset-value ds-name="ds" property="hotel_name"/>
    </cell>

    <!-- ===== Cell B1: static header ===== -->
    <cell row="1" col="2">
        <value>营收金额</value>
        <cell-style font-size="14" bold="true"
            align="center" bgcolor="#f0f0f0"/>
    </cell>

    <!-- ===== Cell B2: expression ===== -->
    <cell row="2" col="2">
        <expression>ds.sum(revenue)</expression>
    </cell>

    <!-- ===== Paper settings ===== -->
    <paper type="A4" orientation="portrait"
        top-margin="20" bottom-margin="20"
        left-margin="20" right-margin="20"/>

</ureportplus>
```

### Cell Attributes Reference

| Attribute | Values | Description |
| :--- | :--- | :--- |
| `type` | `text` / `expression` / `dataset` | Cell type |
| `expand` | `none` / `down` / `right` | Expansion direction |
| `left-parent-cell` | cell name | Left parent cell |
| `top-parent-cell` | cell name | Top parent cell |
| `link-url` | URL expression | Hyperlink |
| `condition` | condition expression | Conditional style / conditional value |
| `format` | format pattern | e.g. `#,##0.00` |

---

## 🌐 URL Reference

All endpoints are prefixed with `/ureport` (the fixed Servlet mapping).

### Design & Preview

| URL | Description |
| :--- | :--- |
| `/ureport/` | Report management center (report list + example cards) |
| `/ureport/designer` | Visual designer (new blank report) |
| `/ureport/designer?_u=file:report.ureportplus.xml` | Open an existing report for editing |
| `/ureport/preview?_u=file:report.ureportplus.xml` | HTML online preview |
| `/ureport/searchForm` | Query form designer |

### Export

| URL | Description |
| :--- | :--- |
| `/ureport/pdf?_u=file:report.ureportplus.xml` | Export PDF |
| `/ureport/excel?_u=file:report.ureportplus.xml` | Export Excel (.xlsx) |
| `/ureport/excel97?_u=file:report.ureportplus.xml` | Export Excel 97 (.xls) |
| `/ureport/word?_u=file:report.ureportplus.xml` | Export Word (.docx) |

### Others

| URL | Description |
| :--- | :--- |
| `/ureport/datasource` | Data source management API |
| `/ureport/chart` | Chart rendering API |
| `/ureport/image` | Image resource loading |
| `/ureport/import` | Import Excel templates |
| `/ureport/version` | Version info (JSON) |
| `/ureport/ai/*` | AI assistant API |

> `_u` parameter format: `file:reportname.ureportplus.xml` (file system) or `classpath:reportname.ureportplus.xml` (classpath)

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

## 🏗 Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                         BROWSER                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │  Designer    │  │  Preview     │  │  Console (Manager)      │  │
│  │ Handsontable │  │ HTML Render  │  │ Report list · Edit      │  │
│  │ CodeMirror   │  │ Chart.js     │  │ Examples · Live search  │  │
│  │ Bootstrap 3  │  │ ZXing        │  │ Stats panel · Version   │  │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬────────────┘  │
└─────────┼──────────────────┼──────────────────────┼───────────────┘
          │ HTTP / JSON       │                      │
┌─────────▼───────────────────▼──────────────────────▼───────────────┐
│                    UReportPlusServlet                              │
│                 (single entry /ureport/*)                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐  │
│  │Designer  │ │ Preview  │ │  Export  │ │DataSource│ │  Auth  │  │
│  │Action    │ │ Action   │ │  Action  │ │ Action   │ │ Action │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬───┘  │
└───────┼─────────────┼────────────┼────────────┼────────────┼──────┘
        │             │            │            │            │
┌───────▼─────────────▼────────────▼────────────▼────────────▼──────┐
│                       CORE ENGINE                                 │
│                                                                   │
│  ┌──────────────┐   ┌──────────────────┐   ┌──────────────────┐  │
│  │ ReportParser │──▶│  ReportBuilder   │──▶│  ExportManager   │  │
│  │  (dom4j)     │   │  (Cell Expand)   │   │  (Producer SPI)  │  │
│  └──────────────┘   └────────┬─────────┘   └────────┬─────────┘  │
│                              │                       │            │
│              ┌───────────────▼───────────────┐       │            │
│              │     Expression Engine         │       │            │
│              │  ANTLR4 → AST → Eval          │       │            │
│              │  Functions · Operators · Vars │       │            │
│              └───────────────────────────────┘       │            │
│                                                      │            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐            │            │
│  │  Cache   │ │  Chart   │ │  Image   │            │            │
│  │  Utils   │ │  Plugin  │ │ Provider │            │            │
│  └──────────┘ └──────────┘ └──────────┘            │            │
│                                                     │            │
└─────────────────────────────────────────────────────┼────────────┘
                                                      │
                              ┌───────────────────────▼────────────┐
                              │        EXPORT FORMATS              │
                              │  HTML · PDF · Excel · Word         │
                              │  (iText 5) (POI 3) (POI XWPF)     │
                              └────────────────────────────────────┘
```

### Report Lifecycle

```
XML definition file (.ureportplus.xml)
    │
    ▼ dom4j parsing
ReportDefinition (static definition model)
    │
    ▼ ReportBuilder.buildReport()
Report (runtime model: Cell / Row / Column tree)
    │
    ▼ Parent-cell expansion + expression evaluation
Cell tree (each cell holds its computed value)
    │
    ▼ ExportManager.dispatch()
HTML / PDF / Excel / Word output
```

---

## 🛠 Tech Stack

### Backend

| Dependency | Version | Purpose |
| :--- | :--- | :--- |
| ANTLR4 Runtime | 4.9.3 | Expression parsing |
| dom4j | 1.6.1 | XML parsing |
| Spring Framework | 4.3.11 | DI / JDBC / Web |
| iText | 5.5.13 | PDF export |
| Apache POI | 3.16 | Excel + Word export |
| ZXing | 3.3.1 | Barcodes / QR codes |
| Velocity | 1.7 | HTML template rendering |
| Jackson | 1.9.11 | JSON processing |

### Frontend

| Library | Version | Purpose |
| :--- | :--- | :--- |
| Handsontable | 7.4.2 | Spreadsheet editor |
| CodeMirror | 5.58.2 | Expression code editor |
| Chart.js | 2.9.4 | Chart rendering |
| Bootstrap | 3.3.7 | UI framework |
| jQuery | 1.12 | DOM manipulation |

---

## 📋 Version History

### v1.0.6 (2026-08-27)

- 🚀 Official Maven Central release: `release.sh` completes build, signing, bundle upload and auto-publish in one command
- 📦 Polished release pipeline (GPG signing, sources/javadoc JARs, Central Portal status polling)

### v1.0.5 (2026-08-27)

- 🎨 Property panel redesigned
- 📤 Export enhancements
- 🗄️ Data source enhancements
- ⚠️ Never published to Maven Central — use v1.0.6 instead

### v1.0.4 (2026-07-31)

- 🐛 Fixed H2 built-in demo data source not being registered
- 🐛 Fixed PDF corruption when export fails
- 🐛 Fixed empty cells produced in merged regions during Excel export
- 🐛 Report file listing now skips directories

### v1.0.3 (2026-07-30)

- 🤖 Streaming multi-agent AI: real-time stage feedback, no more fake progress bars
- 💬 Multi-agent conversation memory and history, smart extraction of reasoning content
- 🎨 Commercial-grade property panel UI overhaul, draggable AI floating ball
- ⌨️ Ctrl+S / Cmd+S save shortcut + save status indicator in toolbar

### v1.0.1 (2026-07-30)

- 🐛 Fixed duplicated AI chat messages
- 💬 AI conversation memory with session persistence
- 🤖 AI can manipulate table structure (insert/delete rows & columns, merge, bands)
- 📚 AI learns from built-in example report patterns

### v1.0.0 (2026-07-28)

- 🎉 Full rebrand from UReport2 → UReportPlus
- 🎨 Brand-new console UI: modern SaaS style + frosted-glass navbar
- 📊 Console stats panel: report count, examples, storage providers, version
- 🔍 Real-time search filtering by report name
- 🕐 Report created / updated timestamps
- 🚀 Maven Central publishing support
- 📖 Full bilingual README documentation

<details>
<summary>UReport2 legacy versions</summary>

- v2.7.2 — Example reports bundled into the JAR, auto-copied on startup
- v2.7.1 — UI polish, inline editing, group subtotals/grand totals, AI multi-agent assistant, console login, 11 example reports
- v2.6.4 — Core feature set: designer, export, expression engine, charts

</details>

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
| Report Storage & Data Source Configuration | [STORAGE-DATASOURCE.md](docs/STORAGE-DATASOURCE.md) |
| Report Calculation Model in Depth | [REPORT-MODEL.md](docs/REPORT-MODEL.md) |
| Expression Language Reference | [EXPRESSION.md](docs/EXPRESSION.md) |
| Changelog | [CHANGELOG.md](CHANGELOG.md) |
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
