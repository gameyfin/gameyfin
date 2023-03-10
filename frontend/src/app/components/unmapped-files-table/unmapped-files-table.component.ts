import {AfterViewInit, Component, Input, OnChanges, SimpleChanges, ViewChild} from '@angular/core';
import {MatLegacyPaginator as MatPaginator} from '@angular/material/legacy-paginator';
import {MatSort} from '@angular/material/sort';
import {MatLegacyTable as MatTable, MatLegacyTableDataSource as MatTableDataSource} from '@angular/material/legacy-table';
import {UnmappedFileDto} from "../../models/dtos/UnmappedFileDto";
import {GamesService} from "../../services/games.service";
import {LibraryManagementService} from "../../services/library-management.service";
import {DialogService} from "../../services/dialog.service";

@Component({
  selector: 'unmapped-files-table',
  templateUrl: './unmapped-files-table.component.html',
  styleUrls: ['./unmapped-files-table.component.scss']
})
export class UnmappedFilesTableComponent implements AfterViewInit, OnChanges {
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatTable) table!: MatTable<UnmappedFileDto>;
  @Input() unmappedFiles!: UnmappedFileDto[];

  dataSource: MatTableDataSource<UnmappedFileDto> = new MatTableDataSource();

  displayedColumns: string[] = ["path", "actions"];

  constructor(private gameService: GamesService,
              private libraryManagementService: LibraryManagementService,
              private dialogService: DialogService) {
  }

  ngAfterViewInit(): void {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
  }

  ngOnChanges(changes: SimpleChanges): void {
    this.refreshData(changes['unmappedFiles'].currentValue);
  }

  refreshUnmappedFilesList(): void {
    this.libraryManagementService.getUnmappedFiles().subscribe(unmappedFiles => this.refreshData(unmappedFiles));
  }

  deleteUnmappedFile(unmappedFile: UnmappedFileDto): void {
    this.libraryManagementService.deleteUnmappedFile(unmappedFile.id).subscribe(
      () => this.refreshData(this.dataSource.data.filter(uf => uf !== unmappedFile))
    );
  }

  openMapUnmappedFileDialog(unmappedFile: UnmappedFileDto): void {
    this.dialogService.mapUnmappedGameDialog(unmappedFile).subscribe(gameSuccessfullyMapped => {
      if (gameSuccessfullyMapped) this.refreshData(this.dataSource.data.filter(uf => uf !== unmappedFile));
    })
  }

  private refreshData(newData: UnmappedFileDto[]): void {
    this.dataSource.data = newData;

    // Dirty hack to force a re-render
    // Did not find a better solution
    this.paginator?._changePageSize(this.paginator?.pageSize);
  }
}
