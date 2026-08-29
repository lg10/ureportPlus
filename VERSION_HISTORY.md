# Version History

🇬🇧 English | [🇨🇳 中文](VERSION_HISTORY-zh_CN.md)

Release history of UReportPlus. Published artifacts are available on [Maven Central](https://central.sonatype.com/artifact/com.kingint.ureportplus/ureportplus-console).

## v1.0.7 (2026-08-29)

- ✨ Dataset binding usability: switching a cell's dataset now auto-clears field selections that no longer exist, and the data preview refreshes immediately when the dataset, field or aggregation changes
- 🐛 Fixed QR code cells producing incorrect data when bound to datasets
- 🖼 Console pages now ship with the UReportPlus logo asset

## v1.0.6 (2026-08-27)

- 🚀 Official Maven Central release: `release.sh` completes build, signing, bundle upload and auto-publish in one command
- 📦 Polished release pipeline (GPG signing, sources/javadoc JARs, Central Portal status polling)

## v1.0.5 (2026-08-27)

- 🎨 Property panel redesigned
- 📤 Export enhancements
- 🗄️ Data source enhancements
- ⚠️ Never published to Maven Central — use v1.0.6 or later instead

## v1.0.4 (2026-07-31)

- 🐛 Fixed H2 built-in demo data source not being registered
- 🐛 Fixed PDF corruption when export fails
- 🐛 Fixed empty cells produced in merged regions during Excel export
- 🐛 Report file listing now skips directories

## v1.0.3 (2026-07-30)

- 🤖 Streaming multi-agent AI: real-time stage feedback, no more fake progress bars
- 💬 Multi-agent conversation memory and history, smart extraction of reasoning content
- 🎨 Commercial-grade property panel UI overhaul, draggable AI floating ball
- ⌨️ Ctrl+S / Cmd+S save shortcut + save status indicator in toolbar

## v1.0.1 (2026-07-30)

- 🐛 Fixed duplicated AI chat messages
- 💬 AI conversation memory with session persistence
- 🤖 AI can manipulate table structure (insert/delete rows & columns, merge, bands)
- 📚 AI learns from built-in example report patterns

## v1.0.0 (2026-07-28)

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
- See [CHANGELOG.md](CHANGELOG.md) for the full UReport2 2.x changelog (2017)

</details>
