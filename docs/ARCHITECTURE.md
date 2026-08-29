# Architecture & Tech Stack

> Part of the [UReportPlus](../README.md) documentation.

## Architecture

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

## Report Lifecycle

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

## Tech Stack

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
