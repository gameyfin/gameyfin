package de.grimsi.gameyfin.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.grimsi.gameyfin.resources.PublicResources;
import de.grimsi.gameyfin.setup.SetupService;
import org.springframework.beans.factory.annotation.Autowired;


@Route("login")
@PageTitle("Login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public LoginView(@Autowired SetupService setupService) {
        if (!setupService.isSetupCompleted()) {
            UI.getCurrent().navigate(SetupView.class);
            UI.getCurrent().close();
        }
        Image logo = new Image(PublicResources.GAMEYFIN_LOGO.path, "Gameyfin");
        logo.setHeight("100px");

        login.setAction("login");

        add(logo);
        add(login);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        if (beforeEnterEvent.getLocation()
                .getQueryParameters()
                .getParameters()
                .containsKey("error")
        ) {
            login.setError(true);
        }
    }
}