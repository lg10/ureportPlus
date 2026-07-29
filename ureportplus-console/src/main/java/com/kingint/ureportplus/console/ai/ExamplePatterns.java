package com.kingint.ureportplus.console.ai;

/**
 * Common UReportPlus report patterns distilled from built-in examples.
 * This is injected into the AI's system prompt so it can quickly build
 * reports matching common Chinese-style reporting patterns.
 */
public class ExamplePatterns {

    /** Singleton pattern reference. */
    public static String getPatternReference() {
        StringBuilder sb = new StringBuilder();
        sb.append("## UReportPlus 报表模式参考\n");
        sb.append("以下是已验证的通用报表模式，你可以直接使用:\n\n");

        sb.append("### 模式1: 简单表格 (纵向分组)\n");
        sb.append("结构: 表头行(标题) → 列头行 → 数据行(向下展开)\n");
        sb.append("- 第1行: 跨列合并的报表标题\n");
        sb.append("- 第2行: 各列的表头文字 (静态文本)\n");
        sb.append("- 第3行及以后: 数据集字段, expand=Down\n");
        sb.append("示例: example01-simple-table\n\n");

        sb.append("### 模式2: 纵向分组+小计\n");
        sb.append("结构: 分组字段(expand=Down) → 明细字段(expand=Down) → 小计(expand=None, leftParent=分组格)\n");
        sb.append("- 分组格: aggregate=group, expand=Down\n");
        sb.append("- 数值格: aggregate=sum/count/avg, expand=None, leftParent=分组格名\n");
        sb.append("- 小计行: 对分组范围内的数值聚合\n");
        sb.append("示例: example02-vertical-group\n\n");

        sb.append("### 模式3: 纵向分组+合并行\n");
        sb.append("结构: 合并的分组列 + 不合并的明细列\n");
        sb.append("- 酒店名列: aggregate=group, expand=Down (自动合并)\n");
        sb.append("- 房型列: aggregate=select, expand=Down (每行展开)\n");
        sb.append("- 数值列: aggregate=sum, expand=None\n");
        sb.append("示例: example03-horizontal-group\n\n");

        sb.append("### 模式4: 交叉表 (纵×横)\n");
        sb.append("结构: 行分组(expand=Down) × 列分组(expand=Right) → 交叉数据(expand=None)\n");
        sb.append("- 左上角格: leftParent=None, topParent=None\n");
        sb.append("- 行分组: expand=Down, 垂直展开\n");
        sb.append("- 列分组: expand=Right, 水平展开\n");
        sb.append("- 交叉值: leftParent=行分组格, topParent=列分组格, aggregate=sum\n");
        sb.append("示例: example05-cross-tab, example10-cross-group\n\n");

        sb.append("### 模式5: 表达式计算\n");
        sb.append("常用表达式:\n");
        sb.append("- 计算列: type=expression, value如 'B3 * C3' 或 'ds.sum(revenue) * 0.13'\n");
        sb.append("- 条件格式: if(A1>1000, '高', '低')\n");
        sb.append("- 格式化: formatNumber(A1, '#,##0.00')\n");
        sb.append("示例: example04-expressions, example06-more-expressions\n\n");

        sb.append("### 模式6: 分页打印\n");
        sb.append("使用 page()/pages() 函数显示页脚, pageSum()/pageCount() 进行本页聚合\n");
        sb.append("示例: example08-page-functions\n\n");

        sb.append("### 模式7: URL参数\n");
        sb.append("使用 param('paramName') 或 param('paramName', '默认值') 获取URL参数\n");
        sb.append("示例: example07-url-params\n\n");

        sb.append("### 关键规则\n");
        sb.append("1. 表格至少有表头行(第1-2行)和数据行\n");
        sb.append("2. 分组的单元格 expand=Down 或 Right\n");
        sb.append("3. 聚合的单元格 expand=None, 通过 leftParent/topParent 限定范围\n");
        sb.append("4. 单元格命名: 列字母+行号 (A1, B2, C3...)\n");
        sb.append("5. dataset类型的 value 不直接写, 而是通过 datasetId+aggregate+property 组合\n");
        sb.append("6. expression类型的 value 写完整表达式\n");
        sb.append("7. simple类型的 value 写显示文本\n");
        sb.append("8. 如果需要比当前更多行/列, 生成 structuralOps insertRow/insertCol\n");

        return sb.toString();
    }
}
