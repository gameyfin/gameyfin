package de.grimsi.gameyfin.ui.services

import com.vaadin.flow.component.UI
import com.vaadin.flow.theme.lumo.Lumo
import org.springframework.stereotype.Service


@Service
class ThemeService {

    fun isDarkModeActive(): Boolean {
        val js = "document.documentElement.getAttribute('theme')"
        return UI.getCurrent().element.executeJs(js).toCompletableFuture().get().asString() == "dark"
    }

    fun toggleTheme() {
        setTheme(!isDarkModeActive())
    }

    fun setTheme(dark: Boolean) {
        val js = "document.documentElement.setAttribute('theme', $0)"

        UI.getCurrent().element.executeJs(js, if (dark) Lumo.DARK else Lumo.LIGHT)
    }
}