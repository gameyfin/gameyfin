import {Injectable} from '@angular/core';
import {MatDialog, MatDialogConfig} from '@angular/material/dialog';
import {ErrorDialogComponent} from '../components/error-dialog/error-dialog.component';
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";
import {MapGameDialogComponent} from "../components/map-game-dialog/map-game-dialog.component";
import {MapLibraryDialogComponent} from "../components/map-library-dialog/map-library-dialog.component";
import {UnmappedFileDto} from "../models/dtos/UnmappedFileDto";
import {LibraryDto} from "../models/dtos/LibraryDto";
import {Observable} from "rxjs";

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
    dialogConfig.minWidth = '25vw';

    dialogConfig.data = {
      message
    };

    this.dialog.open(ErrorDialogComponent, dialogConfig);
  }

  public correctGameMappingDialog(game: DetectedGameDto): Observable<any> {
    const dialogConfig = new MatDialogConfig();

    dialogConfig.disableClose = true;
    dialogConfig.autoFocus = true;
    dialogConfig.closeOnNavigation = true;
    dialogConfig.minWidth = '40vw';

    dialogConfig.data = {
      path: game.path,
      slug: game.slug
    };

    return this.dialog.open(MapGameDialogComponent, dialogConfig).afterClosed();
  }

  public mapUnmappedGameDialog(unmappedFile: UnmappedFileDto): Observable<any> {
    const dialogConfig = new MatDialogConfig();

    dialogConfig.disableClose = true;
    dialogConfig.autoFocus = true;
    dialogConfig.closeOnNavigation = true;
    dialogConfig.minWidth = '40vw';

    dialogConfig.data = {
      path: unmappedFile.path
    };

    return this.dialog.open(MapGameDialogComponent, dialogConfig).afterClosed();
  }

  public libraryMappingDialog(library: LibraryDto): Observable<any> {
    const dialogConfig = new MatDialogConfig();

    dialogConfig.disableClose = true;
    dialogConfig.autoFocus = true;
    dialogConfig.closeOnNavigation = true;
    dialogConfig.minWidth = '40vw';

    dialogConfig.data = {
      path: library.path,
      slugs: library.platforms.map((platform) => platform.slug)
    };

    return this.dialog.open(MapLibraryDialogComponent, dialogConfig).afterClosed();
  }

}
