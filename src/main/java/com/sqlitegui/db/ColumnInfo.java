package com.sqlitegui.db;

/** Metadata for a single column, as reported by PRAGMA table_info. */
public final class ColumnInfo {
    private final int cid;
    private final String name;
    private final String type;
    private final boolean notNull;
    private final String defaultValue;
    private final boolean primaryKey;

    public ColumnInfo(int cid, String name, String type, boolean notNull,
                      String defaultValue, boolean primaryKey) {
        this.cid = cid;
        this.name = name;
        this.type = type;
        this.notNull = notNull;
        this.defaultValue = defaultValue;
        this.primaryKey = primaryKey;
    }

    public int getCid() { return cid; }
    public String getName() { return name; }
    public String getType() { return type; }
    public boolean isNotNull() { return notNull; }
    public String getDefaultValue() { return defaultValue; }
    public boolean isPrimaryKey() { return primaryKey; }
}
