package de.grimsi.gameyfin.ui.views

import com.github.mvysny.karibudsl.v10.image
import com.github.mvysny.karibudsl.v10.loginForm
import com.vaadin.flow.component.login.LoginForm
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.BeforeEnterEvent
import com.vaadin.flow.router.BeforeEnterObserver
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import com.vaadin.flow.server.auth.AnonymousAllowed
import de.grimsi.gameyfin.ui.resources.PublicResources

@Route("login")
@PageTitle("Login")
@AnonymousAllowed
class LoginView : VerticalLayout(), BeforeEnterObserver {

    private var login: LoginForm

    init {
        setSizeFull()
        justifyContentMode = FlexComponent.JustifyContentMode.CENTER
        alignItems = FlexComponent.Alignment.CENTER

        image {
            height = "100px"
            src = PublicResources.GAMEYFIN_LOGO.path
            setAlt("Gameyfin")
        }

        login = loginForm {
            addClassName("login-view")
            action = "login"
        }
    }

    override fun beforeEnter(event: BeforeEnterEvent?) {
        if (event != null) {
            if (event.location
                    .queryParameters
                    .parameters
                    .containsKey("error")
            ) {
                login.isError = true
            }
        }
    }

}