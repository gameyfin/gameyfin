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
)

enum class CompanyType {
    DEVELOPER,
    PUBLISHER
}