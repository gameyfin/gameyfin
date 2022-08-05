import {Component} from '@angular/core';
import {LibraryService} from "../../services/library.service";
import {MatSnackBar} from '@angular/material/snack-bar';
import {timeInterval} from "rxjs";
import {Router} from "@angular/router";
import {GamesService} from "../../services/games.service";

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent {

  darkmodeEnabled: boolean;

  constructor(private libraryService: LibraryService,
              private gameService: GamesService,
              private snackBar: MatSnackBar,
              private router: Router) {

    if(this.getCookie("darkmode") !== null) {
      this.darkmodeEnabled = this.getCookie("darkmode") === "true";
    } else
      if (window.matchMedia) {
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
    this.setCookie("darkmode", this.darkmodeEnabled);
  }

  private setDarkmode(): void {
    document.body.style.background = "#303030";
    document.body.style.color = "white";
  }

  private setLightmode(): void {
    document.body.style.background = "white";
    document.body.style.color = "black";
  }

  scanLibrary(): void {
    this.libraryService.scanLibrary().pipe(timeInterval()).subscribe({
      next: value => this.snackBar.open(`Library scan completed in ${Math.trunc(value.interval / 1000)} seconds.`, undefined, {duration: 2000}),
      error: error => this.snackBar.open(`Error while scanning library: ${error.error.message}`, undefined, {duration: 5000})
    })
    this.snackBar.open('Library scan started in the background. This could take some time.\nYou will get another notification once it\'s done', undefined, {duration: 5000})
  }

  reloadLibrary(): void {
    this.gameService.getAllGames(true).subscribe(() => this.router.navigate(['/library']));
  }

  goToLibraryScreen(): void {
    this.router.navigate(['/']);
  }

  goToLibraryManagementScreen(): void {
    this.router.navigate(['/library-management']);
  }

  onLibraryScreen(): boolean {
    return this.router.url === "/library";
  }

  onLibraryManagementScreen(): boolean {
    return this.router.url === "/library-management";
  }

  private setCookie(name: string, value: any): void {
    let d:Date = new Date();
    document.cookie = `${name}=${value.toString()};`;
  }

  private getCookie(name: string): string | null {
    var dc = document.cookie;
    var prefix = name + "=";
    var begin = dc.indexOf("; " + prefix);
    if (begin == -1) {
      begin = dc.indexOf(prefix);
      if (begin != 0) return null;
    }
    else
    {
      begin += 2;
      var end = document.cookie.indexOf(";", begin);
      if (end == -1) {
        end = dc.length;
      }
    }
    // because unescape has been deprecated, replaced with decodeURI
    //return unescape(dc.substring(begin + prefix.length, end));
    // @ts-ignore
    return decodeURI(dc.substring(begin + prefix.length, end));
  }

}
