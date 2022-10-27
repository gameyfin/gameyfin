import {Component} from '@angular/core';
import {LibraryService} from "../../services/library.service";
import {MatSnackBar} from '@angular/material/snack-bar';
import {Router} from "@angular/router";
import {GamesService} from "../../services/games.service";
import {ThemingService} from "../../services/theming.service";
import {Location} from '@angular/common';
import {LibraryScanRequestDto} from "../../models/dtos/LibraryScanRequestDto";

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent {

  // Maybe bad practice? IDK, but I need to access the document from the template of this component
  document: Document = document;

  constructor(private libraryService: LibraryService,
              private gameService: GamesService,
              private themingService: ThemingService,
              private snackBar: MatSnackBar,
              private router: Router,
              private location: Location) {
  }

  scanLibrary(): void {
    let request = new LibraryScanRequestDto();
    request.downloadImages = true;
    this.libraryService.scanLibrary(request).subscribe({
      next: result => {
        // Refresh the current page "angular style"
        this.router.navigate([this.router.url]).then(() => {
            const snackBarDuration: number = 10000;

            let snackbarContent: string = 'Library scan completed in ' + result.scanDuration + ' seconds:\n' +
              '- ' + result.newGames + ' new games\n' +
              '- ' + result.deletedGames + ' games removed\n' +
              '- ' + result.newUnmappableFiles + ' files/folders could not be mapped\n' +
              '- ' + result.totalGames + ' games currently in your library';

            if (result.companyLogoDownloads !== undefined && result.coverDownloads !== undefined && result.screenshotDownloads !== undefined) {
              snackbarContent = snackbarContent.concat('\n' +
                '- ' + result.coverDownloads + ' covers downloaded\n' +
                '- ' + result.screenshotDownloads + ' screenshots downloaded\n' +
                '- ' + result.companyLogoDownloads + ' company logos downloaded');
            }

            this.snackBar.open(snackbarContent, undefined, {duration: snackBarDuration});
          }
        )
      },
      error: error => this.snackBar.open(`Error while scanning library: ${error.error.message}`, undefined, {duration: 5000})
    })
    this.snackBar.open('Library scan started in the background. This could take some time.\nYou will get another notification once it\'s done', undefined, {duration: 5000})
  }

  reloadLibrary(): void {
    this.gameService.getAllGames(true).subscribe(() => this.router.navigate(['/library']));
  }

  goToLibraryScreen(): void {
    this.location.back();
  }

  goToLibraryManagementScreen(): void {
    this.router.navigate(['/library-management']);
  }

  onLibraryScreen(): boolean {
    return this.router.url.startsWith("/library?") || this.router.url === "/library";
  }

  onLibraryManagementScreen(): boolean {
    return this.router.url === "/library-management";
  }

  toggleTheme(): void {
    this.themingService.toggleTheme();
  }
}
