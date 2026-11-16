package org.gameyfin.plugins.metadata.steam.util

import io.xberg.htmltomarkdown.HtmlToMarkdown

internal fun toMarkdown(html: String?): String? =
    if (html.isNullOrBlank()) html else HtmlToMarkdown.convert(html).content()
