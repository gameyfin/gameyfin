import {Component} from '@angular/core';
import {LibraryService} from "../../services/library.service";
import {MatSnackBar} from '@angular/material/snack-bar';
import {timeInterval} from "rxjs";
import {ActivatedRoute, Router} from "@angular/router";
import {GamesService} from "../../services/games.service";
import {LibraryManagementComponent} from "../library-management/library-management.component";
import {LibraryOverviewComponent} from "../library-overview/library-overview.component";

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent {

  constructor(private libraryService: LibraryService,
              private gameService: GamesService,
              private snackBar: MatSnackBar,
              private router: Router) {
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

}
