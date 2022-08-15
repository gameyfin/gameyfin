import {Injectable} from '@angular/core';
import {OverlayContainer} from "@angular/cdk/overlay";
import {CookieService} from "./cookie.service";

@Injectable({
  providedIn: 'root'
})
export class ThemingService {

  private darkmodeEnabled!: boolean;
  private darkmodeClassName: string = 'darkMode';

  constructor(private cookieService: CookieService,
              private overlay: OverlayContainer) {
    if (this.cookieService.getCookie("darkmode") !== null) {
      this.darkmodeEnabled = this.cookieService.getCookie("darkmode") === "true";
    } else if (window.matchMedia) {
      this.darkmodeEnabled = window.matchMedia('(prefers-color-scheme: dark)').matches;
    } else {
      this.darkmodeEnabled = false;
    }

    this.setTheme();
  }

  toggleTheme(): void {
    this.darkmodeEnabled = !this.darkmodeEnabled;
    this.setTheme();
  }

  private setTheme(): void {
    this.darkmodeEnabled ? this.setDarkmode() : this.setLightmode();
    this.cookieService.setCookie("darkmode", this.darkmodeEnabled);
  }

  private setDarkmode(): void {
    document.body.classList.add(this.darkmodeClassName);
    document.body.style.colorScheme = "dark";
    document.body.style.background = "#303030";
    document.body.style.color = "white";

    this.overlay.getContainerElement().classList.add(this.darkmodeClassName);
  }

  private setLightmode(): void {
    document.body.classList.remove(this.darkmodeClassName);
    document.body.style.colorScheme = "light";
    document.body.style.background = "white";
    document.body.style.color = "black";

    this.overlay.getContainerElement().classList.remove(this.darkmodeClassName);
  }
}
