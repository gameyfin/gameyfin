package de.grimsi.gameyfin.ui.layouts

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome
import com.github.mvysny.karibudsl.v10.*
import com.github.mvysny.kaributools.tooltip
import com.vaadin.flow.component.ClickEvent
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.dependency.JsModule
import com.vaadin.flow.component.menubar.MenuBarVariant
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.router.RouterLayout
import com.vaadin.flow.spring.security.AuthenticationContext
import de.grimsi.gameyfin.setup.SetupService
import de.grimsi.gameyfin.ui.resources.PublicResources
import de.grimsi.gameyfin.ui.services.ThemeService
import de.grimsi.gameyfin.ui.views.SetupView
import de.grimsi.gameyfin.users.util.isAdmin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.UserDetails

@JsModule("./scripts/prefers-color-scheme.js")
class MainLayout(
    @field:Transient private val authContext: AuthenticationContext,
    @Autowired private val setupService: SetupService,
    @Autowired private val themeService: ThemeService
) : KComposite(), RouterLayout {

    init {
        if (!setupService.isSetupCompleted()) UI.getCurrent().navigate(SetupView::class.java)

        val user = authContext.getAuthenticatedUser(UserDetails::class.java).get()

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

                            val a = avatar(user.username) {
                                tooltip = user.username
                                abbreviation = user.username.take(2).uppercase()
                                colorIndex = user.username[0].code.toByte().mod(6)
                            }

                            val toggleDarkModeIcon =
                                FontAwesome.Solid.CIRCLE_HALF_STROKE.create { _ -> themeService.toggleTheme() }
                            iconButton(toggleDarkModeIcon)

                            menuBar {
                                addThemeVariants(MenuBarVariant.LUMO_ICON)
                                item(a) {
                                    item(
                                        menuItem(
                                            FontAwesome.Solid.USER,
                                            "Profile"
                                        ) { _ -> Notification.show("Profile") })
                                    if (user.isAdmin()) {
                                        item(menuItem(FontAwesome.Solid.COG, "Administration") { _ ->
                                            Notification.show(
                                                "Administration"
                                            )
                                        })
                                    }
                                    item(
                                        menuItem(
                                            FontAwesome.Solid.QUESTION_CIRCLE,
                                            "Help"
                                        ) { _ -> Notification.show("Help") })
                                    item(menuItem(FontAwesome.Solid.SIGN_OUT, "Sign out") { _ -> authContext.logout() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun menuItem(icon: FontAwesome.Solid, title: String): HorizontalLayout {
        return HorizontalLayout().apply {
            alignItems = FlexComponent.Alignment.CENTER
            justifyContentMode = FlexComponent.JustifyContentMode.START

            val faIcon = icon.create()
            faIcon.setSize("var(--lumo-icon-size-s)")
            add(faIcon)

            text(title)
        }
    }

    private fun menuItem(
        icon: FontAwesome.Solid,
        title: String,
        action: (ClickEvent<HorizontalLayout>) -> Unit
    ): HorizontalLayout {
        return menuItem(icon, title).apply {
            onLeftClick(action)
        }
    }
}