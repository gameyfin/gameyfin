package org.gameyfin.app.core.jobs

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JobRunResultRepository : JpaRepository<JobRunResult, Long>

