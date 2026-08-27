package com.kingint.ureportplus.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kingint.ureportplus.definition.Band;
import com.kingint.ureportplus.definition.Expand;
import com.kingint.ureportplus.definition.value.AggregateType;
import com.kingint.ureportplus.definition.value.DatasetValue;
import com.kingint.ureportplus.definition.value.Value;
import com.kingint.ureportplus.model.Cell;
import com.kingint.ureportplus.model.Column;
import com.kingint.ureportplus.model.Report;
import com.kingint.ureportplus.model.Row;

/**
 * Handles cloning of Band.subtotal rows for each group in a Down-expanded report.
 * Subtotals are inserted before each subsequent group header instance.
 */
public class SubtotalBuilder {
	private static final Logger log = LoggerFactory.getLogger(SubtotalBuilder.class);

	public static List<Cell> findGroupHeaders(Report report) {
		List<Cell> result = new ArrayList<Cell>();
		Map<Row, Map<Column, Cell>> rowColCellMap = report.getRowColCellMap();
		for (Row row : report.getRows()) {
			if (row.getBand() != null) continue;
			Map<Column, Cell> colMap = rowColCellMap.get(row);
			if (colMap == null) continue;
			for (Cell cell : colMap.values()) {
				if (cell == null) continue;
				if (Expand.Down.equals(cell.getExpand())) {
					Value value = cell.getValue();
					if (value instanceof DatasetValue) {
						DatasetValue dv = (DatasetValue) value;
						if (AggregateType.group.equals(dv.getAggregate()) && !isGroupCell(cell.getLeftParentCell())) {
							result.add(cell);
						}
					}
				}
			}
		}
		return result;
	}

	public static Map<String, List<Cell>> groupByName(List<Cell> cells) {
		Map<String, List<Cell>> map = new HashMap<String, List<Cell>>();
		for (Cell cell : cells) {
			String name = cell.getName();
			List<Cell> list = map.get(name);
			if (list == null) {
				list = new ArrayList<Cell>();
				map.put(name, list);
			}
			list.add(cell);
		}
		for (List<Cell> list : map.values()) {
			Collections.sort(list, new Comparator<Cell>() {
				public int compare(Cell a, Cell b) {
					return Integer.compare(a.getRow().getRowNumber(), b.getRow().getRowNumber());
				}
			});
		}
		return map;
	}

	public static void build(Report report, Context context) {
		List<Row> rows = report.getRows();

		List<Row> allSubtotalRows = new ArrayList<Row>();
		for (Row row : rows) {
			if (Band.subtotal.equals(row.getBand())) {
				allSubtotalRows.add(row);
			}
		}
		if (allSubtotalRows.isEmpty()) {
			return;
		}

		Row templateRow = allSubtotalRows.get(0);
		Map<Row, Map<Column, Cell>> rowColCellMap = report.getRowColCellMap();
		Map<Column, Cell> templateColMap = rowColCellMap.get(templateRow);
		if (templateColMap == null || templateColMap.isEmpty()) {
			for (Row r : allSubtotalRows) {
				Map<Column, Cell> cm = rowColCellMap.get(r);
				if (cm != null && !cm.isEmpty()) {
					templateRow = r;
					templateColMap = cm;
					break;
				}
			}
			if (templateColMap == null || templateColMap.isEmpty()) {
				return;
			}
		}

		rows.removeAll(allSubtotalRows);
		List<Row> summaryRows = report.getSummaryRows();
		summaryRows.removeAll(allSubtotalRows);

		List<Cell> groupHeaders = findGroupHeaders(report);
		if (groupHeaders.isEmpty()) return;

		Map<String, List<Cell>> groupsByName = groupByName(groupHeaders);

		for (Map.Entry<String, List<Cell>> entry : groupsByName.entrySet()) {
			List<Cell> instances = entry.getValue();
			if (instances.size() <= 1) continue;

			for (int i = instances.size() - 1; i >= 0; i--) {
				Cell groupCell = instances.get(i);
				Row clone = cloneSubtotalRow(templateRow, report, context, groupCell);
				if (i == instances.size() - 1) {
					rows.add(clone);
				} else {
					int beforeRow = instances.get(i + 1).getRow().getRowNumber();
					rows.add(beforeRow - 1, clone);
				}
			}
		}
	}

	private static Row cloneSubtotalRow(Row templateRow, Report report, Context context, Cell groupCell) {
		Row newRow = templateRow.newRow();
		newRow.setBand(Band.subtotal);

		Map<Row, Map<Column, Cell>> rowColCellMap = report.getRowColCellMap();
		Map<Column, Cell> templateColMap = rowColCellMap.get(templateRow);
		Map<Column, Cell> newColMap = new HashMap<Column, Cell>();
		rowColCellMap.put(newRow, newColMap);

		if (templateColMap != null) {
			for (Column col : report.getColumns()) {
				Cell templateCell = templateColMap.get(col);
				if (templateCell == null) continue;

				Cell newCell = templateCell.newCell();
				newCell.setRow(newRow);
				newCell.setColumn(col);
				col.getCells().add(newCell);
				newRow.getCells().add(newCell);
				newColMap.put(col, newCell);

				newCell.setLeftParentCell(groupCell);
				groupCell.addRowChild(newCell);

				try {
					List<BindData> dataList = DataCompute.buildCellData(newCell, context);
					if (dataList != null && !dataList.isEmpty()) {
						BindData bd = dataList.get(0);
						newCell.setData(bd.getValue());
						newCell.setBindData(bd.getDataList());
						newCell.doFormat();
					}
				} catch (Exception e) {
					log.debug("Failed to compute subtotal cell {}: {}", newCell.getName(), e.getMessage());
				}

				report.addCell(newCell);
			}
		}
		return newRow;
	}

	private static boolean isGroupCell(Cell cell) {
		if (cell == null) return false;
		if (!Expand.Down.equals(cell.getExpand())) return false;
		Value v = cell.getValue();
		return v instanceof DatasetValue && AggregateType.group.equals(((DatasetValue)v).getAggregate());
	}
}
