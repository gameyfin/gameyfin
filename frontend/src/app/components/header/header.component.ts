import {Component} from '@angular/core';
import {LibraryService} from "../../services/library.service";
import {MatSnackBar} from '@angular/material/snack-bar';
import {timeInterval} from "rxjs";
import {ActivatedRoute, Router} from "@angular/router";

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent {

  constructor(private libraryService: LibraryService,
              private snackBar: MatSnackBar,
              private router: Router) {
  }

  reloadLibrary(): void {
    this.libraryService.scanLibrary().pipe(timeInterval()).subscribe({
      next: value => this.snackBar.open(`Library scan completed in ${Math.trunc(value.interval / 1000)} seconds.`),
      error: error => this.snackBar.open(`Error while scanning library: ${error}`)
    })
  }

  goToLibraryScreen(): void {
    this.router.navigate(['/']);
  }

  notOnLibraryScreen(): boolean {
    return !(this.router.url === "/library");
  }

}
