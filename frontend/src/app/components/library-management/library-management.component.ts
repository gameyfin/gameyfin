import {Component, OnInit} from '@angular/core';
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";
import {GamesService} from "../../services/games.service";
import {LibraryManagementService} from "../../services/library-management.service";
import {UnmappedFileDto} from "../../models/dtos/UnmappedFileDto";
import {LibraryService} from "../../services/library.service";
import {DialogService} from "../../services/dialog.service";

@Component({
  selector: 'app-library-management',
  templateUrl: './library-management.component.html',
  styleUrls: ['./library-management.component.scss']
})
export class LibraryManagementComponent implements OnInit {

  gameMappingTableColumns: string[] = ["path", "game", "actions"];
  unmappedGameTableColumns: string[] = ["path", "actions"];

  mappedGames!: DetectedGameDto[];
  unmappedFiles!: UnmappedFileDto[];

  constructor(private gameService: GamesService,
              private libraryManagementService: LibraryManagementService,
              private dialogService: DialogService) {
  }

  ngOnInit(): void {
    this.refreshMappedGamesList();
    this.refreshUnmappedFilesList();
  }

  refreshMappedGamesList(): void {
    this.gameService.getAllGames().subscribe(games => this.mappedGames = games);
  }

  getFullYearFromTimestamp(timestamp: number): number {
    return new Date(timestamp).getFullYear();
  }

  confirmGameMapping(mappedGame: DetectedGameDto): void {
    this.libraryManagementService.confirmGameMapping(mappedGame.slug).subscribe(() => mappedGame.confirmedMatch = true);
  }

  deleteGameMapping(mappedGame: DetectedGameDto): void {
    this.libraryManagementService.deleteGame(mappedGame.slug).subscribe(() => this.mappedGames = this.mappedGames.filter(game => game !== mappedGame));
  }

  openCorrectMappingDialog(mappedGame: DetectedGameDto): void {
    this.dialogService.correctGameMappingDialog(mappedGame);
  }

  refreshUnmappedFilesList(): void {
    this.libraryManagementService.getUnmappedFiles().subscribe(unmappedFiles => this.unmappedFiles = unmappedFiles);
  }

  deleteUnmappedFile(unmappedFile: UnmappedFileDto): void {
    this.libraryManagementService.deleteUnmappedFile(unmappedFile.id).subscribe(() => this.unmappedFiles = this.unmappedFiles.filter(uf => uf !== unmappedFile));
  }

  openMapUnmappedFileDialog(unmappedFile: UnmappedFileDto): void {
    this.dialogService.mapUnmappedGameDialog(unmappedFile);
  }

}
