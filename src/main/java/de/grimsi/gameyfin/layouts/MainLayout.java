package de.grimsi.gameyfin.layouts;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.security.AuthenticationContext;
import de.grimsi.gameyfin.resources.PublicResources;
import de.grimsi.gameyfin.services.ThemeService;
import de.grimsi.gameyfin.setup.SetupService;
import de.grimsi.gameyfin.views.SetupView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.vaadin.firitin.util.style.LumoProps;

import static de.grimsi.gameyfin.users.util.Utils.isAdmin;

@JsModule("./scripts/prefers-color-scheme.js")
@CssImport("./styles/header.css")
public class MainLayout extends AppLayout {

    public MainLayout(AuthenticationContext authContext,
                      @Autowired SetupService setupService,
                      @Autowired ThemeService themeService) {

        if (!setupService.isSetupCompleted()) {
            UI.getCurrent().navigate(SetupView.class);
            UI.getCurrent().close();
        }

        UserDetails user = authContext.getAuthenticatedUser(UserDetails.class).get();

        Image logo = new Image(PublicResources.GAMEYFIN_LOGO.path, "Gameyfin Logo");
        logo.addClassName("header-logo");

        Button toggleTheme = new Button(FontAwesome.Solid.CIRCLE_HALF_STROKE.create());
        toggleTheme.addThemeVariants(ButtonVariant.LUMO_ICON);
        toggleTheme.addClickListener(listener -> themeService.toggleTheme());

        Avatar avatar = new Avatar(user.getUsername());
        avatar.setAbbreviation(user.getUsername().substring(0, 2).toUpperCase());
        avatar.setColorIndex(user.getUsername().chars().map(i -> i % 6).findFirst().getAsInt());

        MenuBar menu = new MenuBar();
        menu.addThemeVariants(MenuBarVariant.LUMO_ICON);
        MenuItem item = menu.addItem(avatar);
        SubMenu subMenu = item.getSubMenu();
        subMenu.addItem(menuItem(FontAwesome.Solid.USER, "Profile", l -> Notification.show("Profile")));
        if (isAdmin(user)) {
            subMenu.addItem(menuItem(FontAwesome.Solid.COG, "Administration", l -> Notification.show("Administration")));
        }
        subMenu.addItem(menuItem(FontAwesome.Solid.QUESTION_CIRCLE, "Help", l -> Notification.show("Help")));
        subMenu.addItem(menuItem(FontAwesome.Solid.SIGN_OUT, "Sign out", l -> authContext.logout()));

        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setAlignItems(FlexComponent.Alignment.END);
        horizontalLayout.add(logo, toggleTheme, menu);

        addToNavbar(horizontalLayout);
    }

    private HorizontalLayout menuItem(FontAwesome.Solid icon, String title, ComponentEventListener<ClickEvent<HorizontalLayout>> listener) {
        FontAwesome.Solid.Icon i = icon.create();
        i.setSize(LumoProps.ICON_SIZE_S.var());

        HorizontalLayout h = new HorizontalLayout();
        h.setAlignItems(FlexComponent.Alignment.CENTER);
        h.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

        h.add(i);
        h.add(title);
        h.addClickListener(listener);

        return h;
    }
}