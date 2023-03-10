import {AfterViewInit, Component, Input, OnChanges, SimpleChanges, ViewChild} from '@angular/core';
import {MatPaginator} from '@angular/material/paginator';
import {MatSort} from '@angular/material/sort';
import {MatTable, MatTableDataSource} from '@angular/material/table';
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";
import {GamesService} from "../../services/games.service";
import {LibraryManagementService} from "../../services/library-management.service";
import {DialogService} from "../../services/dialog.service";

@Component({
  selector: 'mapped-games-table',
  templateUrl: './mapped-games-table.component.html',
  styleUrls: ['./mapped-games-table.component.scss']
})
export class MappedGamesTableComponent implements AfterViewInit, OnChanges {
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatTable) table!: MatTable<DetectedGameDto>;
  @Input() mappedGames!: DetectedGameDto[];

  dataSource: MatTableDataSource<DetectedGameDto> = new MatTableDataSource();

  displayedColumns: string[] = ["path", "game", "actions"];

  showOnlyUnconfirmedMatches: boolean = false;

  filter: DetectedGameDto = new DetectedGameDto();

  constructor(private gamesService: GamesService,
              private libraryManagementService: LibraryManagementService,
              private dialogService: DialogService) {
  }

  ngAfterViewInit(): void {
    this.dataSource.sort = this.sort;
    this.dataSource.sortingDataAccessor = (item: DetectedGameDto, property: string) => {
      if (property === 'game') {
        return item.title;
      }
      return (item as any)[property];
    };

    this.dataSource.paginator = this.paginator;
  }

  ngOnChanges(changes: SimpleChanges): void {
    this.refreshData(changes['mappedGames'].currentValue);
  }

  refreshMappedGamesList(): void {
    this.gamesService.getAllGames(true).subscribe(games => this.refreshData(games));
  }

  toggleShowOnlyUnconfirmedMatches() {
    this.showOnlyUnconfirmedMatches = !this.showOnlyUnconfirmedMatches;
    this.filter.confirmedMatch = this.showOnlyUnconfirmedMatches ? false : undefined;
  }

  getFullYearFromTimestamp(timestamp: number): number {
    return new Date(timestamp).getFullYear();
  }

  toggleConfirmGameMapping(mappedGame: DetectedGameDto): void {
    this.libraryManagementService.confirmGameMapping(mappedGame.slug, !mappedGame.confirmedMatch).subscribe(() => {
      mappedGame.confirmedMatch = !mappedGame.confirmedMatch;
      this.refreshData(this.dataSource.data);
    });
  }

  deleteGameMapping(mappedGame: DetectedGameDto): void {
    this.libraryManagementService.deleteGame(mappedGame.slug).subscribe(
      () => this.refreshData(this.dataSource.data.filter(game => game !== mappedGame))
    );
  }

  openCorrectMappingDialog(mappedGame: DetectedGameDto): void {
    this.dialogService.correctGameMappingDialog(mappedGame).subscribe(gameSuccessfullyMapped => {
      if (gameSuccessfullyMapped) this.refreshMappedGamesList();
    })
  }

  private refreshData(newData: DetectedGameDto[]): void {
    this.dataSource.data = newData;

    // Dirty hack to force a re-render
    // Did not find a better solution
    this.paginator?._changePageSize(this.paginator?.pageSize);
  }
}
