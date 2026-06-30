# SQLite GUI

A lightweight desktop browser for local SQLite databases, built with **Java 21 + JavaFX**.

![status](https://img.shields.io/badge/version-0.1.0-blue)

## Features

- **Browse & edit tables** — open a `.db` file, pick a table in the sidebar, and view rows in a paginated grid (200 rows/page). Edit cells inline, add rows, and delete rows. Edits are committed precisely via each row's `rowid`.
- **Run SQL** — a SQL editor (run with **Ctrl+Enter**) for arbitrary `SELECT`/`INSERT`/`UPDATE`/DDL. Results show in a grid; statements report affected-row counts.
- **Schema viewer** — the *Structure* tab lists columns, types, NOT NULL / primary-key flags, defaults, and the original `CREATE` statement.
- **Import / export** — import a CSV file into a new table (header row becomes columns), and export any table page or query result back to CSV.

`NULL` values are shown dimmed as `(null)` so they read differently from empty strings.

## Requirements

- **JDK 21+** (the project targets Java 21).
- No global Maven install needed — the repo ships a Maven wrapper (`mvnw` / `mvnw.cmd`).

## Run from source

```bash
# Windows
mvnw.cmd javafx:run

# macOS / Linux
./mvnw javafx:run
```

## Build a self-contained jar

```bash
./mvnw package
java -jar target/sqlite-gui-0.1.0-shaded.jar
```

The shaded jar bundles JavaFX and the SQLite JDBC driver, so it runs anywhere with a JDK installed.

## Project layout

```
src/main/java/com/sqlitegui/
  App.java              JavaFX entry point
  Launcher.java         plain main() used by the fat jar
  db/
    Database.java       JDBC wrapper: connect, introspect, query, edit
    QueryResult.java    columns + rows container
    ColumnInfo.java     PRAGMA table_info metadata
    CsvSupport.java     dependency-free CSV import/export
  ui/
    MainView.java       menus, schema sidebar, tab wiring
    DataTab.java        editable, paginated table browser
    SqlTab.java         SQL editor + results
    StructureTab.java   column metadata + DDL
    ResultGrid.java     dynamic read-only result table builder
    Dialogs.java        error/info/confirm + CSV file dialogs
src/main/resources/styles.css
```

## Notes & limits

- Tables declared `WITHOUT ROWID` are opened **read-only** (no stable rowid to target edits).
- CSV import creates all columns as `TEXT`; SQLite's dynamic typing means values still compare/sort sensibly, but cast in SQL if you need numeric ordering.
- The data grid pages at 200 rows; use the **SQL** tab for ad-hoc filtering of large tables.
