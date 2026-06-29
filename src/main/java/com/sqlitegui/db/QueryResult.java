package com.sqlitegui.db;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple, UI-friendly container for the result of a query: an ordered list of
 * column names plus the rows (each row is an array of cell values, kept as
 * Object so NULLs and the original types survive until the UI stringifies them).
 */
public final class QueryResult {

    private final List<String> columns;
    private final List<Object[]> rows;
    private final int updateCount;
    private final boolean isUpdate;

    private QueryResult(List<String> columns, List<Object[]> rows, int updateCount, boolean isUpdate) {
        this.columns = columns;
        this.rows = rows;
        this.updateCount = updateCount;
        this.isUpdate = isUpdate;
    }

    public static QueryResult ofRows(List<String> columns, List<Object[]> rows) {
        return new QueryResult(columns, rows, -1, false);
    }

    public static QueryResult ofUpdate(int updateCount) {
        return new QueryResult(new ArrayList<>(), new ArrayList<>(), updateCount, true);
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<Object[]> getRows() {
        return rows;
    }

    /** Number of affected rows for non-SELECT statements, or -1 for result sets. */
    public int getUpdateCount() {
        return updateCount;
    }

    /** True when this represents an INSERT/UPDATE/DELETE/DDL statement. */
    public boolean isUpdate() {
        return isUpdate;
    }

    public int getRowCount() {
        return rows.size();
    }
}
