import {AfterViewInit, Component, Input, OnChanges, SimpleChanges, ViewChild} from '@angular/core';
import {MatPaginator} from '@angular/material/paginator';
import {MatSort} from '@angular/material/sort';
import {MatTable, MatTableDataSource} from '@angular/material/table';
import {LibraryDto} from "../../models/dtos/LibraryDto";
import {LibraryScanRequestDto} from "../../models/dtos/LibraryScanRequestDto";
import {GamesService} from "../../services/games.service";
import {LibraryManagementService} from "../../services/library-management.service";
import {DialogService} from "../../services/dialog.service";
import {MatSnackBar} from '@angular/material/snack-bar';
import {Router} from "@angular/router";
import {LibraryService} from "../../services/library.service";

@Component({
  selector: 'mapped-libraries-table',
  templateUrl: './mapped-libraries-table.component.html',
  styleUrls: ['./mapped-libraries-table.component.scss']
})
export class MappedLibrariesTableComponent implements AfterViewInit, OnChanges {
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatTable) table!: MatTable<LibraryDto>;
  @Input() mappedLibraries!: LibraryDto[];

  dataSource: MatTableDataSource<LibraryDto> = new MatTableDataSource();

  displayedColumns: string[] = ["path", "platforms", "actions"];

  filter: LibraryDto = new LibraryDto();

  constructor(private libraryManagementService: LibraryManagementService,
              private dialogService: DialogService,
              private libraryService: LibraryService,
              private snackBar: MatSnackBar,
              private router: Router) {
  }

  ngAfterViewInit(): void {
    this.dataSource.sort = this.sort;
    this.dataSource.sortingDataAccessor = (item: LibraryDto, property: string) => {
      return (item as any)[property];
    };

    this.dataSource.paginator = this.paginator;
  }

  ngOnChanges(changes: SimpleChanges): void {
    this.refreshData(changes['mappedLibraries'].currentValue);
  }

  refreshMappedLibrariesList(): void {
    this.libraryManagementService.getLibraries().subscribe(libraries => this.refreshData(libraries));
  }

  openLibraryMappingDialog(mappedLibrary: LibraryDto): void {
    this.dialogService.libraryMappingDialog(mappedLibrary).subscribe(librarySuccessfullyMapped => {
      if (librarySuccessfullyMapped) this.refreshMappedLibrariesList();
    })
  }

  scanLibrary(mappedLibrary: LibraryDto): void {
    let request = new LibraryScanRequestDto();
    request.path = mappedLibrary.path;
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

  private refreshData(newData: LibraryDto[]): void {
    this.dataSource.data = newData;

    // Dirty hack to force a re-render
    // Did not find a better solution
    this.paginator?._changePageSize(this.paginator?.pageSize);
  }
}
