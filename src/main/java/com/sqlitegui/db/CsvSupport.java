package com.sqlitegui.db;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, dependency-free RFC-4180-style CSV reader/writer plus helpers to
 * import a CSV file into a new SQLite table and export query results out.
 */
public final class CsvSupport {

    private CsvSupport() {
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /** Parses a single CSV line into fields, honouring quotes and escaped quotes. */
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    private static String encodeField(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    /**
     * Imports a CSV stream into a brand-new table. The first row is treated as
     * the header and every column is created as TEXT. Returns the number of
     * data rows inserted.
     */
    public static int importToNewTable(Database db, Reader source, String tableName, boolean firstRowIsHeader)
            throws IOException, SQLException {
        Connection conn = db.getConnection();
        try (BufferedReader reader = new BufferedReader(source)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("CSV file is empty");
            }
            List<String> headers = parseLine(headerLine);

            List<String> columnNames = new ArrayList<>();
            if (firstRowIsHeader) {
                for (int i = 0; i < headers.size(); i++) {
                    String h = headers.get(i).trim();
                    columnNames.add(h.isEmpty() ? "column" + (i + 1) : h);
                }
            } else {
                for (int i = 0; i < headers.size(); i++) {
                    columnNames.add("column" + (i + 1));
                }
            }

            createTextTable(conn, tableName, columnNames);

            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            int inserted = 0;
            try {
                String insertSql = buildInsert(tableName, columnNames);
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    if (!firstRowIsHeader) {
                        bindAndAdd(ps, headers, columnNames.size());
                        inserted++;
                    }
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            continue;
                        }
                        bindAndAdd(ps, parseLine(line), columnNames.size());
                        inserted++;
                        if (inserted % 1000 == 0) {
                            ps.executeBatch();
                        }
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
            return inserted;
        }
    }

    private static void bindAndAdd(PreparedStatement ps, List<String> values, int columnCount)
            throws SQLException {
        for (int i = 0; i < columnCount; i++) {
            String v = i < values.size() ? values.get(i) : null;
            ps.setString(i + 1, v);
        }
        ps.addBatch();
    }

    private static void createTextTable(Connection conn, String tableName, List<String> columns)
            throws SQLException {
        StringBuilder sb = new StringBuilder("CREATE TABLE ")
                .append(Database.quoteIdent(tableName))
                .append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(Database.quoteIdent(columns.get(i))).append(" TEXT");
        }
        sb.append(")");
        try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            ps.executeUpdate();
        }
    }

    private static String buildInsert(String tableName, List<String> columns) {
        StringBuilder sb = new StringBuilder("INSERT INTO ")
                .append(Database.quoteIdent(tableName))
                .append(" (");
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
                placeholders.append(", ");
            }
            sb.append(Database.quoteIdent(columns.get(i)));
            placeholders.append("?");
        }
        sb.append(") VALUES (").append(placeholders).append(")");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    /** Writes a query result to a CSV stream, including a header row. */
    public static void export(QueryResult result, Writer sink) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(sink)) {
            List<String> columns = result.getColumns();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    writer.write(',');
                }
                writer.write(encodeField(columns.get(i)));
            }
            writer.write("\r\n");
            for (Object[] row : result.getRows()) {
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) {
                        writer.write(',');
                    }
                    writer.write(encodeField(row[i]));
                }
                writer.write("\r\n");
            }
        }
    }
}
