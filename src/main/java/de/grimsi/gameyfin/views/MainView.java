package de.grimsi.gameyfin.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.grimsi.gameyfin.layouts.MainLayout;
import jakarta.annotation.security.PermitAll;


@Route(value = "", layout = MainLayout.class)
@PageTitle("Gameyfin")
@PermitAll
public class MainView extends VerticalLayout {

    public MainView() {
        add(new H1("Gameyfin main page"));
        add(new Pre("Work in progress"));
    }
}