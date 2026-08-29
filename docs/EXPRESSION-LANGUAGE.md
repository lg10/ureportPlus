# Expression Language & Built-in Functions

> Part of the [UReportPlus](../README.md) documentation.

UReportPlus uses a custom ANTLR4-based expression engine. Its syntax follows mainstream programming language conventions, so the learning curve is minimal. Expressions can be used in cell values, conditional styles, image sources, QR code data sources and more.

## Data Types

| Type | Examples | Description |
| :--- | :--- | :--- |
| Number | `1`, `3.14`, `-20` | Integer or decimal |
| String | `'hello'`, `"world"` | Single or double quotes |
| Boolean | `true`, `false` | — |

## Operators

| Operator | Example | Result |
| :--- | :--- | :--- |
| `+` | `21 + 31` | `52` |
| `+` | `"Value:" + 331` | `"Value:331"` |
| `-` | `21 - 31` | `-10` |
| `*` | `3 * 6` | `18` |
| `/` | `6 / 3` | `2` |
| `%` | `5 % 3` | `2` |

Comparison and logic: `>` `>=` `<` `<=` `==` `!=` · `and` `or` `not`

## Cell References

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

## Conditionals

### Ternary Expression

```
condition ? trueValue : falseValue
```

```javascript
A1 > 1000 ? "Normal" : "Low"
A1 > 1000 && A1 < 20000 ? "Moderate" : "Adjusted:" + (A1 + 100)
```

### If / Else If / Else

```javascript
if (A1 > 1000) {
    return "High"
} else if (A1 > 500) {
    return "Medium"
} else {
    return "Low"
}
```

The `return` keyword and trailing `;` are both optional.

### Case Expression

```javascript
case {
    A1 == 100  return "Exact match",
    A1 > 100 && A1 < 1000  return "Normal range",
    A1 >= 1000  return "Out of range"
}
```

## Variables and Return

```javascript
// Define variables
var total = ds.sum(revenue);
var tax = total * 0.13;

// Return the final value
return total - tax;
```

---

# Built-in Functions

## Dataset Aggregation

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

## Math Functions

`abs(n)` · `ceil(n)` · `floor(n)` · `round(n, precision)` · `pow(n, exp)` · `sqrt(n)` · `exp(n)` · `log(n)` · `log10(n)` · `sin(n)` · `cos(n)` · `tan(n)` · `random()` · `median(field)` · `mode(field)` · `stdevp(field)` · `vara(field)`

## String Functions

`length(s)` · `lower(s)` · `upper(s)` · `trim(s)` · `substring(s, begin, end)` · `replace(s, old, new)` · `indexOf(s, sub)`

## Date Functions

`date(year, month, day)` · `day(date)` · `month(date)` · `year(date)` · `week(date)` · `formatDate(date, pattern)`

## Pagination Functions (for printing)

| Function | Description |
| :--- | :--- |
| `page()` | Current page number |
| `pages()` | Total pages |
| `pageSum(field)` | Sum on current page |
| `pageAvg(field)` | Average on current page |
| `pageMax(field)` / `pageMin(field)` | Max/min on current page |
| `pageCount(field)` | Count on current page |
| `pageRows()` | Row count on current page |

## Utility Functions

| Function | Description | Example |
| :--- | :--- | :--- |
| `row()` | Current row number (1-based) | `row()` |
| `column()` | Current column number (1-based) | `column()` |
| `param(name)` | Get a URL query parameter | `param("hotelId")` |
| `param(name, default)` | Parameter with default value | `param("year", "2025")` |
| `json(path)` | Parse JSON data | `json("data.items[0].name")` |
| `formatNumber(n, pattern)` | Number formatting | `formatNumber(12345.6, "#,##0.00")` → `12,345.60` |
| `get(cell, index)` | Get the Nth value of a cell | `get(C1, 0)` |

## Chinese Formal Numeral Conversion

Converts amounts into formal Chinese numerals (used on invoices and checks):

```javascript
chn(12345.67)       // → "壹万贰仟叁佰肆拾伍元陆角柒分"
chnMoney(12345.67)  // → "壹万贰仟叁佰肆拾伍元陆角柒分"
```
