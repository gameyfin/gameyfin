import {Component, OnInit} from '@angular/core';
import {NavMenuItem} from '../../models/objects/NavMenuItem';
import {LibraryService} from "../../services/library.service";
import {MatSnackBar} from '@angular/material/snack-bar';
import {timeInterval} from "rxjs";

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit {

  activeItem: NavMenuItem | undefined;

  constructor(private libraryService: LibraryService,
              private snackBar: MatSnackBar) {
  }

  ngOnInit() {
  }

  reloadLibrary() {
    this.libraryService.scanLibrary().pipe(timeInterval()).subscribe({
      next: value => this.snackBar.open(`Library scan completed in ${Math.trunc(value.interval/1000)} seconds.`),
      error: error => this.snackBar.open(`Error while scanning library: ${error}`)
    })
  }
}
