package de.grimsi.gameyfin.ui.views

import com.github.mvysny.karibudsl.v10.KComposite
import com.github.mvysny.karibudsl.v10.flexLayout
import com.github.mvysny.karibudsl.v10.h1
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.FlexLayout
import com.vaadin.flow.router.Route
import com.vaadin.flow.server.auth.AnonymousAllowed
import de.grimsi.gameyfin.ui.layouts.SetupLayout

@Route("/setup", layout = SetupLayout::class)
@AnonymousAllowed
class SetupView : KComposite() {

    init {
        ui {
            flexLayout {
                setWidthFull()
                alignItems = FlexComponent.Alignment.CENTER
                alignContent = FlexLayout.ContentAlignment.CENTER

                h1("Setup View")
            }
        }
    }
}