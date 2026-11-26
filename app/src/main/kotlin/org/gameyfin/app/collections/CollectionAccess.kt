package org.gameyfin.app.collections

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.entities.CollectionKind
import org.gameyfin.app.collections.entities.CollectionVisibility
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.core.security.isAdmin
import org.gameyfin.app.social.MemberIdentity
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.core.Authentication

data class CollectionViewer(val memberId: Long? = null, val admin: Boolean = false) {
    fun readable(): Specification<Collection> = Specification { root, _, cb -> readable(root, cb) }

    fun readable(root: Path<Collection>, cb: CriteriaBuilder): Predicate {
        val visibility = root.get<CollectionVisibility>("visibility")
        val kind = root.get<CollectionKind>("kind")
        val readable = mutableListOf(cb.equal(visibility, CollectionVisibility.PUBLIC))
        if (memberId != null) {
            readable += cb.equal(visibility, CollectionVisibility.MEMBERS_ONLY)
            readable += cb.and(
                cb.equal(visibility, CollectionVisibility.PRIVATE),
                cb.equal(root.get<Any>("owner").get<Long>("id"), memberId)
            )
        }
        if (admin) {
            readable += cb.and(
                cb.equal(visibility, CollectionVisibility.PRIVATE),
                cb.equal(kind, CollectionKind.CURATED)
            )
        }
        return cb.or(*readable.toTypedArray())
    }
}

fun MemberIdentity.collectionViewer(auth: Authentication? = getCurrentAuth()) =
    CollectionViewer(id(auth), auth?.isAuthenticated == true && auth.isAdmin())
