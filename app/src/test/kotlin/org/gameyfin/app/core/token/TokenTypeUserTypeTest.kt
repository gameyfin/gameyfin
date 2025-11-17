package org.gameyfin.app.core.token

import io.mockk.every
import io.mockk.mockk
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TokenTypeUserTypeTest {

    private lateinit var userType: TokenTypeUserType
    private lateinit var resultSet: ResultSet
    private lateinit var preparedStatement: PreparedStatement
    private lateinit var session: SharedSessionContractImplementor

    @BeforeEach
    fun setup() {
        userType = TokenTypeUserType()
        resultSet = mockk()
        preparedStatement = mockk(relaxed = true)
        session = mockk()
    }

    @Test
    fun `getSqlType should return VARCHAR`() {
        assertEquals(Types.VARCHAR, userType.getSqlType())
    }

    @Test
    fun `returnedClass should return TokenType class`() {
        assertEquals(TokenType::class.java, userType.returnedClass())
    }

    @Test
    fun `equals should return true when both objects are same instance`() {
        val type = TokenType.PasswordReset
        assertEquals(true, userType.equals(type, type))
    }

    @Test
    fun `equals should return true when both have same key`() {
        assertEquals(true, userType.equals(TokenType.PasswordReset, TokenType.PasswordReset))
    }

    @Test
    fun `equals should return false when types differ`() {
        assertFalse(userType.equals(TokenType.PasswordReset, TokenType.EmailConfirmation))
    }

    @Test
    fun `equals should return true when both are null`() {
        assertEquals(true, userType.equals(null, null))
    }

    @Test
    fun `equals should return false when one is null`() {
        assertFalse(userType.equals(TokenType.PasswordReset, null))
        assertFalse(userType.equals(null, TokenType.EmailConfirmation))
    }

    @Test
    fun `hashCode should return consistent value for same type`() {
        val type = TokenType.PasswordReset
        val hash1 = userType.hashCode(type)
        val hash2 = userType.hashCode(type)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashCode should match key hashCode`() {
        val type = TokenType.PasswordReset
        assertEquals(type.key.hashCode(), userType.hashCode(type))
    }

    @Test
    fun `hashCode should differ for different types`() {
        val hash1 = userType.hashCode(TokenType.PasswordReset)
        val hash2 = userType.hashCode(TokenType.EmailConfirmation)

        assertEquals(false, hash1 == hash2)
    }

    @Test
    fun `nullSafeGet should return PasswordReset for password-reset key`() {
        every { resultSet.getString(0) } returns "password-reset"

        val result = userType.nullSafeGet(resultSet, 0, session, null)

        assertNotNull(result)
        assertEquals(TokenType.PasswordReset, result)
    }

    @Test
    fun `nullSafeGet should return EmailConfirmation for email-verification key`() {
        every { resultSet.getString(1) } returns "email-verification"

        val result = userType.nullSafeGet(resultSet, 1, session, null)

        assertNotNull(result)
        assertEquals(TokenType.EmailConfirmation, result)
    }

    @Test
    fun `nullSafeGet should return Invitation for invitation key`() {
        every { resultSet.getString(2) } returns "invitation"

        val result = userType.nullSafeGet(resultSet, 2, session, null)

        assertNotNull(result)
        assertEquals(TokenType.Invitation, result)
    }

    @Test
    fun `nullSafeGet should return null when database value is null`() {
        every { resultSet.getString(0) } returns null

        val result = userType.nullSafeGet(resultSet, 0, session, null)

        assertNull(result)
    }

    @Test
    fun `nullSafeGet should throw exception for unknown key`() {
        every { resultSet.getString(0) } returns "unknown-type"

        val exception = assertThrows(IllegalArgumentException::class.java) {
            userType.nullSafeGet(resultSet, 0, session, null)
        }

        assertEquals("Unknown TokenType: unknown-type", exception.message)
    }

    @Test
    fun `nullSafeGet should throw exception for empty string`() {
        every { resultSet.getString(0) } returns ""

        val exception = assertThrows(IllegalArgumentException::class.java) {
            userType.nullSafeGet(resultSet, 0, session, null)
        }

        assertEquals("Unknown TokenType: ", exception.message)
    }

    @Test
    fun `nullSafeSet should set string value for PasswordReset`() {
        val capturedValues = mutableListOf<String>()
        every { preparedStatement.setString(0, capture(capturedValues)) } returns Unit

        userType.nullSafeSet(preparedStatement, TokenType.PasswordReset, 0, session)

        assertEquals(1, capturedValues.size)
        assertEquals("password-reset", capturedValues[0])
    }

    @Test
    fun `nullSafeSet should set string value for EmailConfirmation`() {
        val capturedValues = mutableListOf<String>()
        every { preparedStatement.setString(1, capture(capturedValues)) } returns Unit

        userType.nullSafeSet(preparedStatement, TokenType.EmailConfirmation, 1, session)

        assertEquals(1, capturedValues.size)
        assertEquals("email-verification", capturedValues[0])
    }

    @Test
    fun `nullSafeSet should set string value for Invitation`() {
        val capturedValues = mutableListOf<String>()
        every { preparedStatement.setString(2, capture(capturedValues)) } returns Unit

        userType.nullSafeSet(preparedStatement, TokenType.Invitation, 2, session)

        assertEquals(1, capturedValues.size)
        assertEquals("invitation", capturedValues[0])
    }

    @Test
    fun `nullSafeSet should set null when value is null`() {
        val capturedIndices = mutableListOf<Int>()
        val capturedTypes = mutableListOf<Int>()
        every { preparedStatement.setNull(capture(capturedIndices), capture(capturedTypes)) } returns Unit

        userType.nullSafeSet(preparedStatement, null, 0, session)

        assertEquals(1, capturedIndices.size)
        assertEquals(0, capturedIndices[0])
        assertEquals(Types.VARCHAR, capturedTypes[0])
    }

    @Test
    fun `deepCopy should return same instance`() {
        val type = TokenType.PasswordReset
        val copy = userType.deepCopy(type)

        assertEquals(type, copy)
    }

    @Test
    fun `deepCopy should work for all token types`() {
        assertEquals(TokenType.PasswordReset, userType.deepCopy(TokenType.PasswordReset))
        assertEquals(TokenType.EmailConfirmation, userType.deepCopy(TokenType.EmailConfirmation))
        assertEquals(TokenType.Invitation, userType.deepCopy(TokenType.Invitation))
    }

    @Test
    fun `isMutable should return false`() {
        assertFalse(userType.isMutable())
    }

    @Test
    fun `disassemble should return key string`() {
        val result = userType.disassemble(TokenType.PasswordReset)

        assertEquals("password-reset", result)
    }

    @Test
    fun `disassemble should work for all token types`() {
        assertEquals("password-reset", userType.disassemble(TokenType.PasswordReset))
        assertEquals("email-verification", userType.disassemble(TokenType.EmailConfirmation))
        assertEquals("invitation", userType.disassemble(TokenType.Invitation))
    }

    @Test
    fun `assemble should return PasswordReset from password-reset string`() {
        val result = userType.assemble("password-reset", null)

        assertEquals(TokenType.PasswordReset, result)
    }

    @Test
    fun `assemble should return EmailConfirmation from email-verification string`() {
        val result = userType.assemble("email-verification", null)

        assertEquals(TokenType.EmailConfirmation, result)
    }

    @Test
    fun `assemble should return Invitation from invitation string`() {
        val result = userType.assemble("invitation", null)

        assertEquals(TokenType.Invitation, result)
    }

    @Test
    fun `assemble should throw exception for unknown key`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            userType.assemble("invalid-key", null)
        }

        assertEquals("Unknown TokenType: invalid-key", exception.message)
    }

    @Test
    fun `assemble should throw exception for non-string cached value`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            userType.assemble(12345, null)
        }

        assertEquals("Invalid cached value: 12345", exception.message)
    }

    @Test
    fun `assemble and disassemble should be inverse operations`() {
        val original = TokenType.PasswordReset
        val disassembled = userType.disassemble(original)
        val reassembled = userType.assemble(disassembled, null)

        assertEquals(original, reassembled)
    }

    @Test
    fun `assemble and disassemble should work for all token types`() {
        val types = listOf(
            TokenType.PasswordReset,
            TokenType.EmailConfirmation,
            TokenType.Invitation
        )

        types.forEach { type ->
            val disassembled = userType.disassemble(type)
            val reassembled = userType.assemble(disassembled, null)
            assertEquals(type, reassembled)
        }
    }
}

