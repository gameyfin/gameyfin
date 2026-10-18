package org.gameyfin.app.system.backup

import io.mockk.mockk
import io.mockk.verify
import org.gameyfin.app.core.backup.BackupFormat
import org.gameyfin.app.core.backup.JdbcValues
import org.gameyfin.app.core.backup.SchemaInspector
import org.gameyfin.app.core.backup.ValueKind
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types
import java.util.*

class JdbcSupportTest {

    private lateinit var connection: Connection

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:h2:mem:jdbcsupport-${UUID.randomUUID()}", "sa", "")
        connection.createStatement().use {
            it.execute("CREATE TABLE PARENT (ID BIGINT PRIMARY KEY)")
            it.execute("CREATE TABLE CHILD (ID BIGINT PRIMARY KEY, PARENT_ID BIGINT REFERENCES PARENT(ID), SELF_ID BIGINT REFERENCES CHILD(ID))")
            it.execute("CREATE TABLE GRANDCHILD (ID BIGINT PRIMARY KEY, CHILD_ID BIGINT REFERENCES CHILD(ID))")
            it.execute("CREATE TABLE \"my_table\" (ID INT GENERATED ALWAYS AS IDENTITY, V VARCHAR(10))")
            it.execute("CREATE SEQUENCE SEQ_A INCREMENT BY 5")
            it.execute(
                """CREATE TABLE VALS (
                    I BIGINT, D DECIMAL(10,2), F DOUBLE, B BOOLEAN, S VARCHAR(50), DT DATE, T TIME,
                    TS TIMESTAMP, TSTZ TIMESTAMP WITH TIME ZONE, BIN VARBINARY(20), C CLOB)"""
            )
        }
    }

    @AfterEach
    fun tearDown() = connection.close()

    private fun json(kind: ValueKind, column: String, insertSql: String): String {
        connection.createStatement().use { it.execute("DELETE FROM VALS") }
        connection.createStatement().use { it.execute(insertSql) }
        connection.createStatement().use { st ->
            st.executeQuery("SELECT $column FROM VALS").use { rs ->
                rs.next()
                val writer = StringWriter()
                BackupFormat.jsonMapper.createGenerator(writer).use { gen ->
                    gen.writeStartArray()
                    JdbcValues.write(rs, 1, kind, gen)
                    gen.writeEndArray()
                }
                return writer.toString()
            }
        }
    }

    @Test
    fun `fromJdbcType classifies types`() {
        assertEquals(ValueKind.INTEGER, ValueKind.fromJdbcType(Types.BIGINT))
        assertEquals(ValueKind.DECIMAL, ValueKind.fromJdbcType(Types.NUMERIC))
        assertEquals(ValueKind.FLOAT, ValueKind.fromJdbcType(Types.DOUBLE))
        assertEquals(ValueKind.BOOLEAN, ValueKind.fromJdbcType(Types.BIT))
        assertEquals(ValueKind.STRING, ValueKind.fromJdbcType(Types.CLOB))
        assertEquals(ValueKind.DATE, ValueKind.fromJdbcType(Types.DATE))
        assertEquals(ValueKind.TIME, ValueKind.fromJdbcType(Types.TIME))
        assertEquals(ValueKind.TIMESTAMP, ValueKind.fromJdbcType(Types.TIMESTAMP))
        assertEquals(ValueKind.TIMESTAMP_TZ, ValueKind.fromJdbcType(Types.TIMESTAMP_WITH_TIMEZONE))
        assertEquals(ValueKind.BINARY, ValueKind.fromJdbcType(Types.BLOB))
        assertEquals(ValueKind.OTHER, ValueKind.fromJdbcType(Types.OTHER))
    }

    @Test
    fun `write serializes values and nulls`() {
        assertEquals("[42]", json(ValueKind.INTEGER, "I", "INSERT INTO VALS (I) VALUES (42)"))
        assertEquals("[null]", json(ValueKind.INTEGER, "I", "INSERT INTO VALS (I) VALUES (NULL)"))
        assertEquals("[1.50]", json(ValueKind.DECIMAL, "D", "INSERT INTO VALS (D) VALUES (1.5)"))
        assertEquals("[null]", json(ValueKind.DECIMAL, "D", "INSERT INTO VALS (D) VALUES (NULL)"))
        assertEquals("[2.5]", json(ValueKind.FLOAT, "F", "INSERT INTO VALS (F) VALUES (2.5)"))
        assertEquals("[null]", json(ValueKind.FLOAT, "F", "INSERT INTO VALS (F) VALUES (NULL)"))
        assertEquals("[true]", json(ValueKind.BOOLEAN, "B", "INSERT INTO VALS (B) VALUES (TRUE)"))
        assertEquals("[null]", json(ValueKind.BOOLEAN, "B", "INSERT INTO VALS (B) VALUES (NULL)"))
        assertEquals("[\"x\"]", json(ValueKind.STRING, "S", "INSERT INTO VALS (S) VALUES ('x')"))
        assertEquals("[null]", json(ValueKind.OTHER, "S", "INSERT INTO VALS (S) VALUES (NULL)"))
        assertEquals("[\"2026-01-02\"]", json(ValueKind.DATE, "DT", "INSERT INTO VALS (DT) VALUES ('2026-01-02')"))
        assertEquals("[\"03:04:05\"]", json(ValueKind.TIME, "T", "INSERT INTO VALS (T) VALUES ('03:04:05')"))
        assertEquals(
            "[\"2026-01-02T03:04:05\"]",
            json(ValueKind.TIMESTAMP, "TS", "INSERT INTO VALS (TS) VALUES ('2026-01-02 03:04:05')")
        )
        assertEquals(
            "[\"2026-01-02T03:04:05Z\"]",
            json(ValueKind.TIMESTAMP_TZ, "TSTZ", "INSERT INTO VALS (TSTZ) VALUES ('2026-01-02 03:04:05+00')")
        )
        assertEquals("[\"AQID\"]", json(ValueKind.BINARY, "BIN", "INSERT INTO VALS (BIN) VALUES (X'010203')"))
        assertEquals("[null]", json(ValueKind.BINARY, "BIN", "INSERT INTO VALS (BIN) VALUES (NULL)"))
    }

    private fun bind(jdbcType: Int, json: String, block: (PreparedStatement) -> Unit) {
        val ps = mockk<PreparedStatement>(relaxed = true)
        JdbcValues.bind(ps, 1, jdbcType, BackupFormat.jsonMapper.readTree(json))
        block(ps)
    }

    @Test
    fun `bind converts values to target column types`() {
        bind(Types.BIGINT, "5") { verify { it.setLong(1, 5L) } }
        bind(Types.INTEGER, "\"6\"") { verify { it.setInt(1, 6) } }
        bind(Types.SMALLINT, "true") { verify { it.setShort(1, 1) } }
        bind(Types.DECIMAL, "1.25") { verify { it.setBigDecimal(1, java.math.BigDecimal("1.25")) } }
        bind(Types.DOUBLE, "1.5") { verify { it.setDouble(1, 1.5) } }
        bind(Types.BOOLEAN, "true") { verify { it.setBoolean(1, true) } }
        bind(Types.BOOLEAN, "0") { verify { it.setBoolean(1, false) } }
        bind(Types.BOOLEAN, "\"TRUE\"") { verify { it.setBoolean(1, true) } }
        bind(Types.BOOLEAN, "\"1\"") { verify { it.setBoolean(1, true) } }
        bind(Types.VARCHAR, "\"x\"") { verify { it.setString(1, "x") } }
        bind(Types.DATE, "\"2026-01-02\"") { verify { it.setObject(1, java.time.LocalDate.of(2026, 1, 2)) } }
        bind(Types.TIME, "\"03:04:05\"") { verify { it.setObject(1, java.time.LocalTime.of(3, 4, 5)) } }
        bind(Types.TIMESTAMP, "\"2026-01-02T03:04:05\"") {
            verify { it.setObject(1, java.time.LocalDateTime.of(2026, 1, 2, 3, 4, 5)) }
        }
        bind(Types.TIMESTAMP, "\"2026-01-02T03:04:05Z\"") { verify { it.setObject(1, any<java.time.LocalDateTime>()) } }
        bind(Types.TIMESTAMP_WITH_TIMEZONE, "\"2026-01-02T03:04:05Z\"") {
            verify { it.setObject(1, java.time.OffsetDateTime.parse("2026-01-02T03:04:05Z")) }
        }
        bind(Types.TIMESTAMP_WITH_TIMEZONE, "\"2026-01-02T03:04:05\"") {
            verify { it.setObject(1, any<java.time.OffsetDateTime>()) }
        }
        bind(Types.VARBINARY, "\"AQID\"") { verify { it.setBytes(1, byteArrayOf(1, 2, 3)) } }
        bind(Types.OTHER, "\"v\"") { verify { it.setObject(1, "v", Types.OTHER) } }
        bind(Types.VARCHAR, "null") { verify { it.setNull(1, Types.VARCHAR) } }
    }

    @Test
    fun `bind sets null for missing value`() {
        val ps = mockk<PreparedStatement>(relaxed = true)
        JdbcValues.bind(ps, 1, Types.INTEGER, null)
        verify { ps.setNull(1, Types.INTEGER) }
    }

    @Test
    fun `schema inspector lists tables, columns and sequences`() {
        val inspector = SchemaInspector(connection)

        assertTrue(inspector.tables().containsAll(listOf("CHILD", "GRANDCHILD", "PARENT", "VALS")))
        assertEquals("\"a\"\"b\"", inspector.quote("a\"b"))

        val columns = inspector.columns("my_table")
        assertEquals(listOf("ID", "V"), columns.map { it.name })
        assertTrue(columns.first().autoIncrement)
        assertFalse(columns.last().autoIncrement)
        assertEquals(Types.BIGINT, inspector.columns("PARENT").single().jdbcType)

        val sequence = inspector.sequences().single { it.name == "SEQ_A" }
        assertEquals(5, sequence.increment)
    }

    @Test
    fun `sortByDependencies puts referenced tables first and tolerates cycles`() {
        val inspector = SchemaInspector(connection)

        val sorted = inspector.sortByDependencies(listOf("GRANDCHILD", "CHILD", "PARENT"))
        assertEquals(listOf("PARENT", "CHILD", "GRANDCHILD"), sorted)

        connection.createStatement().use {
            it.execute("CREATE TABLE CYC_A (ID BIGINT PRIMARY KEY, B_ID BIGINT)")
            it.execute("CREATE TABLE CYC_B (ID BIGINT PRIMARY KEY, A_ID BIGINT REFERENCES CYC_A(ID))")
            it.execute("ALTER TABLE CYC_A ADD FOREIGN KEY (B_ID) REFERENCES CYC_B(ID)")
        }
        assertEquals(listOf("CYC_A", "CYC_B"), SchemaInspector(connection).sortByDependencies(listOf("CYC_B", "CYC_A")))
    }
}
