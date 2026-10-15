package org.gameyfin.app.core.backup

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.JsonNode
import java.sql.*
import java.time.*
import java.util.*

/**
 * Vendor-neutral classification of JDBC column types used to (de)serialize table data.
 */
internal enum class ValueKind {
    INTEGER, DECIMAL, FLOAT, BOOLEAN, STRING, DATE, TIME, TIMESTAMP, TIMESTAMP_TZ, BINARY, OTHER;

    companion object {
        fun fromJdbcType(jdbcType: Int): ValueKind = when (jdbcType) {
            Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> INTEGER
            Types.DECIMAL, Types.NUMERIC -> DECIMAL
            Types.REAL, Types.FLOAT, Types.DOUBLE -> FLOAT
            Types.BOOLEAN, Types.BIT -> BOOLEAN
            Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.CLOB,
            Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.NCLOB -> STRING

            Types.DATE -> DATE
            Types.TIME -> TIME
            Types.TIMESTAMP -> TIMESTAMP
            Types.TIMESTAMP_WITH_TIMEZONE -> TIMESTAMP_TZ
            Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> BINARY
            else -> OTHER
        }
    }
}

internal object JdbcValues {

    /**
     * Reads the value of column [index] from [rs] and writes it to [gen] in a vendor-neutral representation.
     */
    fun write(rs: ResultSet, index: Int, kind: ValueKind, gen: JsonGenerator) {
        when (kind) {
            ValueKind.INTEGER -> rs.getLong(index).let { if (rs.wasNull()) gen.writeNull() else gen.writeNumber(it) }
            ValueKind.DECIMAL -> rs.getBigDecimal(index)?.let { gen.writeNumber(it) } ?: gen.writeNull()
            ValueKind.FLOAT -> rs.getDouble(index).let { if (rs.wasNull()) gen.writeNull() else gen.writeNumber(it) }
            ValueKind.BOOLEAN -> rs.getBoolean(index)
                .let { if (rs.wasNull()) gen.writeNull() else gen.writeBoolean(it) }

            ValueKind.DATE -> writeString(gen, rs.getObject(index, LocalDate::class.java))
            ValueKind.TIME -> writeString(gen, rs.getObject(index, LocalTime::class.java))
            ValueKind.TIMESTAMP -> writeString(gen, rs.getObject(index, LocalDateTime::class.java))
            ValueKind.TIMESTAMP_TZ -> writeString(gen, rs.getObject(index, OffsetDateTime::class.java))
            ValueKind.BINARY -> writeString(gen, rs.getBytes(index)?.let { Base64.getEncoder().encodeToString(it) })
            ValueKind.STRING, ValueKind.OTHER -> writeString(gen, rs.getString(index))
        }
    }

    private fun writeString(gen: JsonGenerator, value: Any?) {
        if (value == null) gen.writeNull() else gen.writeString(value.toString())
    }

    /**
     * Binds a value read from a backup to parameter [index] of [ps], converting it to the type of the target column.
     */
    fun bind(ps: PreparedStatement, index: Int, jdbcType: Int, value: JsonNode?) {
        if (value == null || value.isNull) {
            ps.setNull(index, jdbcType)
            return
        }

        when (ValueKind.fromJdbcType(jdbcType)) {
            ValueKind.INTEGER -> when (jdbcType) {
                Types.BIGINT -> ps.setLong(index, toLong(value))
                Types.INTEGER -> ps.setInt(index, toLong(value).toInt())
                else -> ps.setShort(index, toLong(value).toShort())
            }

            ValueKind.DECIMAL -> ps.setBigDecimal(index, value.decimalValue())
            ValueKind.FLOAT -> ps.setDouble(index, value.asDouble())
            ValueKind.BOOLEAN -> ps.setBoolean(index, toBoolean(value))
            ValueKind.STRING -> ps.setString(index, value.asString())
            ValueKind.DATE -> ps.setObject(index, LocalDate.parse(value.asString()))
            ValueKind.TIME -> ps.setObject(index, LocalTime.parse(value.asString()))
            ValueKind.TIMESTAMP -> ps.setObject(index, toLocalDateTime(value.asString()))
            ValueKind.TIMESTAMP_TZ -> ps.setObject(index, toOffsetDateTime(value.asString()))
            ValueKind.BINARY -> ps.setBytes(index, Base64.getDecoder().decode(value.asString()))
            ValueKind.OTHER -> ps.setObject(index, value.asString(), jdbcType)
        }
    }

    private fun toLong(value: JsonNode): Long = when {
        value.isBoolean -> if (value.asBoolean()) 1 else 0
        value.isNumber -> value.asLong()
        else -> value.asString().trim().toLong()
    }

    private fun toBoolean(value: JsonNode): Boolean = when {
        value.isBoolean -> value.asBoolean()
        value.isNumber -> value.asLong() != 0L
        else -> value.asString().trim().let { it.equals("true", ignoreCase = true) || it == "1" }
    }

    private fun toLocalDateTime(value: String): LocalDateTime = try {
        LocalDateTime.parse(value)
    } catch (_: DateTimeException) {
        OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
    }

    private fun toOffsetDateTime(value: String): OffsetDateTime = try {
        OffsetDateTime.parse(value)
    } catch (_: DateTimeException) {
        LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toOffsetDateTime()
    }
}

/**
 * Small helper around [DatabaseMetaData] to work with the tables of the current schema in a vendor-neutral way.
 */
internal class SchemaInspector(private val connection: Connection) {
    private val metaData: DatabaseMetaData = connection.metaData
    private val catalog: String? = connection.catalog
    private val schema: String? = connection.schema
    private val quote: String = metaData.identifierQuoteString?.trim()?.takeIf { it.isNotEmpty() } ?: "\""

    data class Column(val name: String, val jdbcType: Int, val autoIncrement: Boolean)

    private val escape: String = metaData.searchStringEscape ?: ""

    fun quote(identifier: String) = "$quote${identifier.replace(quote, quote + quote)}$quote"

    /** Escapes wildcards (e.g. `_`) in a name used as metadata search pattern */
    private fun pattern(name: String) =
        if (escape.isEmpty()) name else name.replace(escape, escape + escape).replace("_", "${escape}_")
            .replace("%", "$escape%")

    /** All application tables of the current schema (migration bookkeeping tables are excluded) */
    fun tables(): List<String> =
        metaData.getTables(catalog, schema, "%", arrayOf("TABLE")).use { rs ->
            buildList { while (rs.next()) add(rs.getString("TABLE_NAME")) }
        }.filterNot { BackupFormat.isExcludedTable(it) }.sorted()

    fun columns(table: String): List<Column> =
        metaData.getColumns(catalog, schema?.let { pattern(it) }, pattern(table), "%").use { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        Column(
                            name = rs.getString("COLUMN_NAME"),
                            jdbcType = rs.getInt("DATA_TYPE"),
                            autoIncrement = rs.getString("IS_AUTOINCREMENT").equals("YES", ignoreCase = true)
                        )
                    )
                }
            }
        }

    /**
     * Sorts tables so that tables referenced by foreign keys come before the tables referencing them.
     * Self-references are ignored; tables that are part of a cycle are appended in alphabetical order.
     */
    fun sortByDependencies(tables: List<String>): List<String> {
        val dependencies = tables.associateWith { table ->
            metaData.getImportedKeys(catalog, schema, table).use { rs ->
                buildSet { while (rs.next()) add(rs.getString("PKTABLE_NAME")) }
            }.filter { it != table && it in tables }.toMutableSet()
        }

        val sorted = mutableListOf<String>()
        val remaining = tables.sorted().toMutableList()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { table -> dependencies.getValue(table).all { it in sorted } }
            if (ready.isEmpty()) {
                sorted.addAll(remaining)
                break
            }
            sorted.addAll(ready)
            remaining.removeAll(ready)
        }
        return sorted
    }

    data class Sequence(val name: String, val increment: Long)

    /** All sequences of the current schema */
    fun sequences(): List<Sequence> =
        connection.prepareStatement(
            "SELECT SEQUENCE_NAME, INCREMENT FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_SCHEMA = ?"
        ).use { ps ->
            ps.setString(1, schema)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(Sequence(rs.getString(1), rs.getString(2)?.trim()?.toLongOrNull() ?: 1))
                }
            }
        }
}
