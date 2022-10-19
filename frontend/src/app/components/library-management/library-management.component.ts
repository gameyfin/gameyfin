import {Component, OnInit} from '@angular/core';
import {GamesService} from "../../services/games.service";
import {LibraryManagementService} from "../../services/library-management.service";
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";
import {UnmappedFileDto} from "../../models/dtos/UnmappedFileDto";
import {LibraryDto} from "../../models/dtos/LibraryDto";

@Component({
  selector: 'app-library-management',
  templateUrl: './library-management.component.html',
  styleUrls: ['./library-management.component.scss']
})
export class LibraryManagementComponent implements OnInit {
  loggedIn: boolean = false;

  mappedGames: DetectedGameDto[] = [];
  unmappedFiles: UnmappedFileDto[] = [];
  mappedLibraries: LibraryDto[] = [];

  constructor(private gamesService: GamesService,
              private libraryManagementService: LibraryManagementService) {
  }

  ngOnInit(): void {
    this.gamesService.getAllGames().subscribe(games => this.mappedGames = games);
    this.libraryManagementService.getUnmappedFiles().subscribe(uf => {
      this.unmappedFiles = uf;
      this.loggedIn = true;
    });
    this.libraryManagementService.getLibraries().subscribe(libraries => {
      this.mappedLibraries = libraries;
      this.loggedIn = true;
    });
  }

}
