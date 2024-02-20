package de.grimsi.gameyfin.layouts;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import de.grimsi.gameyfin.resources.PublicResources;
import de.grimsi.gameyfin.services.ThemeService;
import org.springframework.beans.factory.annotation.Autowired;

@CssImport("./styles/header.css")
public class SetupLayout extends AppLayout {

    public SetupLayout(@Autowired ThemeService themeService) {
        Image logo = new Image(PublicResources.GAMEYFIN_LOGO.path, "Gameyfin Logo");
        logo.addClassName("header-logo");

        Button toggleTheme = new Button(FontAwesome.Solid.CIRCLE_HALF_STROKE.create());
        toggleTheme.addThemeVariants(ButtonVariant.LUMO_ICON);
        toggleTheme.addClickListener(listener -> themeService.toggleTheme());

        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setWidthFull();
        horizontalLayout.setAlignSelf(FlexComponent.Alignment.END);
        horizontalLayout.add(logo, toggleTheme);

        addToNavbar(horizontalLayout);
    }
}