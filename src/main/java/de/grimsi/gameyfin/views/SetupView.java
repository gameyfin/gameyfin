package de.grimsi.gameyfin.views;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.grimsi.gameyfin.layouts.SetupLayout;
import de.grimsi.gameyfin.setup.SetupService;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "/setup", layout = SetupLayout.class)
@PageTitle("Setup")
@AnonymousAllowed
public class SetupView extends VerticalLayout {

    public SetupView(@Autowired SetupService setupService) {
        if (setupService.isSetupCompleted()) {
            UI.getCurrent().navigate(LoginView.class);
            UI.getCurrent().close();
        }

        setWidthFull();
        setAlignItems(Alignment.CENTER);

        add(new Text("Looks like it's your first time starting Gameyfin. Let's continue setting up your very own instance 🙂"));

        TextField username = new TextField("Username");
        username.focus();
        PasswordField passwordField = new PasswordField("Password");
        PasswordField passwordFieldRepeat = new PasswordField("Password (repeated)");

        FormLayout form = new FormLayout();
        form.add(new Text("Let's start with creating a super admin account. This account will have full permissions."));
        form.add(username, passwordField, passwordFieldRepeat);

        add(form);
    }
}


