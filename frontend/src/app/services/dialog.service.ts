import {Injectable} from '@angular/core';
import {MatDialog, MatDialogConfig} from '@angular/material/dialog';
import {ErrorDialogComponent} from '../components/error-dialog/error-dialog.component';
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";
import {MapGameDialogComponent} from "../components/map-game-dialog/map-game-dialog.component";
import {UnmappedFileDto} from "../models/dtos/UnmappedFileDto";

@Injectable({
  providedIn: 'root'
})
export class DialogService {

  constructor(private dialog: MatDialog) {
  }

  public showErrorDialog(message: string): void {
    const dialogConfig = new MatDialogConfig();

    dialogConfig.disableClose = true;
    dialogConfig.autoFocus = true;
    dialogConfig.closeOnNavigation = true;

    dialogConfig.data = {
      message
    };

    this.dialog.open(ErrorDialogComponent, dialogConfig);
  }

  public correctGameMappingDialog(game: DetectedGameDto): void {
    const dialogConfig = new MatDialogConfig();

    dialogConfig.disableClose = true;
    dialogConfig.autoFocus = true;
    dialogConfig.closeOnNavigation = true;

    dialogConfig.data = {
      path: game.path,
      slug: game.slug
    };

    this.dialog.open(MapGameDialogComponent, dialogConfig);
  }

  public mapUnmappedGameDialog(unmappedFile: UnmappedFileDto): void {
    const dialogConfig = new MatDialogConfig();

    dialogConfig.disableClose = true;
    dialogConfig.autoFocus = true;
    dialogConfig.closeOnNavigation = true;

    dialogConfig.data = {
      path: unmappedFile.path
    };

    this.dialog.open(MapGameDialogComponent, dialogConfig);
  }

}
