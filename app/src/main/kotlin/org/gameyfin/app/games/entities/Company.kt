package org.gameyfin.app.games.entities

import jakarta.persistence.*

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["name", "type"])])
class Company(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,
    val name: String,
    val type: CompanyType
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Company) return false
        return name == other.name && type == other.type
    }
}

enum class CompanyType {
    DEVELOPER,
    PUBLISHER
}