package org.gameyfin.app.shared.token

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import kotlin.reflect.full.createInstance

class TokenTypeUserType : UserType<TokenType> {

    override fun getSqlType(): Int = Types.VARCHAR

    override fun returnedClass(): Class<TokenType> = TokenType::class.java

    override fun equals(x: TokenType?, y: TokenType?): Boolean {
        if (x === y) return true
        if (x == null || y == null) return false
        return x.key == y.key
    }

    override fun hashCode(x: TokenType): Int = x.key.hashCode()

    override fun nullSafeGet(
        rs: ResultSet,
        position: Int,
        session: SharedSessionContractImplementor,
        owner: Any?
    ): TokenType? {
        val key = rs.getString(position) ?: return null
        val tokenTypeClass = TokenType::class

        return tokenTypeClass.sealedSubclasses
            .map { it.objectInstance ?: it.createInstance() }
            .firstOrNull { it.key == key }
            ?: throw IllegalArgumentException("Unknown TokenType: $key")
    }

    override fun nullSafeSet(
        st: PreparedStatement,
        value: TokenType?,
        index: Int,
        session: SharedSessionContractImplementor
    ) {
        if (value == null) {
            st.setNull(index, Types.VARCHAR)
        } else {
            st.setString(index, value.key)
        }
    }

    override fun deepCopy(value: TokenType): TokenType = value

    override fun isMutable(): Boolean = false

    override fun disassemble(value: TokenType): Serializable = value.key

    override fun assemble(cached: Serializable, owner: Any?): TokenType {
        val key = cached as? String ?: throw IllegalArgumentException("Invalid cached value: $cached")
        val tokenTypeClass = TokenType::class

        return tokenTypeClass.sealedSubclasses
            .map { it.objectInstance ?: it.createInstance() }
            .firstOrNull { it.key == key }
            ?: throw IllegalArgumentException("Unknown TokenType: $key")
    }
}