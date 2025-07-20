package org.gameyfin.app.core.jobs

interface Job {
    val name: String
    fun run(): JobRunResult
}

