package de.grimsi.gameyfin.ui.views

import com.github.mvysny.karibudsl.v10.h1
import com.github.mvysny.karibudsl.v10.pre
import com.github.mvysny.karibudsl.v10.verticalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.Route
import de.grimsi.gameyfin.ui.layouts.MainLayout
import jakarta.annotation.security.PermitAll


@Route("", layout = MainLayout::class)
@PermitAll
class MainView : VerticalLayout() {

    init {
        verticalLayout {
            h1 { text = "Gameyfin main page" }
            pre { text = "Work in progress" }
        }
    }
}