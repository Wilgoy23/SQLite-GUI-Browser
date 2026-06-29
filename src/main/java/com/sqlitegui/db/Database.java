package com.sqlitegui.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around a single SQLite JDBC connection. All database access in
 * the app goes through one instance of this class.
 */
public final class Database implements AutoCloseable {

    private final Connection conn;
    private final String path;

    private Database(Connection conn, String path) {
        this.conn = conn;
        this.path = path;
    }

    /** Opens (or creates) the SQLite database file at the given path. */
    public static Database open(String filePath) throws SQLException {
        // The xerial driver registers itself automatically, but be explicit so a
        // missing driver fails fast with a clear message.
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found on classpath", e);
        }
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + filePath);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
        }
        return new Database(c, filePath);
    }

    public String getPath() {
        return path;
    }

    // ------------------------------------------------------------------
    // Schema introspection
    // ------------------------------------------------------------------

    public List<String> getTables() throws SQLException {
        return objectNames("table");
    }

    public List<String> getViews() throws SQLException {
        return objectNames("view");
    }

    private List<String> objectNames(String type) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM sqlite_master WHERE type = ? "
                + "AND name NOT LIKE 'sqlite_%' ORDER BY name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        return names;
    }

    public List<ColumnInfo> getColumns(String table) throws SQLException {
        List<ColumnInfo> cols = new ArrayList<>();
        // PRAGMA does not support bound parameters; quote the identifier instead.
        String sql = "PRAGMA table_info(" + quoteIdent(table) + ")";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                cols.add(new ColumnInfo(
                        rs.getInt("cid"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getInt("notnull") != 0,
                        rs.getString("dflt_value"),
                        rs.getInt("pk") != 0));
            }
        }
        return cols;
    }

    /** Returns the CREATE statement stored for a table or view. */
    public String getDdl(String name) throws SQLException {
        String sql = "SELECT sql FROM sqlite_master WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return "";
    }

    public long getRowCount(String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + quoteIdent(table))) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    // ------------------------------------------------------------------
    // Data access & editing
    // ------------------------------------------------------------------

    /**
     * Reads a page of rows from a table. The first column of every returned row
     * is the SQLite rowid (aliased {@value #ROWID_ALIAS}); the UI hides it but
     * uses it to target precise UPDATE/DELETE statements.
     */
    public static final String ROWID_ALIAS = "__rowid__";

    public QueryResult getTableData(String table, int limit, int offset) throws SQLException {
        String sql = "SELECT rowid AS " + ROWID_ALIAS + ", * FROM " + quoteIdent(table)
                + " LIMIT " + limit + " OFFSET " + offset;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return materialize(rs);
        }
    }

    /** Updates a single cell, located by its rowid. */
    public void updateCell(String table, Object rowId, String column, Object value) throws SQLException {
        String sql = "UPDATE " + quoteIdent(table) + " SET " + quoteIdent(column)
                + " = ? WHERE rowid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, value);
            ps.setObject(2, rowId);
            ps.executeUpdate();
        }
    }

    /** Deletes a row by its rowid. */
    public void deleteRow(String table, Object rowId) throws SQLException {
        String sql = "DELETE FROM " + quoteIdent(table) + " WHERE rowid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, rowId);
            ps.executeUpdate();
        }
    }

    /** Inserts an empty row (all defaults/NULLs) and returns its new rowid. */
    public long insertEmptyRow(String table) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO " + quoteIdent(table) + " DEFAULT VALUES");
            try (ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        }
    }

    // ------------------------------------------------------------------
    // Arbitrary SQL
    // ------------------------------------------------------------------

    /**
     * Executes an arbitrary SQL statement. Returns a result set for queries, or
     * an update count for everything else.
     */
    public QueryResult execute(String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            boolean hasResultSet = st.execute(sql);
            if (hasResultSet) {
                try (ResultSet rs = st.getResultSet()) {
                    return materialize(rs);
                }
            } else {
                return QueryResult.ofUpdate(st.getUpdateCount());
            }
        }
    }

    private QueryResult materialize(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        List<String> columns = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            columns.add(md.getColumnLabel(i));
        }
        List<Object[]> rows = new ArrayList<>();
        while (rs.next()) {
            Object[] row = new Object[n];
            for (int i = 1; i <= n; i++) {
                row[i - 1] = rs.getObject(i);
            }
            rows.add(row);
        }
        return QueryResult.ofRows(columns, rows);
    }

    // ------------------------------------------------------------------
    // Transactions (used by bulk import)
    // ------------------------------------------------------------------

    public Connection getConnection() {
        return conn;
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Safely quotes a SQL identifier (table/column name) by doubling embedded quotes. */
    public static String quoteIdent(String ident) {
        return '"' + ident.replace("\"", "\"\"") + '"';
    }
}
