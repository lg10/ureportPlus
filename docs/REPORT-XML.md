# Report XML Format

> Part of the [UReportPlus](../README.md) documentation.

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

## Cell Attributes Reference

| Attribute | Values | Description |
| :--- | :--- | :--- |
| `type` | `text` / `expression` / `dataset` | Cell type |
| `expand` | `none` / `down` / `right` | Expansion direction |
| `left-parent-cell` | cell name | Left parent cell |
| `top-parent-cell` | cell name | Top parent cell |
| `link-url` | URL expression | Hyperlink |
| `condition` | condition expression | Conditional style / conditional value |
| `format` | format pattern | e.g. `#,##0.00` |
