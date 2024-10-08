package de.grimsi.gameyfin.messages.templates

abstract class MjmlTemplate {
    companion object {
        val placeholders: Map<String, String> = mapOf(
            "logo" to "data:image/png;base64,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",
            "gradient" to "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAgAAAAABCAYAAACouxZ2AAAAo0lEQVRIS+VUwRGAIAyDWdzA/QdwKzwQKKUpKCennr6AmrYkpHZZN2eKz5Ybw0IhwuPx5+KQlhKr4kWa69iQ22JcuhLsPd/X4bup3KQA1cz5lULyGGAZ/7RpYWfrQvl1ftvctrWZo4vv+uiX5QfFhri9yTNDfhO15+jS86vnt6s7fM/nPINz/8Mzz8yyd3hGfVMx8F3PgHlU+UP30+gsI02Rn3dirSVLy0JP4wAAAABJRU5ErkJggg=="
        )
    }
}