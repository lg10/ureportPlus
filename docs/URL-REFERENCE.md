# URL Reference

> Part of the [UReportPlus](../README.md) documentation.

All endpoints are prefixed with `/ureport` (the fixed Servlet mapping).

## Design & Preview

| URL | Description |
| :--- | :--- |
| `/ureport/` | Report management center (report list + example cards) |
| `/ureport/designer` | Visual designer (new blank report) |
| `/ureport/designer?_u=file:report.ureportplus.xml` | Open an existing report for editing |
| `/ureport/preview?_u=file:report.ureportplus.xml` | HTML online preview |
| `/ureport/searchForm` | Query form designer |

## Export

| URL | Description |
| :--- | :--- |
| `/ureport/pdf?_u=file:report.ureportplus.xml` | Export PDF |
| `/ureport/excel?_u=file:report.ureportplus.xml` | Export Excel (.xlsx) |
| `/ureport/excel97?_u=file:report.ureportplus.xml` | Export Excel 97 (.xls) |
| `/ureport/word?_u=file:report.ureportplus.xml` | Export Word (.docx) |

## Others

| URL | Description |
| :--- | :--- |
| `/ureport/datasource` | Data source management API |
| `/ureport/chart` | Chart rendering API |
| `/ureport/image` | Image resource loading |
| `/ureport/import` | Import Excel templates |
| `/ureport/version` | Version info (JSON) |
| `/ureport/ai/*` | AI assistant API |

> `_u` parameter format: `file:reportname.ureportplus.xml` (file system) or `classpath:reportname.ureportplus.xml` (classpath)
