<p align="center">
  <img src="docs/images/logo.png" alt="UReportPlus" width="200" onerror="this.style.display='none'"/>
</p>

<h1 align="center">UReportPlus</h1>

<p align="center">
  <strong>高性能纯 Java 中式报表引擎</strong>
  <br/>
  可视化 Web 设计器 · 多格式导出 · 开源免费
</p>

<p align="center">
  🇨🇳 中文 &nbsp;|&nbsp; <a href="README.md">🇬🇧 English</a>
</p>

<p align="center">
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License"/></a>
  <a href="https://central.sonatype.com/"><img src="https://img.shields.io/badge/maven--central-v1.0.6-blue" alt="Maven Central"/></a>
  <a href="#"><img src="https://img.shields.io/badge/java-1.7%2B-orange" alt="Java"/></a>
  <a href="https://gitee.com/lg10/ureport-plus"><img src="https://img.shields.io/badge/gitee-ureport--plus-red" alt="Gitee"/></a>
</p>

---

## 📖 目录

- [项目简介](#-项目简介)
- [集成指南](#-集成指南)
- [功能特性](#-功能特性)
- [快速开始](#-快速开始)
- [配置参考](#-配置参考)
- [表达式语言](#-表达式语言)
- [内置函数库](#-内置函数库)
- [报表 XML 格式](#-报表-xml-格式)
- [URL 接口参考](#-url-接口参考)
- [扩展开发 SPI](#-扩展开发-spi)
- [系统架构](#-系统架构)
- [技术栈](#-技术栈)
- [版本历史](#-版本历史)
- [常见问题](#-常见问题)

---

## 📌 项目简介

**UReportPlus** 是一款新一代中式报表引擎，完全基于 Java 和 Spring 构建，继承并升级了 UReport2 的设计理念。你可以在浏览器中完成从报表设计、数据绑定、预览到导出的全部流程——**零代码，全可视化**。

### 为什么选择 UReportPlus？

| 对比维度 | 传统报表工具 | UReportPlus |
| :--- | :--- | :--- |
| 部署方式 | 独立服务器，资源重 | **嵌入式 JAR**，随应用启动 |
| 报表设计 | 桌面客户端，需安装 | **浏览器可视化**，打开即用 |
| 开源协议 | 商业授权或 GPL 传染 | **Apache 2.0** 完全开放 |
| 扩展能力 | 插件受限，黑盒 | **SPI 接口**，存储/权限/图片全可定制 |
| 表达式 | 私有语法，学习成本高 | **ANTLR4 标准解析**，类 JavaScript 语法 |
| 二次开发 | 需要商业授权 | **纯 Java 源码**，可深度定制 |

### 适用场景

- 🏨 **酒店行业**：日营收报表、月度汇总、年度对比分析
- 🏭 **制造业**：生产日报、质检报告、工序统计
- 💰 **财务报表**：利润表、资产负债表、现金流量表
- 📊 **数据分析**：交叉表、分组汇总、同比环比
- 🧾 **票据打印**：发票、收据、凭证（PDF 精确排版）
- 🏢 **政务系统**：公示表格、统计年鉴、普查报告

---

## 🔧 集成指南

### Maven 依赖

```xml
<!-- 推荐：控制台模块，Maven 自动解析传递依赖 -->
<dependency>
    <groupId>com.kingint.ureportplus</groupId>
    <artifactId>ureportplus-console</artifactId>
    <version>1.0.6</version>
</dependency>
```

```xml
<!-- 备选：Fat JAR（全部依赖打入一个包，约 80MB） -->
<dependency>
    <groupId>com.kingint.ureportplus</groupId>
    <artifactId>ureportplus-all</artifactId>
    <version>1.0.6</version>
</dependency>
```

| 模块 | 适用场景 |
| :--- | :--- |
| `ureportplus-console` | **生产环境推荐**。精确控制依赖版本，避免冲突 |
| `ureportplus-all` | 快速原型 / 依赖冲突难以解决时使用 |

### Gradle 依赖

```groovy
implementation 'com.kingint.ureportplus:ureportplus-console:1.0.6'
```

### Spring Boot 集成（推荐）

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
            new ServletRegistrationBean<>(
                new UReportPlusServlet(), "/ureport/*"
            );
        reg.setLoadOnStartup(1);
        return reg;
    }
}
```

```properties
# application.properties — 最小配置
ureportplus.fileStoreDir=/opt/reports
```

> ⚠️ **重要**：URL Pattern 必须为 `/ureport/*`，不可修改。这是框架内部路由的硬编码前缀。

### 传统 web.xml 配置

```xml
<!-- Servlet 配置 -->
<servlet>
    <servlet-name>ureportplusServlet</servlet-name>
    <servlet-class>com.kingint.ureportplus.console.UReportPlusServlet</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>ureportplusServlet</servlet-name>
    <url-pattern>/ureport/*</url-pattern>
</servlet-mapping>
```

**项目未使用 Spring 的情况下**：

```xml
<listener>
    <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>
<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>classpath:ureportplus-console-context.xml</param-value>
</context-param>
```

**项目已使用 Spring 的情况下**，在已有配置中导入：

```xml
<import resource="classpath:ureportplus-console-context.xml"/>
```

---

## ✨ 功能特性

### 🎨 可视化报表设计器

基于 Handsontable 电子表格组件打造的 Web 设计器，类 Excel 操作体验。支持：

- 合并/拆分单元格、斜线表头
- 条件样式（根据数据动态变色）
- 字体、字号、颜色、边框、对齐、背景色
- 行高/列宽拖拽调整、插入/删除行列
- **100 步撤销/重做**
- 导出 Excel 模板 → 填写数据 → 导入生成报表

### 📊 中式复杂报表

原生支持中国式复杂报表的所有典型需求：

| 报表类型 | 说明 |
| :--- | :--- |
| 分组报表 | 按字段纵向/横向分组，支持多级嵌套 |
| 交叉报表 | 行维度 × 列维度，自动计算交叉点数据 |
| 明细报表 | 逐行展示数据，支持分页 |
| 分片报表 | 纵向+横向同时分组，适合多维分析 |
| 主子报表 | 父格-子格依赖树，自动迭代展开 |

### 📤 多格式导出

| 格式 | 技术 | 特性 |
| :--- | :--- | :--- |
| **PDF** | iText 5.5 | 精确排版、内嵌中文字体、支持打印 |
| **Excel** | Apache POI 3 | 支持 .xlsx 和 .xls 格式、分页分 Sheet |
| **Word** | Apache POI XWPF | .docx 格式，可继续编辑 |
| **HTML** | 服务端渲染 | 在线预览，支持条件样式和图表 |

### 🧮 表达式引擎

ANTLR4 驱动的自研 DSL，类 JavaScript 语法：

```javascript
// 计算税额
ds.sum(revenue) * 0.13

// 条件判断
A1 > 1000 ? "超标" : "正常"

// 多条件分支
if (A1 > 10000) { return "优秀" }
else if (A1 > 5000) { return "良好" }
else { return "一般" }

// 中文大写金额
chnMoney(ds.sum(revenue))
```

### 📈 图表与条码

- **10 种图表**：柱状图、折线图、饼图、面积图、环形图、雷达图、极坐标图、散点图、气泡图、混合图
- **条码/二维码**：ZXing 库生成，可嵌入报表单元格

### 🔌 扩展机制 (SPI)

全部通过 Spring Bean 注入，无需修改源码：

- **ReportProvider** — 报表存储（本地/数据库/OSS/S3/MinIO）
- **ImageProvider** — 图片加载（本地/HTTP/HTTPS/数据库BLOB）
- **ReportAuthCheck** — 权限校验（集成企业LDAP/OAuth/SSO）

### 更多特性

- 🤖 **AI 智能助手**：自然语言描述需求 → 自动生成报表（支持 OpenAI 兼容接口）
- 📐 **查询表单设计器**：可视化设计参数查询表单
- 🔐 **控制台认证**：支持登录保护，可接入企业权限系统
- 🚀 **生产安全**：可禁用设计器入口，仅保留预览和导出
- 📦 **Fat JAR**：提供 All-in-One 包，零依赖集成

---

## 🚀 快速开始

### 环境要求

- JDK 1.7 及以上
- Maven 3.0 及以上
- 现代浏览器（Chrome / Firefox / Edge）

### 1. 克隆项目

```bash
git clone https://gitee.com/lg10/ureport-plus.git
cd ureport-plus
```

### 2. 编译安装

```bash
cd ureportplus-parent
mvn clean install -DskipTests
```

### 3. 启动 Demo

```bash
cd ../../test-app
mvn spring-boot:run
```

### 4. 打开浏览器

```
http://localhost:8080/ureport/designer
```

> 🔑 Demo 应用内置 H2 数据库（酒店营收示例数据），控制台登录：`admin` / `admin`

---

## ⚙️ 配置参考

### 核心配置

| 属性 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `ureportplus.fileStoreDir` | String | `ureportfiles/` | 报表文件存储目录，生产环境建议使用绝对路径 |
| `ureportplus.disableFileProvider` | boolean | `false` | 设为 `true` 禁用文件系统存储 |
| `ureportplus.disableHttpSessionReportCache` | boolean | `false` | 禁用 Session 级报表缓存 |
| `ureportplus.debug` | boolean | `false` | 调试模式：详细日志，禁用所有缓存 |
| `ureportplus.disableDesigner` | boolean | `false` | **生产环境建议开启**，关闭设计器入口 |

### AI 助手配置

| 属性 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `ureportplus.ai.enabled` | boolean | `false` | 启用 AI 报表生成功能 |
| `ureportplus.ai.provider` | String | `openai` | AI 服务提供商 |
| `ureportplus.ai.api-key` | String | — | API 密钥 |
| `ureportplus.ai.api-url` | String | `https://api.openai.com/v1` | API 端点 |
| `ureportplus.ai.model` | String | `gpt-4o` | 模型名称 |
| `ureportplus.ai.max-tokens` | int | `4096` | 单次最大 Token 数 |
| `ureportplus.ai.temperature` | double | `0.3` | 生成温度 (0-2) |
| `ureportplus.ai.max-retries` | int | `2` | 失败重试次数 |

### 控制台认证

| 属性 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `ureportplus.console.username` | String | — | 设置后自动启用控制台登录 |
| `ureportplus.console.password` | String | — | 登录密码 |

---

## 🧮 表达式语言

UReportPlus 使用基于 ANTLR4 的自研表达式引擎，语法参考 JavaScript，学习成本低。表达式可在单元格、条件样式、图片源、二维码数据源等多处使用。

### 数据类型与运算符

**基本类型**

| 类型 | 示例 | 说明 |
| :--- | :--- | :--- |
| 数字 | `1`, `3.14`, `-20`, `1.5e3` | 整数或小数 |
| 字符串 | `'hello'`, `"世界"` | 单引号或双引号均可 |
| 布尔 | `true`, `false` | — |

**运算符**

| 运算符 | 示例 | 结果 | 说明 |
| :--- | :--- | :--- | :--- |
| `+` | `21 + 31` | `52` | 数字相加 |
| `+` | `"值:" + 331` | `"值:331"` | 字符串拼接 |
| `-` | `21 - 31` | `-10` | 数字相减 |
| `*` | `3 * 6` | `18` | 数字相乘 |
| `/` | `6 / 3` | `2` | 除法（最多 8 位小数） |
| `%` | `5 % 3` | `2` | 取余数 |

**比较与逻辑**：`>` `>=` `<` `<=` `==` `!=` · `and` `or` `not`

### 单元格引用

引用的计算是**相对于当前单元格**进行的——这是中式报表引擎的核心特性。

| 写法 | 含义 | 示例 |
| :--- | :--- | :--- |
| `A1` | 相对引用，沿父子依赖树定位 | `A1 * 0.13` |
| `&A1` | 绝对引用，始终指向 A1 | `&A1` |
| `$A1` | 行相对、列绝对 | `$A1 + B1` |

```javascript
// 每行明细计算税额
B1 * 0.13

// 汇总子格
sum(C1)
```

### 条件判断

#### 三元表达式

```javascript
A1 > 1000 ? "正常" : "偏低"
A1 > 1000 and A1 < 20000 ? "适中" : "调整:" + (A1 + 100)
```

#### If / Else If / Else

```javascript
if (A1 > 10000) {
    return "优秀"
} else if (A1 > 5000) {
    return "良好"
} else {
    return "待改进"
}
```

> `return` 关键字和结尾 `;` 均非强制。

#### Case 表达式

```javascript
case {
    A1 == 100  return "精确匹配",
    A1 > 100 && A1 < 1000  return "正常范围",
    A1 >= 1000  return "超出范围"
}
```

### 变量赋值与返回

```javascript
var total = ds.sum(revenue);
var tax = total * 0.13;
return total - tax;
```

---

## 📚 内置函数库

### 数据集聚合函数

| 函数 | 语法 | 说明 |
| :--- | :--- | :--- |
| `sum` | `ds.sum(字段)` | 求和 |
| `avg` | `ds.avg(字段)` | 平均值 |
| `count` | `ds.count(字段)` | 行数统计 |
| `max` | `ds.max(字段)` | 最大值 |
| `min` | `ds.min(字段)` | 最小值 |
| `list` | `ds.list(字段)` | 逗号分隔列表 |
| `order` | `ds.order(字段)` | 排序列表 |

### 数学函数

`abs` · `ceil` · `floor` · `round` · `pow` · `sqrt` · `exp` · `log` · `log10` · `sin` · `cos` · `tan` · `random` · `median` · `mode` · `stdevp` · `vara`

### 字符串函数

`length` · `lower` · `upper` · `trim` · `substring` · `replace` · `indexOf`

### 日期函数

`date` · `day` · `month` · `year` · `week` · `formatDate`

### 分页函数

`page()` · `pages()` · `pageSum(字段)` · `pageAvg(字段)` · `pageMax(字段)` · `pageMin(字段)` · `pageCount(字段)` · `pageRows()`

### 其他工具函数

| 函数 | 说明 | 示例 |
| :--- | :--- | :--- |
| `row()` / `column()` | 当前行号/列号 | — |
| `param(name)` | URL 查询参数 | `param("hotelId")` |
| `param(name, 默认值)` | 带默认值的参数 | `param("year", "2025")` |
| `json(path)` | 解析 JSON | `json("data.items[0].name")` |
| `formatNumber(n, f)` | 数字格式化 | `formatNumber(12345.6, "#,##0.00")` |
| `get(cell, idx)` | 获取格第 N 个值 | `get(C1, 0)` |

### 中文金额函数

```javascript
chn(12345.67)       // → "壹万贰仟叁佰肆拾伍元陆角柒分"
chnMoney(12345.67)  // → "壹万贰仟叁佰肆拾伍元陆角柒分"
```

---

## 📄 报表 XML 格式

所有报表以 `.ureportplus.xml` 扩展名保存。以下是一个完整示例：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ureportplus xmlns="http://www.example.org/ureportplus"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <!-- 数据源 -->
    <datasource name="demo" type="jdbc"
        driver="org.h2.Driver"
        url="jdbc:h2:mem:ureportplus_demo"
        username="sa" password=""/>

    <!-- 数据集 -->
    <dataset name="ds" type="sql" datasource="demo">
        <sql><![CDATA[
            SELECT hotel_name, room_type, revenue
            FROM hotel_revenue ORDER BY hotel_name
        ]]></sql>
    </dataset>

    <!-- 行列定义 -->
    <row row-number="1" height="30"/>
    <row row-number="2" height="25"/>
    <column col-number="1" width="120"/>
    <column col-number="2" width="100"/>

    <!-- A1: 静态表头 -->
    <cell row="1" col="1">
        <value>酒店名称</value>
        <cell-style font-size="14" bold="true"
            align="center" bgcolor="#f0f0f0"/>
    </cell>

    <!-- A2: 绑定数据集 -->
    <cell row="2" col="1">
        <dataset-value ds-name="ds" property="hotel_name"/>
    </cell>

    <!-- B1: 静态表头 -->
    <cell row="1" col="2">
        <value>营收金额</value>
        <cell-style font-size="14" bold="true"
            align="center" bgcolor="#f0f0f0"/>
    </cell>

    <!-- B2: 表达式计算 -->
    <cell row="2" col="2">
        <expression>ds.sum(revenue)</expression>
    </cell>

    <!-- 纸张设置 -->
    <paper type="A4" orientation="portrait"
        top-margin="20" bottom-margin="20"
        left-margin="20" right-margin="20"/>

</ureportplus>
```

### 单元格核心属性

| 属性 | 可选值 | 说明 |
| :--- | :--- | :--- |
| `type` | `text` / `expression` / `dataset` | 单元格内容类型 |
| `expand` | `none` / `down` / `right` | 数据展开方向 |
| `left-parent-cell` | 格名称 | 左侧父格（纵向分组） |
| `top-parent-cell` | 格名称 | 上方父格（横向分组） |
| `link-url` | URL 表达式 | 超链接地址 |
| `condition` | 条件表达式 | 动态条件样式 |
| `format` | 格式串 | `#,##0.00` 等 |

---

## 🌐 URL 接口参考

所有 URL 前缀固定为 `/ureport`。

### 设计 & 预览

| URL | 说明 |
| :--- | :--- |
| `/ureport/` | 📋 报表管理中心（列表、搜索、统计面板） |
| `/ureport/designer` | 🎨 可视化设计器（新建） |
| `/ureport/designer?_u=file:xxx.ureportplus.xml` | ✏️ 打开编辑已有报表 |
| `/ureport/preview?_u=file:xxx.ureportplus.xml` | 👁️ HTML 在线预览 |
| `/ureport/searchForm` | 🔍 查询表单设计器 |

### 格式导出

| URL | 说明 |
| :--- | :--- |
| `/ureport/pdf?_u=file:xxx.ureportplus.xml` | 📕 导出 PDF |
| `/ureport/excel?_u=file:xxx.ureportplus.xml` | 📗 导出 Excel (.xlsx) |
| `/ureport/excel97?_u=file:xxx.ureportplus.xml` | 📗 导出 Excel 97-2003 (.xls) |
| `/ureport/word?_u=file:xxx.ureportplus.xml` | 📘 导出 Word (.docx) |

### 其他

| URL | 方法 | 说明 |
| :--- | :--- | :--- |
| `/ureport/datasource` | POST | 数据源管理 |
| `/ureport/chart` | POST | 图表渲染 |
| `/ureport/image` | POST | 图片资源加载 |
| `/ureport/import` | POST | 导入 Excel 模板 |
| `/ureport/version` | GET | 版本信息 (JSON) |
| `/ureport/ai/*` | POST | AI 助手 |

> **`_u` 参数**：`file:xxx.ureportplus.xml`（文件系统）或 `classpath:xxx.ureportplus.xml`（类路径）

---

## 🔌 扩展开发 SPI

### ReportProvider — 自定义报表存储

```java
public interface ReportProvider {
    InputStream loadReport(String file);
    void saveReport(String file, String content);
    void deleteReport(String file);
    List<ReportFile> getReportFiles();
    String getName();       // 显示名称
    String getPrefix();     // URL 前缀
    boolean disabled();
}
```

**示例：MySQL 数据库存储**

```java
@Component
public class MysqlReportProvider implements ReportProvider {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public InputStream loadReport(String file) {
        String content = jdbcTemplate.queryForObject(
            "SELECT content FROM ureport_reports WHERE name = ?",
            String.class, file);
        return new ByteArrayInputStream(
            content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void saveReport(String file, String content) {
        jdbcTemplate.update(
            "INSERT INTO ureport_reports (name, content) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE content = ?",
            file, content, content);
    }
    // ... 其余方法
}
```

实现接口并注册为 Spring Bean 即刻生效。

### ImageProvider — 自定义图片源

```java
public interface ImageProvider {
    InputStream getImage(String path);
    boolean support(String path);
}
```

### ReportAuthCheck — 权限校验

```java
public interface ReportAuthCheck {
    boolean isPermitted(String action, String reportFile);
}
```

`action` 取值：`designer`、`preview`、`pdf`、`excel`、`excel97`、`word`、`delete`、`save`

---

## 🏗 系统架构

```
┌──────────────────────────────────────────────────────────────────┐
│                          BROWSER 浏览器                          │
│  ┌────────────┐  ┌────────────┐  ┌──────────────────────────┐   │
│  │ Designer   │  │  Preview   │  │  Console 管理中心         │   │
│  │ 设计器     │  │  预览器    │  │ 报表列表·搜索·统计·示例   │   │
│  └─────┬──────┘  └─────┬──────┘  └────────────┬─────────────┘   │
└────────┼───────────────┼──────────────────────┼──────────────────┘
         │   HTTP/JSON   │                      │
┌────────▼───────────────▼──────────────────────▼──────────────────┐
│              UReportPlusServlet (单入口 /ureport/*)               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────┐  │
│  │Designer  │ │ Preview  │ │ Export   │ │DataSource│ │Auth  │  │
│  │Action    │ │ Action   │ │ Action   │ │ Action   │ │Action│  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └──┬───┘  │
└───────┼────────────┼────────────┼────────────┼──────────┼──────┘
        │            │            │            │           │
┌───────▼────────────▼────────────▼────────────▼───────────▼──────┐
│                       CORE ENGINE 核心引擎                       │
│  ┌──────────────┐   ┌─────────────────┐   ┌─────────────────┐  │
│  │ ReportParser │──▶│  ReportBuilder  │──▶│ ExportManager   │  │
│  │ (dom4j)      │   │  (Cell Expand)  │   │ (Producer SPI)  │  │
│  └──────────────┘   └───────┬─────────┘   └───────┬─────────┘  │
│              ┌──────────────▼──────────────┐       │             │
│              │    Expression Engine        │       │             │
│              │  ANTLR4 → AST → Eval       │       │             │
│              └─────────────────────────────┘       │             │
└────────────────────────────────────────────────────┼─────────────┘
                            ┌────────────────────────▼──────────┐
                            │  HTML · PDF · Excel · Word        │
                            └───────────────────────────────────┘
```

**报表生命周期**：XML 解析 → 父格展开 → 表达式计算 → 格式导出

---

## 🛠 技术栈

| 依赖 | 版本 | 用途 |
| :--- | :--- | :--- |
| ANTLR4 | 4.9.3 | 表达式语法解析 |
| dom4j | 1.6.1 | 报表 XML 解析 |
| Spring | 4.3.11 | DI / JDBC / Web |
| iText | 5.5.13 | PDF 导出 |
| Apache POI | 3.16 | Excel + Word 导出 |
| ZXing | 3.3.1 | 条码/二维码 |
| Velocity | 1.7 | HTML 模板 |
| Handsontable | 7.4.2 | 电子表格编辑器 |
| CodeMirror | 5.58.2 | 表达式编辑器 |
| Chart.js | 2.9.4 | 图表渲染 |
| Bootstrap | 3.3.7 | UI 框架 |

---

## 📋 版本历史

### v1.0.6 (2026-08-27)

- 🚀 **Maven Central 正式发布**：`release.sh` 一键完成构建、签名、合并上传与自动发布
- 📦 **发布流程完善**：GPG 签名、源码/文档 JAR、Central Portal 状态轮询

### v1.0.5 (2026-08-27)

- 🎨 **属性面板全新设计**
- 📤 **导出功能增强**
- 🗄️ **数据源功能增强**
- ⚠️ 本版本未发布到 Maven Central，请直接使用 v1.0.6

### v1.0.4 (2026-07-31)

- 🐛 修复 H2 内置示例数据源未注册问题
- 🐛 修复导出异常时 PDF 文件损坏
- 🐛 修复 Excel 导出时合并单元格区域产生空单元格
- 🐛 报表文件列表自动跳过目录

### v1.0.3 (2026-07-30)

- 🤖 **AI 流式多智能体**：实时阶段反馈，告别假进度条
- 💬 多智能体会话记忆与历史、推理内容智能提取
- 🎨 属性面板商业化 UI 改版、可拖拽 AI 悬浮球
- ⌨️ Ctrl+S / Cmd+S 快捷保存 + 工具栏保存状态指示

### v1.0.1 (2026-07-30)

- 🐛 修复 AI 对话消息重复显示
- 💬 AI 会话记忆与会话持久化
- 🤖 AI 支持表格结构操作（插入/删除行列、合并、分片）
- 📚 AI 学习内置示例报表模式

### v1.0.0 (2026-07-28)

- 🎉 **品牌升级**：UReport2 全面升级为 UReportPlus
- 🎨 **全新控制台 UI**：现代化 SaaS 风格、Indigo 配色、毛玻璃导航栏
- 📊 **统计面板**：报表数、示例数、存储源、版本
- 🔍 **实时搜索**：按报表名称过滤
- 🕐 **时间显示**：创建时间和更新时间
- 📦 **Maven Central 发布支持**
- 📖 **完整中英文文档**

<details>
<summary>📜 UReport2 历史版本</summary>

- v2.7.2 — 示例报表打包进 JAR，启动自动拷贝
- v2.7.1 — UI 美化、内联编辑、分组小计/合计、AI 助手、控制台登录、11 个示例
- v2.6.4 — 核心功能：设计器、导出、表达式引擎、图表

</details>

---

## ❓ 常见问题

<details>
<summary><b>能修改 /ureport 路径吗？</b></summary>

不能。`/ureport/*` 是框架内部路由的硬编码前缀，不可修改。
</details>

<details>
<summary><b>Spring Boot 3.x 能用吗？</b></summary>

有限兼容。引擎基于 javax.servlet，Spring Boot 3.x 使用 Jakarta EE。建议使用 `ureportplus-all` 减少冲突，或等待 Jakarta 迁移版本。
</details>

<details>
<summary><b>报表能存数据库吗？</b></summary>

可以。实现 `ReportProvider` SPI，读写数据库。参见上方 SPI 示例代码。
</details>

<details>
<summary><b>如何禁用设计器？</b></summary>

```properties
ureportplus.disableDesigner=true
```
</details>

<details>
<summary><b>密码如何加密？</b></summary>

使用 Jasypt 等方案包装 `ureportplus.properties` 中的敏感值。
</details>

<details>
<summary><b>支持 IE 吗？</b></summary>

不支持。请使用 Chrome、Firefox、Edge。
</details>

---

## 📎 相关资源

| 文档 | 链接 |
| :--- | :--- |
| 📖 English README | [README.md](README.md) |
| 💾 存储与数据源 | [STORAGE-DATASOURCE.md](docs/STORAGE-DATASOURCE.md) |
| 🧮 报表计算模型 | [REPORT-MODEL.md](docs/REPORT-MODEL.md) |
| 🔤 表达式大全 | [EXPRESSION.md](docs/EXPRESSION.md) |
| 📝 更新日志 | [CHANGELOG.md](CHANGELOG.md) |

---

## 🤝 参与贡献

1. 🍴 Fork 本仓库
2. 🌿 创建分支：`git checkout -b feature/xxx`
3. ✏️ 提交更改：`git commit -m 'feat: xxx'`
4. 📤 推送分支：`git push origin feature/xxx`
5. 🔀 提交 Pull Request 到 `develop` 分支

---

## 📄 开源协议

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0) · Copyright © 2017-2026 **KinginT**

Powered by [Gitee](https://gitee.com/lg10/ureport-plus)
