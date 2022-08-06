import {Component} from '@angular/core';
import {NavigationEnd, Router} from "@angular/router";
import {Config} from "./config/Config";
import {Title} from "@angular/platform-browser";

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {

  constructor(private router: Router, private title: Title) {
    this.router.routeReuseStrategy.shouldReuseRoute = () => false;
    this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        // Trick the Router into believing it's last link wasn't previously loaded
        this.router.navigated = false;
      }
    });

    title.setTitle(Config.baseTitle);
  }
}
