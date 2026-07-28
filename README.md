# UReportPlus

> High-performance pure Java Chinese-style report engine · Visual web designer · Multi-format export

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/badge/maven--central-v1.0.0-blue)](https://central.sonatype.com/)
[![Java](https://img.shields.io/badge/java-1.7%2B-orange)](#)

UReportPlus is a pure Java report engine built on Spring. Design complex Chinese-style reports entirely in a browser — no coding required. Supports multi-source data, iterative cell expansion, and export to HTML / PDF / Excel / Word.

---

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [Integration Guide](#integration-guide)
  - [Maven Dependency](#maven-dependency)
  - [Gradle Dependency](#gradle-dependency)
  - [Spring Boot Configuration](#spring-boot-configuration)
  - [Traditional web.xml](#traditional-webxml)
- [Configuration Reference](#configuration-reference)
- [Expression Language](#expression-language)
  - [Data Types & Operators](#data-types--operators)
  - [Cell References](#cell-references)
  - [Conditional Expressions](#conditional-expressions)
  - [Built-in Functions](#built-in-functions)
- [Report XML Format](#report-xml-format)
- [URL Reference](#url-reference)
- [SPI Extension Points](#spi-extension-points)
- [Architecture](#architecture)
- [FAQ](#faq)

---

## Features

- 🎨 **Visual Report Designer** — Drag-and-drop, Excel-like grid, runs in Chrome / Firefox / Edge
- 📊 **Chinese-Style Reports** — Cross-tabs, grouped headers, merged cells, subtotals, page functions
- 🔄 **Iterative Cell Expansion** — Parent-child cell dependency tree auto-expands with dataset rows
- 📤 **Multi-Format Export** — HTML preview, PDF, Excel (.xlsx / .xls), Word (.docx)
- 🗄️ **Multi-Source Data** — JDBC / SQL queries, Spring Beans, built-in datasets, parameters
- 📈 **Charts** — Bar, Line, Pie, Area, Doughnut, Radar, Scatter, Bubble, Mix (Chart.js)
- 🏷️ **Barcode / QR Code** — Built-in ZXing integration
- 🔌 **SPI Extensible** — Custom report storage, image providers, auth checks
- 📐 **Expression Engine** — ANTLR4-based DSL with 40+ built-in functions
- 🤖 **AI Assistant** — Optional AI-powered report generation (OpenAI compatible)

---

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.kingint.ureportplus</groupId>
    <artifactId>ureportplus-console</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Spring Boot Configuration

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
public class Application {

    @Bean
    public ServletRegistrationBean<UReportPlusServlet> ureportplusServlet() {
        return new ServletRegistrationBean<>(
            new UReportPlusServlet(), "/ureport/*"
        );
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. Configure

```properties
# application.properties
ureportplus.fileStoreDir=/opt/ureport/reports
ureportplus.disableFileProvider=false
ureportplus.debug=false
```

### 4. Run

```
http://localhost:8080/ureport/designer
```

---

## Integration Guide

### Maven Dependency

```xml
<!-- Option A: Console module (recommended) — Maven resolves transitive deps -->
<dependency>
    <groupId>com.kingint.ureportplus</groupId>
    <artifactId>ureportplus-console</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Option B: Fat JAR (all-in-one) — zero dependency pain -->
<dependency>
    <groupId>com.kingint.ureportplus</groupId>
    <artifactId>ureportplus-all</artifactId>
    <version>1.0.0</version>
</dependency>
```

> **Which to choose?** Use `console` for production — you control dependency versions. Use `all` for quick demos or when dependency conflicts are unmanageable.

### Gradle Dependency

```groovy
dependencies {
    implementation 'com.kingint.ureportplus:ureportplus-console:1.0.0'
}
```

### Spring Boot Configuration

```java
@Bean
public ServletRegistrationBean<UReportPlusServlet> ureportplusServlet() {
    ServletRegistrationBean<UReportPlusServlet> bean = 
        new ServletRegistrationBean<>(new UReportPlusServlet(), "/ureport/*");
    bean.setLoadOnStartup(1);
    return bean;
}
```

> ⚠️ **Important**: The URL pattern **must** be `/ureport/*` — this is the fixed servlet mapping used by the console.

### Traditional web.xml

```xml
<servlet>
    <servlet-name>ureportplusServlet</servlet-name>
    <servlet-class>com.kingint.ureportplus.console.UReportPlusServlet</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>ureportplusServlet</servlet-name>
    <url-pattern>/ureport/*</url-pattern>
</servlet-mapping>
```

#### Load Spring Context

If your project already uses Spring, import the config in your existing context:

```xml
<import resource="classpath:ureportplus-console-context.xml"/>
```

If not using Spring, use `ContextLoaderListener`:

```xml
<listener>
    <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>
<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>classpath:ureportplus-console-context.xml</param-value>
</context-param>
```

---

## Configuration Reference

Create `application.properties` (Spring Boot) or `config.properties` (traditional):

| Property | Default | Description |
|---|---|---|
| `ureportplus.fileStoreDir` | `ureportfiles/` | Report file storage directory (absolute path recommended) |
| `ureportplus.disableFileProvider` | `false` | Set `true` to disable file-system report storage |
| `ureportplus.disableHttpSessionReportCache` | `false` | Disable session-based report caching |
| `ureportplus.debug` | `false` | Enable debug mode (verbose logging, no cache) |
| `ureportplus.disableDesigner` | `false` | Set `true` to block designer access in production |

### AI Assistant (Optional)

| Property | Default | Description |
|---|---|---|
| `ureportplus.ai.enabled` | `false` | Enable AI-powered report generation |
| `ureportplus.ai.provider` | `openai` | AI provider (currently supports `openai`) |
| `ureportplus.ai.api-key` | — | Your OpenAI API key |
| `ureportplus.ai.api-url` | `https://api.openai.com/v1` | API endpoint |
| `ureportplus.ai.model` | `gpt-4o` | Model name |
| `ureportplus.ai.max-tokens` | `4096` | Max response tokens |
| `ureportplus.ai.temperature` | `0.3` | Generation temperature |
| `ureportplus.ai.max-retries` | `2` | Retry count on failure |

### Console Auth

| Property | Default | Description |
|---|---|---|
| `ureportplus.console.username` | — | Console login username (enables auth when set) |
| `ureportplus.console.password` | — | Console login password |

---

## Expression Language

UReportPlus uses an ANTLR4-powered expression DSL. Expressions can be used in cells (type = "expression"), conditional formatting, image sources, barcode data, and query form components.

### Data Types & Operators

| Type | Example | Description |
|---|---|---|
| Number | `1`, `123`, `0.5` | Integer or decimal |
| String | `'hello'`, `"world"` | Single or double quoted |
| Boolean | `true`, `false` | — |

| Operator | Example | Description |
|---|---|---|
| `+` | `A1 + B1` | Addition / String concatenation |
| `-` | `A1 - 100` | Subtraction |
| `*` | `A1 * 1.2` | Multiplication |
| `/` | `A1 / 3` | Division (8 decimal places) |
| `%` | `A1 % 5` | Modulo |

### Cell References

Reference cells by their grid coordinates. References are **relative to the current cell position** within the expansion tree.

| Syntax | Example | Description |
|---|---|---|
| `&A1` | `&A1` | Absolute reference to cell A1 (does not shift during expansion) |
| `A1` | `A1` | Relative reference — resolves against the parent-child tree |
| `$A1` | `$A1` | Row-relative, column-absolute |

```javascript
// Example: compute tax for each detail row
B1 * 0.13

// Example: aggregate all children
sum(C1)
```

### Conditional Expressions

#### Ternary Expression

```
condition ? valueIfTrue : valueIfFalse
```
```javascript
A1 > 1000 ? "normal" : "low"
A1 > 1000 and A1 < 20000 ? "normal" : "adjusted: " + (A1 + 100)
```

#### If / Else If / Else

```
if (condition) { return value }
else if (condition) { return value }
else { return value }
```
```javascript
if (A1 > 1000) {
    return "normal"
} else if (A1 > 500) {
    return "medium"
} else {
    return "low"
}
```

#### Case Expression

```
case { condition return value, condition return value, ... }
```
```javascript
case { A1 == 100 return "exact",
       A1 > 100 and A1 < 1000 return "normal",
       A1 >= 1000 return "high" }
```

### Built-in Functions

#### Aggregate Functions (operate on datasets)

| Function | Syntax | Description |
|---|---|---|
| `sum` | `ds.sum(field)` | Sum of field values |
| `avg` | `ds.avg(field)` | Average |
| `count` | `ds.count(field)` | Row count |
| `max` | `ds.max(field)` | Maximum |
| `min` | `ds.min(field)` | Minimum |
| `list` | `ds.list(field)` | Comma-separated list |
| `order` | `ds.order(field)` | Ordered list |

#### Math Functions

`abs`, `ceil`, `floor`, `round`, `pow`, `sqrt`, `exp`, `log`, `log10`, `sin`, `cos`, `tan`, `random`, `mode`, `median`, `stdevp`, `vara`

#### String Functions

`length`, `lower`, `upper`, `trim`, `substring`, `replace`, `indexOf`

#### Date Functions

`date`, `day`, `month`, `year`, `week`, `formatDate`

#### Page Functions (for print layout)

| Function | Description |
|---|---|
| `page()` | Current page number |
| `pages()` | Total page count |
| `pageSum(field)` | Running sum for current page |
| `pageAvg(field)` | Running average for current page |
| `pageMax(field)` | Max value on current page |
| `pageMin(field)` | Min value on current page |
| `pageCount(field)` | Count on current page |
| `pageRows()` | Row count on current page |

#### Other Functions

| Function | Description |
|---|---|
| `row()` | Current row index (1-based) |
| `column()` | Current column index (1-based) |
| `param(name)` | URL query parameter value |
| `param(name, defaultValue)` | URL parameter with fallback |
| `json(path)` | Parse JSON string |
| `formatNumber(num, format)` | Format number (e.g., `#,##0.00`) |
| `get(cell, index)` | Get indexed value from a cell with multiple values |

#### Chinese RMB Conversion

| Function | Description |
|---|---|
| `chn(num)` | Convert number to Chinese uppercase |
| `chnMoney(num)` | Convert amount to Chinese RMB format |

---

## Report XML Format

Report definitions are `.ureportplus.xml` files. Here's a minimal example:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ureportplus xmlns="http://www.example.org/ureportplus"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    
    <!-- Datasource: JDBC connection -->
    <datasource name="demo" type="jdbc" 
        driver="org.h2.Driver"
        url="jdbc:h2:mem:ureportplus_demo"
        username="sa" password=""/>
    
    <!-- Dataset: SQL query -->
    <dataset name="ds" type="sql" datasource="demo">
        <sql><![CDATA[
            SELECT hotel_name, room_type, revenue 
            FROM hotel_revenue ORDER BY hotel_name
        ]]></sql>
    </dataset>
    
    <!-- Row definition -->
    <row row-number="1" height="30"/>
    <row row-number="2" height="25"/>
    
    <!-- Column definition -->
    <column col-number="1" width="120"/>
    <column col-number="2" width="100"/>
    
    <!-- Cell: header (A1) -->
    <cell row="1" col="1">
        <value>酒店名称</value>
    </cell>
    
    <!-- Cell: dataset-bound (A2) -->
    <cell row="2" col="1">
        <dataset-value ds-name="ds" property="hotel_name"/>
    </cell>
    
    <!-- Cell: expression (B2) -->
    <cell row="2" col="2">
        <expression>ds.sum(revenue)</expression>
    </cell>
    
    <!-- Paper settings -->
    <paper type="A4" orientation="portrait"
        top-margin="20" bottom-margin="20"
        left-margin="20" right-margin="20"/>
</ureportplus>
```

---

## URL Reference

All URLs are relative to the servlet context path, with `/ureport` as the fixed prefix.

| URL | Method | Description |
|---|---|---|
| `/ureport/` | GET | Report Management Console (listing all reports) |
| `/ureport/designer` | GET | Visual Report Designer |
| `/ureport/designer?_u=file:report.ureportplus.xml` | GET | Open existing report in designer |
| `/ureport/preview?_u=file:report.ureportplus.xml` | GET | HTML preview of a report |
| `/ureport/pdf?_u=file:report.ureportplus.xml` | GET | Export to PDF |
| `/ureport/excel?_u=file:report.ureportplus.xml` | GET | Export to Excel (.xlsx) |
| `/ureport/excel97?_u=file:report.ureportplus.xml` | GET | Export to Excel 97 (.xls) |
| `/ureport/word?_u=file:report.ureportplus.xml` | GET | Export to Word (.docx) |
| `/ureport/image` | POST | Image resource loading |
| `/ureport/chart` | POST | Chart data rendering |
| `/ureport/datasource` | POST | Datasource management |
| `/ureport/searchForm` | GET | Query Form Designer |
| `/ureport/import` | POST | Import Excel template |
| `/ureport/version` | GET | Version info (JSON) |
| `/ureport/ai/*` | POST | AI assistant endpoints |

> **File reference format**: `file:filename.ureportplus.xml` (local file system) or `classpath:filename.ureportplus.xml` (classpath)

---

## SPI Extension Points

UReportPlus provides several SPI interfaces for customization.

### ReportProvider — Custom Report Storage

```java
package com.kingint.ureportplus.provider.report;

import java.io.InputStream;
import java.util.List;

public interface ReportProvider {
    InputStream loadReport(String file);
    void saveReport(String file, String content);
    void deleteReport(String file);
    List<ReportFile> getReportFiles();
    String getName();
    String getPrefix();
    boolean disabled();
}
```

Example implementations:
- `FileReportProvider` — File system storage (default)
- `ClasspathReportProvider` — Read-only from classpath

To implement database-backed storage, implement this interface and register as a Spring bean.

### ImageProvider — Custom Image Loading

```java
package com.kingint.ureportplus.provider.image;

public interface ImageProvider {
    InputStream getImage(String path);
    boolean support(String path);
}
```

### ReportAuthCheck — Access Control

```java
package com.kingint.ureportplus.console.auth;

public interface ReportAuthCheck {
    boolean isPermitted(String action, String reportFile);
}
```

Implement and register as a Spring bean. Returning `false` for an action blocks the operation.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Browser (Designer)                      │
│  Handsontable Grid · CodeMirror Editor · Chart.js · ZXing  │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP / JSON
┌──────────────────────────▼──────────────────────────────────┐
│                  UReportPlusServlet (/ureport/*)             │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐   │
│  │  Designer   │  │   Preview    │  │  Export (PDF/    │   │
│  │  Action     │  │   Action     │  │  Excel/Word)    │   │
│  └─────────────┘  └──────────────┘  └─────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    Core Engine                              │
│  ┌──────────────┐  ┌────────────┐  ┌──────────────────┐   │
│  │ ReportParser │→│ ReportBuild │→│  ExportManager   │   │
│  │  (dom4j)     │  │  (Expand)   │  │  (iText/POI)    │   │
│  └──────────────┘  └────────────┘  └──────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │        Expression Engine (ANTLR4 → AST → eval)       │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**Report Lifecycle**:
1. **Parse** — `ReportParser` reads `.ureportplus.xml` → `ReportDefinition` (POJO model)
2. **Build** — `ReportBuilder.buildReport()` iterates cells, expands parent-child tree with dataset rows → `Report` (runtime model)
3. **Export** — `ExportManager` dispatches to format-specific Producer classes

### Core Packages

| Package | Purpose |
|---|---|
| `com.kingint.ureportplus.definition` | POJO model parsed from XML |
| `com.kingint.ureportplus.model` | Runtime model: `Report`, `Cell`, `Row`, `Column` |
| `com.kingint.ureportplus.build` | Computation engine: cell expansion, aggregation |
| `com.kingint.ureportplus.expression` | Expression DSL: parser, visitor, functions |
| `com.kingint.ureportplus.export` | Export dispatcher + format-specific producers |
| `com.kingint.ureportplus.chart` | Chart plugin system (bar, line, pie, etc.) |
| `com.kingint.ureportplus.provider` | SPI: report storage, image loading |
| `com.kingint.ureportplus.console` | Web layer: servlet, actions, auth |

### Key Dependencies

| Library | Version | Usage |
|---|---|---|
| ANTLR4 | 4.9.3 | Expression parser |
| dom4j | 1.6.1 | Report XML parsing |
| Spring | 4.3.11 | DI, JDBC, Web MVC |
| iText | 5.5.13 | PDF export |
| Apache POI | 3.16 | Excel / Word export |
| ZXing | 3.3.1 | Barcode / QR code |
| Velocity | 1.7 | HTML template rendering |
| Handsontable | 7.4.2 | Spreadsheet grid (frontend) |
| CodeMirror | 5.58.2 | Expression editor (frontend) |
| Chart.js | 2.9.4 | Charts (frontend) |
| Bootstrap | 3.3.7 | UI framework (frontend) |

---

## FAQ

### Q: Can I change the URL from `/ureport` to something else?
No. The `/ureport/*` path is hardcoded as the servlet's internal routing prefix. Changing it breaks all internal URL resolution.

### Q: Does it support database report storage?
Yes. Implement the `ReportProvider` SPI and register it as a Spring bean. See the SPI section above.

### Q: How to disable the designer in production?
Set `ureportplus.disableDesigner=true` in your properties.

### Q: Can I use this with Spring Boot 3.x?
Partially. The engine targets Java 1.7 with Spring 4.x. Spring Boot 3.x + Jakarta EE requires adapting the servlet APIs. Use `ureportplus-all` to avoid classloader conflicts.

### Q: Is IE supported?
No. The designer requires a modern browser (Chrome, Firefox, Edge). IE lacks the necessary JavaScript APIs.

### Q: Where are report files stored?
Default: a `ureportfiles` directory under your application's working directory. Override with `ureportplus.fileStoreDir`.

---

## Links

- 📖 [Report Storage & Datasource Configuration](docs/STORAGE-DATASOURCE.md)
- 🧮 [Report Calculation Model](docs/REPORT-MODEL.md)
- 🔤 [Expression Language Reference](docs/EXPRESSION.md)
- 📦 [Maven Central](https://central.sonatype.com/)
- 🗂️ [Source Code](https://gitee.com/lg10/ureport-plus)

---

## License

Apache License 2.0 · Copyright © 2017-2026 KinginT
