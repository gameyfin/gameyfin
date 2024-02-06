package de.grimsi.gameyfin.ui.layouts

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome
import com.github.mvysny.karibudsl.v10.*
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.router.RouterLayout
import de.grimsi.gameyfin.setup.SetupService
import de.grimsi.gameyfin.ui.resources.PublicResources
import de.grimsi.gameyfin.ui.services.ThemeService
import de.grimsi.gameyfin.ui.views.LoginView
import org.springframework.beans.factory.annotation.Autowired

class SetupLayout(
    @Autowired private val setupService: SetupService,
    @Autowired private val themeService: ThemeService
) : KComposite(), RouterLayout {

    init {
        if (setupService.isSetupCompleted()) UI.getCurrent().navigate(LoginView::class.java)

        ui {
            appLayout {
                navbar {
                    flexLayout {
                        setWidthFull()
                        alignItems = FlexComponent.Alignment.CENTER

                        image(PublicResources.GAMEYFIN_LOGO.path) {
                            setWidthFull()
                            height = "40px"
                            className = "header-logo"
                        }

                        horizontalLayout {
                            alignItems = FlexComponent.Alignment.CENTER

                            val toggleDarkModeIcon =
                                FontAwesome.Solid.CIRCLE_HALF_STROKE.create { _ -> themeService.toggleTheme() }
                            iconButton(toggleDarkModeIcon)
                        }
                    }
                }
            }
        }
    }
}