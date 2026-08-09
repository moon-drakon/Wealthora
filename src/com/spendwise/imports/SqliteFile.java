package com.spendwise.imports;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads tables out of a SQLite database file.
 *
 * <p>Money Manager backups are SQLite databases, so importing one means reading
 * the file format directly. The reader is deliberately small: it opens the file
 * read-only, walks table b-trees, and decodes rows. It never writes, never
 * evaluates SQL, and never opens journal or write-ahead files, so the backup a
 * user selects cannot be modified by importing it.
 *
 * <p>Only plain rowid tables are supported, which is all a Money Manager backup
 * contains. Anything else is reported as unreadable rather than guessed at.
 */
public final class SqliteFile {

    /** SQLite files start with this exact string including its final zero byte. */
    static final byte[] MAGIC = signature();

    private static byte[] signature() {
        byte[] text = "SQLite format 3".getBytes(StandardCharsets.US_ASCII);
        byte[] magic = new byte[text.length + 1];
        System.arraycopy(text, 0, magic, 0, text.length);
        return magic;
    }

    private static final long MAX_FILE_BYTES = 256L * 1024L * 1024L;
    private static final int LEAF_TABLE = 13;
    private static final int INTERIOR_TABLE = 5;

    private final byte[] data;
    private final int pageSize;
    private final int usableSize;
    private final Map<String, TableDefinition> tables = new LinkedHashMap<>();

    private SqliteFile(byte[] data) {
        this.data = data;
        int declaredPageSize = readShort(16);
        this.pageSize = declaredPageSize == 1 ? 65536 : declaredPageSize;
        if (pageSize < 512 || Integer.bitCount(pageSize) != 1) {
            throw new SqliteFormatException("The database page size is invalid.");
        }
        int reserved = data[20] & 0xFF;
        this.usableSize = pageSize - reserved;
        if (usableSize < 480) {
            throw new SqliteFormatException(
                    "The database reserves too much space per page.");
        }
        int encoding = readInt(56);
        if (encoding != 0 && encoding != 1) {
            throw new SqliteFormatException(
                    "Only UTF-8 databases can be read.");
        }
        readSchema();
    }

    /** Returns true when the first bytes of {@code file} are the SQLite magic. */
    public static boolean hasSqliteSignature(byte[] header) {
        if (header == null || header.length < MAGIC.length) {
            return false;
        }
        for (int index = 0; index < MAGIC.length; index++) {
            if (header[index] != MAGIC[index]) {
                return false;
            }
        }
        return true;
    }

    /** Opens a database file for reading. The file is never modified. */
    public static SqliteFile open(Path file) {
        Path path = Objects.requireNonNull(file, "Database file is required.")
                .toAbsolutePath().normalize();
        try {
            long size = Files.size(path);
            if (size > MAX_FILE_BYTES) {
                throw new SqliteFormatException(
                        "The selected backup is too large to read.");
            }
            return open(Files.readAllBytes(path));
        } catch (IOException | SecurityException exception) {
            throw new SqliteFormatException(
                    "The selected backup could not be read.", exception);
        }
    }

    /** Opens a database already held in memory, such as a ZIP entry. */
    public static SqliteFile open(byte[] content) {
        byte[] bytes = Objects.requireNonNull(
                content, "Database content is required.");
        if (!hasSqliteSignature(bytes)) {
            throw new SqliteFormatException(
                    "The selected file is not a database backup.");
        }
        return new SqliteFile(bytes);
    }

    /** Table names in the order the database declares them. */
    public Set<String> tableNames() {
        return Set.copyOf(tables.keySet());
    }

    public boolean hasTable(String name) {
        return tables.containsKey(key(name));
    }

    /** Column names of {@code table} in stored order. */
    public List<String> columnNames(String table) {
        return definition(table).columns();
    }

    /**
     * Reads every row of {@code table} as a column-name to value map. Values are
     * {@link String}, {@link Long}, {@link BigDecimal}, {@code byte[]}, or null.
     */
    public List<Map<String, Object>> rows(String table) {
        TableDefinition definition = definition(table);
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        collect(definition, definition.rootPage(), rows, visited, 0);
        return rows;
    }

    // ------------------------------------------------------------------ schema

    private void readSchema() {
        TableDefinition schema = new TableDefinition("sqlite_master", 1,
                List.of("type", "name", "tbl_name", "rootpage", "sql"), -1);
        List<Map<String, Object>> rows = new ArrayList<>();
        collect(schema, 1, rows, new HashSet<>(), 0);
        for (Map<String, Object> row : rows) {
            if (!"table".equals(text(row.get("type")))) {
                continue;
            }
            String name = text(row.get("name"));
            String sql = text(row.get("sql"));
            Object rootPage = row.get("rootpage");
            if (name == null || sql == null || !(rootPage instanceof Long page)
                    || page <= 0) {
                continue;
            }
            if (sql.toUpperCase(Locale.ROOT).contains("WITHOUT ROWID")) {
                continue;
            }
            List<String> columns = parseColumns(sql);
            if (columns.isEmpty()) {
                continue;
            }
            tables.put(key(name), new TableDefinition(name, page.intValue(),
                    columns, rowidAliasIndex(sql, columns)));
        }
    }

    /**
     * Extracts column names from a CREATE TABLE statement. Table constraints and
     * anything the reader does not understand are ignored, which is safe because
     * only column order matters for decoding a record.
     */
    static List<String> parseColumns(String createTableSql) {
        int open = createTableSql.indexOf('(');
        int close = createTableSql.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return List.of();
        }
        String body = createTableSql.substring(open + 1, close);
        List<String> columns = new ArrayList<>();
        int depth = 0;
        StringBuilder part = new StringBuilder();
        for (int index = 0; index < body.length(); index++) {
            char character = body.charAt(index);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            }
            if (character == ',' && depth == 0) {
                addColumn(columns, part.toString());
                part.setLength(0);
            } else {
                part.append(character);
            }
        }
        addColumn(columns, part.toString());
        return List.copyOf(columns);
    }

    private static void addColumn(List<String> columns, String definition) {
        String trimmed = definition.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        for (String constraint : List.of("PRIMARY ", "UNIQUE ", "CHECK",
                "FOREIGN ", "CONSTRAINT ")) {
            if (upper.startsWith(constraint)) {
                return;
            }
        }
        String name = firstToken(trimmed);
        if (!name.isEmpty()) {
            columns.add(name);
        }
    }

    private static String firstToken(String definition) {
        char first = definition.charAt(0);
        if (first == '"' || first == '`' || first == '[') {
            char closing = first == '[' ? ']' : first;
            int end = definition.indexOf(closing, 1);
            return end < 0 ? "" : definition.substring(1, end);
        }
        int end = 0;
        while (end < definition.length()
                && !Character.isWhitespace(definition.charAt(end))) {
            end++;
        }
        return definition.substring(0, end);
    }

    /**
     * Finds a column declared {@code INTEGER PRIMARY KEY}. Such a column is not
     * stored in the record; its value is the row identifier.
     */
    private static int rowidAliasIndex(String sql, List<String> columns) {
        String upper = sql.toUpperCase(Locale.ROOT);
        for (int index = 0; index < columns.size(); index++) {
            String pattern = columns.get(index).toUpperCase(Locale.ROOT)
                    + " INTEGER PRIMARY KEY";
            if (upper.contains(pattern)) {
                return index;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------- b-tree walk

    private void collect(
            TableDefinition table,
            int pageNumber,
            List<Map<String, Object>> rows,
            Set<Integer> visited,
            int depth) {
        if (depth > 64) {
            throw new SqliteFormatException(
                    "The database structure is nested too deeply.");
        }
        if (!visited.add(pageNumber)) {
            throw new SqliteFormatException(
                    "The database structure contains a loop.");
        }
        int base = (pageNumber - 1) * pageSize;
        if (base < 0 || base + usableSize > data.length) {
            throw new SqliteFormatException("The database is truncated.");
        }
        int header = pageNumber == 1 ? base + 100 : base;
        int type = data[header] & 0xFF;
        int cellCount = readShort(header + 3);
        if (type == LEAF_TABLE) {
            int pointers = header + 8;
            for (int index = 0; index < cellCount; index++) {
                int cell = base + readShort(pointers + index * 2);
                rows.add(readLeafCell(table, cell));
            }
            return;
        }
        if (type != INTERIOR_TABLE) {
            throw new SqliteFormatException(
                    "The database contains an unsupported page type.");
        }
        int pointers = header + 12;
        for (int index = 0; index < cellCount; index++) {
            int cell = base + readShort(pointers + index * 2);
            collect(table, readInt(cell), rows, visited, depth + 1);
        }
        collect(table, readInt(header + 8), rows, visited, depth + 1);
    }

    private Map<String, Object> readLeafCell(TableDefinition table, int cell) {
        int cursor = cell;
        Varint payloadSize = readVarint(cursor);
        cursor += payloadSize.length();
        Varint rowId = readVarint(cursor);
        cursor += rowId.length();
        byte[] payload = readPayload(cursor, payloadSize.value());
        return decodeRecord(table, payload, rowId.value());
    }

    /**
     * Copies a record payload, following overflow pages when the record does not
     * fit on its page.
     */
    private byte[] readPayload(int start, long payloadSize) {
        if (payloadSize < 0 || payloadSize > MAX_FILE_BYTES) {
            throw new SqliteFormatException("A stored record is invalid.");
        }
        int maxLocal = usableSize - 35;
        int minLocal = ((usableSize - 12) * 32 / 255) - 23;
        int localSize;
        if (payloadSize <= maxLocal) {
            localSize = (int) payloadSize;
        } else {
            int candidate = (int) (minLocal
                    + ((payloadSize - minLocal) % (usableSize - 4)));
            localSize = candidate > maxLocal ? minLocal : candidate;
        }
        byte[] payload = new byte[(int) payloadSize];
        require(start + localSize <= data.length);
        System.arraycopy(data, start, payload, 0, localSize);
        int written = localSize;
        if (written == payloadSize) {
            return payload;
        }
        int overflow = readInt(start + localSize);
        Set<Integer> seen = new HashSet<>();
        while (written < payloadSize) {
            if (overflow <= 0 || !seen.add(overflow)) {
                throw new SqliteFormatException(
                        "A stored record has a broken overflow chain.");
            }
            int base = (overflow - 1) * pageSize;
            require(base >= 0 && base + usableSize <= data.length);
            int chunk = Math.min(usableSize - 4, (int) payloadSize - written);
            System.arraycopy(data, base + 4, payload, written, chunk);
            written += chunk;
            overflow = readInt(base);
        }
        return payload;
    }

    private Map<String, Object> decodeRecord(
            TableDefinition table, byte[] payload, long rowId) {
        LinkedHashMap<String, Object> record = new LinkedHashMap<>();
        Varint headerSize = readVarint(payload, 0);
        int typeCursor = headerSize.length();
        int valueCursor = (int) headerSize.value();
        if (valueCursor < 0 || valueCursor > payload.length) {
            throw new SqliteFormatException("A stored record header is invalid.");
        }
        List<String> columns = table.columns();
        for (int index = 0; index < columns.size(); index++) {
            if (typeCursor >= valueCursor) {
                record.put(columns.get(index),
                        index == table.rowidAlias() ? rowId : null);
                continue;
            }
            Varint serial = readVarint(payload, typeCursor);
            typeCursor += serial.length();
            ValueAndSize decoded = decodeValue(
                    payload, valueCursor, serial.value());
            valueCursor += decoded.size();
            Object value = index == table.rowidAlias() && decoded.value() == null
                    ? rowId
                    : decoded.value();
            record.put(columns.get(index), value);
        }
        return record;
    }

    private ValueAndSize decodeValue(byte[] payload, int offset, long serial) {
        if (serial == 0) {
            return new ValueAndSize(null, 0);
        }
        if (serial == 8) {
            return new ValueAndSize(0L, 0);
        }
        if (serial == 9) {
            return new ValueAndSize(1L, 0);
        }
        if (serial >= 1 && serial <= 6) {
            int size = switch ((int) serial) {
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 3;
                case 4 -> 4;
                case 5 -> 6;
                default -> 8;
            };
            return new ValueAndSize(
                    readSignedInteger(payload, offset, size), size);
        }
        if (serial == 7) {
            require(offset + 8 <= payload.length);
            long bits = readSignedInteger(payload, offset, 8);
            return new ValueAndSize(
                    BigDecimal.valueOf(Double.longBitsToDouble(bits)), 8);
        }
        if (serial < 12) {
            throw new SqliteFormatException(
                    "A stored value uses an unsupported type.");
        }
        int size = (int) ((serial - (serial % 2 == 0 ? 12 : 13)) / 2);
        require(offset + size <= payload.length);
        byte[] slice = new byte[size];
        System.arraycopy(payload, offset, slice, 0, size);
        Object value = serial % 2 == 0
                ? slice
                : new String(slice, StandardCharsets.UTF_8);
        return new ValueAndSize(value, size);
    }

    private static long readSignedInteger(byte[] payload, int offset, int size) {
        require(offset + size <= payload.length);
        long value = payload[offset] & 0xFF;
        if ((value & 0x80) != 0) {
            value -= 256;
        }
        for (int index = 1; index < size; index++) {
            value = (value << 8) | (payload[offset + index] & 0xFF);
        }
        return value;
    }

    // ---------------------------------------------------------------- decoding

    private Varint readVarint(int offset) {
        return readVarint(data, offset);
    }

    private static Varint readVarint(byte[] bytes, int offset) {
        long value = 0;
        for (int index = 0; index < 8; index++) {
            require(offset + index < bytes.length);
            int current = bytes[offset + index] & 0xFF;
            value = (value << 7) | (current & 0x7F);
            if ((current & 0x80) == 0) {
                return new Varint(value, index + 1);
            }
        }
        require(offset + 8 < bytes.length);
        value = (value << 8) | (bytes[offset + 8] & 0xFF);
        return new Varint(value, 9);
    }

    private int readShort(int offset) {
        require(offset + 2 <= data.length);
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private int readInt(int offset) {
        require(offset + 4 <= data.length);
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new SqliteFormatException(
                    "The backup file is incomplete or damaged.");
        }
    }

    private TableDefinition definition(String table) {
        TableDefinition definition = tables.get(key(table));
        if (definition == null) {
            throw new SqliteFormatException(
                    "The backup does not contain the expected data.");
        }
        return definition;
    }

    private static String key(String name) {
        return name == null ? "" : name.toUpperCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private record TableDefinition(
            String name, int rootPage, List<String> columns, int rowidAlias) {
    }

    private record Varint(long value, int length) {
    }

    private record ValueAndSize(Object value, int size) {
    }
}
