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
  🇬🇧 English &nbsp;|&nbsp; <a href="README-zh_CN.md">🇨🇳 中文</a>
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
- [扩展开发 (SPI)](#-扩展开发-spi)
- [系统架构](#-系统架构)
- [技术栈](#-技术栈)
- [版本历史](#-版本历史)
- [常见问题](#-常见问题)

---

## 📌 项目简介

**UReportPlus** 是新一代中式报表引擎，基于 Spring 构建，延续了 UReport2 的设计理念并进行了全面升级。你可以在浏览器中完成复杂报表的设计、预览和导出——无需编写代码，纯可视化操作。

### 为什么选择 UReportPlus

| 对比维度 | 传统报表工具 | UReportPlus |
| :--- | :--- | :--- |
| 部署方式 | 独立服务器，重 | 嵌入式 JAR，轻 |
| 报表设计 | 桌面客户端 | **浏览器可视化** |
| 开源协议 | 商业授权 / GPL | **Apache 2.0** 完全开放 |
| 扩展性 | 插件受限 | **SPI 接口**，可定制存储/权限/图片源 |
| 表达式 | 私有语法 | **ANTLR4 标准解析**，类 JavaScript 语法 |
| 二次开发 | 黑盒 | 纯 Java 源码，可深度定制 |

### 典型应用场景

- 🏨 酒店行业日报 / 月报 / 年度营收分析
- 🏭 制造业生产报表 / 质检报告
- 💰 财务报表：利润表、资产负债表、现金流量表
- 📊 数据看板：交叉表、分组汇总、同比环比
- 🧾 票据打印：发票、收据、凭证（PDF 精确排版）

---

## 🔧 集成指南

### Maven 依赖

```xml
<!-- 推荐：控制台模块，Maven 自动管理传递依赖 -->
<dependency>
    <groupId>com.kingint.ureportplus</groupId>
    <artifactId>ureportplus-console</artifactId>
    <version>1.0.6</version>
</dependency>
```

```xml
<!-- 备选：Fat JAR（全部依赖打包在内，80MB） -->
<dependency>
    <groupId>com.kingint.ureportplus</groupId>
    <artifactId>ureportplus-all</artifactId>
    <version>1.0.6</version>
</dependency>
```

> **选择建议**：生产环境用 `console` 精确控制依赖版本；快速原型用 `all` 避免冲突。

### Gradle

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
            new ServletRegistrationBean<>(new UReportPlusServlet(), "/ureport/*");
        reg.setLoadOnStartup(1);
        return reg;
    }
}
```

```properties
# application.properties - 最小配置
ureportplus.fileStoreDir=/opt/reports
```

### 传统 web.xml

```xml
<!-- 1. Servlet -->
<servlet>
    <servlet-name>ureportplusServlet</servlet-name>
    <servlet-class>com.kingint.ureportplus.console.UReportPlusServlet</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>ureportplusServlet</servlet-name>
    <url-pattern>/ureport/*</url-pattern>         <!-- 必须 /ureport/* -->
</servlet-mapping>

<!-- 2. Spring 上下文（如未使用 Spring） -->
<listener>
    <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>
<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>classpath:ureportplus-console-context.xml</param-value>
</context-param>
```

如项目已使用 Spring，改为在现有配置中导入：

```xml
<import resource="classpath:ureportplus-console-context.xml"/>
```

---

## ✨ 功能特性

<table>
  <tr>
    <td width="50%">
      <h4>🎨 可视化报表设计器</h4>
      <p>基于 Handsontable 的专业级 Web 设计器。支持合并单元格、条件样式、多级表头、斜线表头。类 Excel 操作体验，支持撤销/重做（100步）。</p>
    </td>
    <td width="50%">
      <h4>📊 中式复杂报表</h4>
      <p>原生支持交叉表、分组表、明细表、分片表。父格-子格依赖树驱动迭代展开，自动处理数据分组、小计、合计。</p>
    </td>
  </tr>
  <tr>
    <td>
      <h4>📤 多格式导出</h4>
      <p>一键导出 <strong>PDF</strong>（精确排版）、<strong>Excel</strong>（.xlsx / .xls，支持分页分Sheet）、<strong>Word</strong>（.docx）、<strong>HTML</strong>（在线预览）。PDF 内置中文字体，无需额外配置。</p>
    </td>
    <td>
      <h4>🗄️ 多数据源</h4>
      <p>JDBC 直连数据库（MySQL / Oracle / PostgreSQL / H2...），Spring Bean 注入，内置示例数据集。SQL 查询与可视化数据集双模式。</p>
    </td>
  </tr>
  <tr>
    <td>
      <h4>🧮 强大的表达式引擎</h4>
      <p>ANTLR4 驱动的 DSL 语言。支持单元格引用（相对/绝对）、三元表达式、if/else/case 条件判断、40+ 内置函数。含中文大写金额转换（<code>chn</code> / <code>chnMoney</code>）。</p>
    </td>
    <td>
      <h4>📈 图表 & 条码</h4>
      <p>内置 10 种图表类型（柱状图、折线图、饼图、面积图、雷达图、散点图、气泡图等）。支持 ZXing 条码和二维码生成。</p>
    </td>
  </tr>
  <tr>
    <td>
      <h4>🔌 SPI 可扩展</h4>
      <p>自定义报表存储（本地文件 / 数据库 / OSS / S3），自定义图片加载（本地 / HTTP / HTTPS），自定义权限校验。全部通过标准 Spring Bean 注入。</p>
    </td>
    <td>
      <h4>🤖 AI 智能助手</h4>
      <p>可选 AI 功能：自然语言描述需求 → 自动生成报表模板。支持 OpenAI 兼容接口。可配置模型、温度、Token 上限。</p>
    </td>
  </tr>
  <tr>
    <td>
      <h4>📐 查询表单</h4>
      <p>可视化设计查询参数表单。支持文本框、下拉框、日期选择器、单选框、复选框、提交/重置按钮。参数通过 <code>param()</code> 函数在表达式中使用。</p>
    </td>
    <td>
      <h4>🔐 权限控制</h4>
      <p>控制台登录认证。通过 SPI 接口可集成企业现有权限体系（LDAP / OAuth / SSO）。可禁用设计器用于生产环境。</p>
    </td>
  </tr>
</table>

---

## 🚀 快速开始

### 前置条件

- JDK 1.7+
- Maven 3.0+

### 一分钟跑起来

```bash
# 1. 克隆项目
git clone https://gitee.com/lg10/ureport-plus.git
cd ureport-plus

# 2. 编译安装到本地仓库
cd ureportplus-parent
mvn clean install -DskipTests

# 3. 启动 Demo 应用
cd ../../test-app
mvn spring-boot:run
```

浏览器打开 **http://localhost:8080/ureport/designer** 🎉

> Demo 内置 H2 数据库酒店营收示例数据。控制台登录：`admin` / `admin`

---

## ⚙️ 配置参考

### 核心配置

| 属性 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `ureportplus.fileStoreDir` | String | `ureportfiles/` | 报表文件存储目录（生产环境用绝对路径） |
| `ureportplus.disableFileProvider` | boolean | `false` | 设为 `true` 禁用文件系统存储 |
| `ureportplus.disableHttpSessionReportCache` | boolean | `false` | 禁用 Session 级报表缓存 |
| `ureportplus.debug` | boolean | `false` | 调试模式（详细日志 + 禁用缓存） |
| `ureportplus.disableDesigner` | boolean | `false` | 生产环境关闭设计器入口 |

### AI 助手配置

| 属性 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `ureportplus.ai.enabled` | boolean | `false` | 启用 AI 报表生成 |
| `ureportplus.ai.provider` | String | `openai` | AI 提供商标识 |
| `ureportplus.ai.api-key` | String | — | API 密钥 |
| `ureportplus.ai.api-url` | String | `https://api.openai.com/v1` | API 端点 |
| `ureportplus.ai.model` | String | `gpt-4o` | 模型名称 |
| `ureportplus.ai.max-tokens` | int | `4096` | 最大 Token 数 |
| `ureportplus.ai.temperature` | double | `0.3` | 生成温度 (0-2) |
| `ureportplus.ai.max-retries` | int | `2` | 失败重试次数 |

### 控制台认证

| 属性 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `ureportplus.console.username` | String | — | 设置即启用控制台登录 |
| `ureportplus.console.password` | String | — | 登录密码 |

---

## 🧮 表达式语言

UReportPlus 使用 ANTLR4 自研表达式引擎，语法兼容主流编程语言习惯，学习成本极低。

### 数据类型

| 类型 | 示例 | 说明 |
| :--- | :--- | :--- |
| 数字 | `1`, `3.14`, `-20` | 整数或小数 |
| 字符串 | `'hello'`, `"世界"` | 单引号或双引号均可 |
| 布尔 | `true`, `false` | — |

### 运算符

| 运算符 | 示例 | 结果 |
| :--- | :--- | :--- |
| `+` | `21 + 31` | `52` |
| `+` | `"值:" + 331` | `"值:331"` |
| `-` | `21 - 31` | `-10` |
| `*` | `3 * 6` | `18` |
| `/` | `6 / 3` | `2` |
| `%` | `5 % 3` | `2` |

### 单元格引用

表达式中的单元格引用是**相对于当前格**进行计算的——这是中式报表引擎的关键特性。

| 写法 | 含义 | 示例 |
| :--- | :--- | :--- |
| `A1` | 相对引用：沿父子树定位 | `A1 * 0.13` |
| `&A1` | 绝对引用：始终指向 A1 | `&A1` |
| `$A1` | 行相对、列绝对 | `$A1 + B1` |

```javascript
// 实际场景：计算每行明细的税额
B1 * 0.13                          // B1 为金额，13% 税率

// 实际场景：汇总子格
sum(C1)                            // 对所有子格 C1 求和
```

### 条件判断

#### 三元表达式

```
条件 ? 真值 : 假值
```

```javascript
A1 > 1000 ? "正常" : "偏低"
A1 > 1000 && A1 < 20000 ? "适中" : "调整值:" + (A1 + 100)
```

#### If / Else If / Else

```javascript
if (A1 > 1000) {
    return "正常值"
} else if (A1 > 500) {
    return "中等值"
} else {
    return "低值"
}
```

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
// 定义变量
var total = ds.sum(revenue);
var tax = total * 0.13;

// 返回最终值
return total - tax;
```

---

## 📚 内置函数库

### 数据集聚合函数

对 SQL 查询结果集进行统计计算。

| 函数 | 语法 | 说明 |
| :--- | :--- | :--- |
| `sum` | `ds.sum(字段)` | 求和 |
| `avg` | `ds.avg(字段)` | 平均值 |
| `count` | `ds.count(字段)` | 计数 |
| `max` | `ds.max(字段)` | 最大值 |
| `min` | `ds.min(字段)` | 最小值 |
| `list` | `ds.list(字段)` | 逗号分隔列表 |
| `order` | `ds.order(字段)` | 排序列表 |

### 数学函数

`abs(n)` · `ceil(n)` · `floor(n)` · `round(n, 精度)` · `pow(n, 幂)` · `sqrt(n)` · `exp(n)` · `log(n)` · `log10(n)` · `sin(n)` · `cos(n)` · `tan(n)` · `random()` · `median(字段)` · `mode(字段)` · `stdevp(字段)` · `vara(字段)`

### 字符串函数

`length(s)` · `lower(s)` · `upper(s)` · `trim(s)` · `substring(s, begin, end)` · `replace(s, old, new)` · `indexOf(s, sub)`

### 日期函数

`date(年, 月, 日)` · `day(date)` · `month(date)` · `year(date)` · `week(date)` · `formatDate(date, 格式)`

### 分页函数（打印用）

| 函数 | 说明 |
| :--- | :--- |
| `page()` | 当前页码 |
| `pages()` | 总页数 |
| `pageSum(字段)` | 当前页求和 |
| `pageAvg(字段)` | 当前页均值 |
| `pageMax(字段)` / `pageMin(字段)` | 当前页极值 |
| `pageCount(字段)` | 当前页计数 |
| `pageRows()` | 当前页行数 |

### 其他工具函数

| 函数 | 说明 | 示例 |
| :--- | :--- | :--- |
| `row()` | 当前行号（从 1 开始） | `row()` |
| `column()` | 当前列号（从 1 开始） | `column()` |
| `param(name)` | 获取 URL 查询参数 | `param("hotelId")` |
| `param(name, 默认值)` | 带默认值的参数 | `param("year", "2025")` |
| `json(path)` | 解析 JSON 数据 | `json("data.items[0].name")` |
| `formatNumber(n, 格式)` | 数字格式化 | `formatNumber(12345.6, "#,##0.00")` → `12,345.60` |
| `get(cell, index)` | 获取格第 N 个值 | `get(C1, 0)` |

### 中文金额转换

```javascript
chn(12345.67)       // → "壹万贰仟叁佰肆拾伍元陆角柒分"
chnMoney(12345.67)  // → "壹万贰仟叁佰肆拾伍元陆角柒分"
```

---

## 📄 报表 XML 格式

报表定义文件使用 `.ureportplus.xml` 扩展名。完整示例如下：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ureportplus xmlns="http://www.example.org/ureportplus"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <!-- ===== 数据源：JDBC 连接 ===== -->
    <datasource name="demo" type="jdbc"
        driver="org.h2.Driver"
        url="jdbc:h2:mem:ureportplus_demo"
        username="sa" password=""/>

    <!-- ===== 数据集：SQL 查询 ===== -->
    <dataset name="ds" type="sql" datasource="demo">
        <sql><![CDATA[
            SELECT hotel_name, room_type, revenue
            FROM hotel_revenue
            ORDER BY hotel_name
        ]]></sql>
    </dataset>

    <!-- ===== 行定义 ===== -->
    <row row-number="1" height="30"/>      <!-- 表头行 -->
    <row row-number="2" height="25"/>      <!-- 数据行 -->

    <!-- ===== 列定义 ===== -->
    <column col-number="1" width="120"/>   <!-- 酒店名称列 -->
    <column col-number="2" width="100"/>   <!-- 营收列 -->

    <!-- ===== 单元格 A1：静态表头 ===== -->
    <cell row="1" col="1">
        <value>酒店名称</value>
        <cell-style font-size="14" bold="true"
            align="center" bgcolor="#f0f0f0"/>
    </cell>

    <!-- ===== 单元格 A2：绑定数据集字段 ===== -->
    <cell row="2" col="1">
        <dataset-value ds-name="ds" property="hotel_name"/>
    </cell>

    <!-- ===== 单元格 B1：静态表头 ===== -->
    <cell row="1" col="2">
        <value>营收金额</value>
        <cell-style font-size="14" bold="true"
            align="center" bgcolor="#f0f0f0"/>
    </cell>

    <!-- ===== 单元格 B2：表达式 ===== -->
    <cell row="2" col="2">
        <expression>ds.sum(revenue)</expression>
    </cell>

    <!-- ===== 纸张设置 ===== -->
    <paper type="A4" orientation="portrait"
        top-margin="20" bottom-margin="20"
        left-margin="20" right-margin="20"/>

</ureportplus>
```

### 单元格属性全览

| 属性 | 可选值 | 说明 |
| :--- | :--- | :--- |
| `type` | `text` / `expression` / `dataset` | 单元格类型 |
| `expand` | `none` / `down` / `right` | 展开方向 |
| `left-parent-cell` | 格名 | 左侧父格 |
| `top-parent-cell` | 格名 | 上方父格 |
| `link-url` | URL 表达式 | 超链接 |
| `condition` | 条件表达式 | 条件样式/条件值 |
| `format` | 格式字符串 | `#,##0.00` 等 |

---

## 🌐 URL 接口参考

所有接口前缀为 `/ureport`（Servlet 固定映射）。

### 设计 & 预览

| URL | 说明 |
| :--- | :--- |
| `/ureport/` | 报表管理中心（报表列表 + 示例卡片） |
| `/ureport/designer` | 可视化设计器（新建空白报表） |
| `/ureport/designer?_u=file:report.ureportplus.xml` | 打开已有报表编辑 |
| `/ureport/preview?_u=file:report.ureportplus.xml` | HTML 在线预览 |
| `/ureport/searchForm` | 查询表单设计器 |

### 导出

| URL | 说明 |
| :--- | :--- |
| `/ureport/pdf?_u=file:report.ureportplus.xml` | 导出 PDF |
| `/ureport/excel?_u=file:report.ureportplus.xml` | 导出 Excel (.xlsx) |
| `/ureport/excel97?_u=file:report.ureportplus.xml` | 导出 Excel 97 (.xls) |
| `/ureport/word?_u=file:report.ureportplus.xml` | 导出 Word (.docx) |

### 其他

| URL | 说明 |
| :--- | :--- |
| `/ureport/datasource` | 数据源管理 API |
| `/ureport/chart` | 图表渲染 API |
| `/ureport/image` | 图片资源加载 |
| `/ureport/import` | 导入 Excel 模板 |
| `/ureport/version` | 版本信息 (JSON) |
| `/ureport/ai/*` | AI 助手 API |

> `_u` 参数格式：`file:reportname.ureportplus.xml`（文件系统）或 `classpath:reportname.ureportplus.xml`（类路径）

---

## 🔌 扩展开发 (SPI)

### ReportProvider — 自定义报表存储

```java
public interface ReportProvider {
    InputStream loadReport(String file);   // 加载报表
    void saveReport(String file, String content);  // 保存报表
    void deleteReport(String file);         // 删除报表
    List<ReportFile> getReportFiles();      // 文件列表
    String getName();                       // 显示名称
    String getPrefix();                     // URL 前缀
    boolean disabled();                     // 是否禁用
}
```

**示例：数据库存储**

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
    // ... 其他方法
}
```

实现接口并注册为 Spring Bean 即可生效，无需修改 UReportPlus 源码。

### ImageProvider — 自定义图片源

```java
public interface ImageProvider {
    InputStream getImage(String path);  // 获取图片流
    boolean support(String path);       // 是否支持该路径
}
```

内置实现：
- `DefaultImageProvider` — 本地文件
- `HttpImageProvider` / `HttpsImageProvider` — 远程图片

### ReportAuthCheck — 权限校验

```java
public interface ReportAuthCheck {
    boolean isPermitted(String action, String reportFile);
}
```

`action` 包括：`designer`、`preview`、`pdf`、`excel`、`word`、`delete`、`save`

---

## 🏗 系统架构

```
┌────────────────────────────────────────────────────────────────────┐
│                         BROWSER                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │  Designer    │  │  Preview     │  │  Console (管理中心)     │  │
│  │ Handsontable │  │ HTML Render  │  │ 报表列表 · 一键预览编辑  │  │
│  │ CodeMirror   │  │ Chart.js     │  │ 示例卡片 · 实时搜索     │  │
│  │ Bootstrap 3  │  │ ZXing        │  │ 统计面板 · 版本信息     │  │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬────────────┘  │
└─────────┼──────────────────┼──────────────────────┼───────────────┘
          │ HTTP / JSON       │                      │
┌─────────▼───────────────────▼──────────────────────▼───────────────┐
│                    UReportPlusServlet                              │
│                   (单入口 /ureport/*)                               │
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

### 报表生命周期

```
XML 定义文件 (.ureportplus.xml)
    │
    ▼ dom4j 解析
ReportDefinition (静态定义模型)
    │
    ▼ ReportBuilder.buildReport()
Report (运行时模型：Cell / Row / Column 树)
    │
    ▼ 父格展开 + 表达式计算
Cell 树 (每个格持有数据值)
    │
    ▼ ExportManager.dispatch()
HTML / PDF / Excel / Word 输出
```

---

## 🛠 技术栈

### 后端

| 依赖 | 版本 | 用途 |
| :--- | :--- | :--- |
| ANTLR4 Runtime | 4.9.3 | 表达式解析 |
| dom4j | 1.6.1 | XML 解析 |
| Spring Framework | 4.3.11 | DI / JDBC / Web |
| iText | 5.5.13 | PDF 导出 |
| Apache POI | 3.16 | Excel + Word 导出 |
| ZXing | 3.3.1 | 条码 / 二维码 |
| Velocity | 1.7 | HTML 模板渲染 |
| Jackson | 1.9.11 | JSON 处理 |

### 前端

| 库 | 版本 | 用途 |
| :--- | :--- | :--- |
| Handsontable | 7.4.2 | 电子表格编辑器 |
| CodeMirror | 5.58.2 | 表达式代码编辑器 |
| Chart.js | 2.9.4 | 图表渲染 |
| Bootstrap | 3.3.7 | UI 框架 |
| jQuery | 1.12 | DOM 操作 |

---

## 📋 版本历史

### v1.0.6 (2026-08-27)

- 🚀 Maven Central 正式发布：`release.sh` 一键完成构建、签名、合并上传与自动发布
- 📦 修复并完善发布流程（GPG 签名、源码/文档 JAR、Central Portal 状态轮询）

### v1.0.5 (2026-08-27)

- 🎨 属性面板全新设计
- 📤 导出功能增强
- 🗄️ 数据源功能增强
- ⚠️ 本版本未发布到 Maven Central，请直接使用 v1.0.6

### v1.0.4 (2026-07-31)

- 🐛 修复 H2 内置示例数据源未注册问题
- 🐛 修复导出异常时 PDF 文件损坏
- 🐛 修复 Excel 导出时合并单元格区域产生空单元格
- 🐛 报表文件列表自动跳过目录

### v1.0.3 (2026-07-30)

- 🤖 AI 流式多智能体：实时阶段反馈，告别假进度条
- 💬 多智能体会话记忆与历史、推理内容智能提取
- 🎨 属性面板商业化 UI 改版、可拖拽 AI 悬浮球
- ⌨️ Ctrl+S / Cmd+S 快捷保存 + 工具栏保存状态指示

### v1.0.1 (2026-07-30)

- 🐛 修复 AI 对话消息重复显示
- 💬 AI 会话记忆与会话持久化
- 🤖 AI 支持表格结构操作（插入/删除行列、合并、分片）
- 📚 AI 学习内置示例报表模式

### v1.0.0 (2026-07-28)

- 🎉 UReport2 → UReportPlus 全面品牌升级
- 🎨 全新控制台 UI：现代化 SaaS 风格 + 毛玻璃导航栏
- 📊 控制台统计面板：报表数、示例数、存储源、版本
- 🔍 实时搜索过滤报表名称
- 🕐 报表创建时间 / 更新时间显示
- 🚀 Maven Central 发布支持
- 📖 完整中英文 README 文档

<details>
<summary>UReport2 历史版本</summary>

- v2.7.2 — 示例报表打包进 JAR，启动自动拷贝
- v2.7.1 — UI 美化、内联编辑、分组小计/合计、AI 多智能体助手、控制台登录、11 个示例报表
- v2.6.4 — 基础功能集：设计器、导出、表达式引擎、图表

</details>

---

## ❓ 常见问题

<details>
<summary><b>能否修改 /ureport 路径？</b></summary>

不能。`/ureport/*` 是 Servlet 内部路由的硬编码前缀，修改会导致所有子路径（`/designer`、`/preview` 等）路由失败。
</details>

<details>
<summary><b>Spring Boot 3.x 能用吗？</b></summary>

部分兼容。引擎基于 Spring 4.x + javax.servlet，Spring Boot 3.x 改用 Jakarta EE。建议使用 `ureportplus-all` Fat JAR 避免类加载冲突，或后续关注 Jakarta 迁移版本。
</details>

<details>
<summary><b>怎么在报表里显示数据库图片？</b></summary>

实现 `ImageProvider` SPI 接口，从数据库读取 BLOB 返回 `InputStream`。然后在报表单元格中设置图片 URL 指向你的 Provider 路径。
</details>

<details>
<summary><b>如何禁用设计器以提高生产安全性？</b></summary>

```properties
ureportplus.disableDesigner=true
```
</details>

<details>
<summary><b>支持 IE 浏览器吗？</b></summary>

不支持。设计器依赖现代浏览器 API（Canvas、ES6、CSS Grid）。推荐 Chrome、Firefox、Edge。
</details>

<details>
<summary><b>报告文件可以存到数据库吗？</b></summary>

可以。实现 `ReportProvider` 接口，读写数据库。参见 [扩展开发](#-扩展开发-spi) 章节。
</details>

<details>
<summary><b>数据源密码能加密存储吗？</b></summary>

可以。数据源的密码通过 Spring 属性占位符注入，你可以使用 Jasypt 等加密方案包装 `ureportplus.properties` 中的敏感值。
</details>

---

## 📎 相关资源

| 文档 | 链接 |
| :--- | :--- |
| 报表存储与数据源配置 | [STORAGE-DATASOURCE.md](docs/STORAGE-DATASOURCE.md) |
| 报表计算模型详解 | [REPORT-MODEL.md](docs/REPORT-MODEL.md) |
| 表达式语言大全 | [EXPRESSION.md](docs/EXPRESSION.md) |
| 更新日志 | [CHANGELOG.md](CHANGELOG.md) |
| 问题模板 | [ISSUE_TEMPLATE.md](ISSUE_TEMPLATE.md) |

---

## 🤝 参与贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 提交 Pull Request

---

## 📄 开源协议

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)

Copyright © 2017-2026 **KinginT** · Powered by [Gitee](https://gitee.com/lg10/ureport-plus)
