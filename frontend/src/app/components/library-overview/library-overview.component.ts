import {AfterContentInit, Component} from '@angular/core';
import {GamesService} from "../../services/games.service";
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";
import {GenreDto} from "../../models/dtos/GenreDto";
import {ThemeDto} from "../../models/dtos/ThemeDto";
import {firstValueFrom, forkJoin, Observable} from "rxjs";
import {SortDirection} from "@angular/material/sort";
import {PlayerPerspectiveDto} from "../../models/dtos/PlayerPerspectiveDto";
import {PlatformDto} from "../../models/dtos/PlatformDto";
import {ActivatedRoute, ActivatedRouteSnapshot, Params, Router} from "@angular/router";
import {Location} from "@angular/common";

class SortOption {
  title: string;
  field: string;
  direction: SortDirection;

  constructor(title: string, field: string, direction: SortDirection) {
    this.title = title;
    this.field = field;
    this.direction = direction;
  }
}

@Component({
  selector: 'app-gameserver-list',
  templateUrl: './library-overview.component.html',
  styleUrls: ['./library-overview.component.scss']
})
export class LibraryOverviewComponent implements AfterContentInit {

  defaultSortOption: SortOption = new SortOption("Title (A-Z)", "title", "asc");

  sortOptions: SortOption[] = [
    this.defaultSortOption,
    new SortOption("Title (Z-A)", "title", "desc"),

    new SortOption("Release (newest first)", "releaseDate", "desc"),
    new SortOption("Release (oldest first)", "releaseDate", "asc"),

    new SortOption("Added to library (newest first)", "addedToLibrary", "desc"),
    new SortOption("Added to library (oldest first)", "addedToLibrary", "asc"),

    new SortOption("Rating (highest first)", "totalRating", "desc"),
    new SortOption("Rating (lowest first)", "totalRating", "asc")
  ];

  searchTerm: string = "";
  selectedSortOption: SortOption = this.defaultSortOption;
  offlineCoopFilterEnabled: boolean = false;
  onlineCoopFilterEnabled: boolean = false;
  lanSupportFilterEnabled: boolean = false;
  activeThemeFilters: string[] = [];
  activeGenreFilters: string[] = [];
  activePlayerPerspectiveFilters: string[] = [];
  activePlatformFilters: string[] = [];

  games: DetectedGameDto[] = [];
  availableGenres: GenreDto[] = [];
  availableThemes: ThemeDto[] = [];
  availablePlayerPerspectives: PlayerPerspectiveDto[] = [];
  availablePlatforms: PlatformDto[] = [];

  loading: boolean = true;
  gameLibraryIsEmpty: boolean = false;
  private previousStateParams: Params = {};

  constructor(private gameServerService: GamesService,
              private route: ActivatedRoute,
              private router: Router,
              private location: Location) {
  }

  ngAfterContentInit(): void {
    this.gameServerService.getAllGames().subscribe(
      detectedGames => {
        if (detectedGames.length === 0) {
          this.gameLibraryIsEmpty = true;
          this.loading = false;
          return;
        }

        this.games = detectedGames;

        let genreObservable: Observable<ThemeDto[]> = this.gameServerService.getAvailableGenres();
        let themeObservable: Observable<GenreDto[]> = this.gameServerService.getAvailableThemes();
        let playerPerspectiveObservable: Observable<PlayerPerspectiveDto[]> = this.gameServerService.getAvailablePlayerPerspectives();
        let platformObservable: Observable<PlatformDto[]> = this.gameServerService.getAvailablePlatforms();

        forkJoin([genreObservable, themeObservable, playerPerspectiveObservable, platformObservable]).subscribe(result => {
          this.availableGenres = result[0];
          this.availableThemes = result[1];
          this.availablePlayerPerspectives = result[2];
          this.availablePlatforms = result[3];

          this.previousStateParams = this.route.snapshot.queryParams;
          if (this.previousStateParams['search'] !== undefined) this.searchTerm = this.previousStateParams['search'];
          if (this.previousStateParams['sort'] !== undefined) this.selectedSortOption = this.matchSelectedSortOptionFromParam(this.previousStateParams['sort']);
          if (this.previousStateParams['gamemodes'] !== undefined) this.setSelectedGamemodesFromParam(this.previousStateParams['gamemodes']);
          if (this.previousStateParams['genres'] !== undefined) this.activeGenreFilters = this.matchSelectedFilters(this.availableGenres, this.previousStateParams['genres']);
          if (this.previousStateParams['themes'] !== undefined) this.activeThemeFilters = this.matchSelectedFilters(this.availableThemes, this.previousStateParams['themes']);
          if (this.previousStateParams['playerPerspectives'] !== undefined) this.activePlayerPerspectiveFilters = this.matchSelectedFilters(this.availablePlayerPerspectives, this.previousStateParams['playerPerspectives']);
          if (this.previousStateParams['platforms'] !== undefined) this.activePlatformFilters = this.matchSelectedFilters(this.availablePlatforms, this.previousStateParams['platforms']);

          this.refreshLibraryView().then(() => this.loading = false);
        });
      }
    );
  }

  async refreshLibraryView(): Promise<void> {
    let games: DetectedGameDto[] = await firstValueFrom(this.gameServerService.getAllGames());
    this.games = this.sortGames(this.filterGames(games));
    this.saveStateToRoute();
  }

  clearSearchTerm(): void {
    this.searchTerm = "";
    this.refreshLibraryView();
  }

  filterGames(games: DetectedGameDto[]): DetectedGameDto[] {
    if (this.searchTerm.trim().toLowerCase().length > 0) {
      games = games.filter(game => game.title.trim().toLowerCase().includes(this.searchTerm.trim().toLowerCase()));
    }

    if (this.offlineCoopFilterEnabled || this.onlineCoopFilterEnabled || this.lanSupportFilterEnabled) {
      games = games.filter(game => (game.offlineCoop === this.offlineCoopFilterEnabled || game.onlineCoop === this.onlineCoopFilterEnabled || game.lanSupport === this.lanSupportFilterEnabled));
    }

    if (this.activeGenreFilters.length > 0) {
      games = games.filter(game => this.activeGenreFilters.every(activeGenreFilter => game.genres?.map(g => g.slug).includes(activeGenreFilter)));
    }

    if (this.activeThemeFilters.length > 0) {
      games = games.filter(game => this.activeThemeFilters.every(activeThemeFilter => game.themes?.map(g => g.slug).includes(activeThemeFilter)));
    }

    if (this.activePlayerPerspectiveFilters.length > 0) {
      games = games.filter(game => this.activePlayerPerspectiveFilters.every(activePlayerPerspectiveFilter => game.playerPerspectives?.map(g => g.slug).includes(activePlayerPerspectiveFilter)));
    }

    if (this.activePlatformFilters.length > 0) {
      games = games.filter(game => this.activePlatformFilters.some(activePlatformFilter =>
        game?.library?.platforms?.map(g => g.slug).includes(activePlatformFilter) && game?.platforms?.map(g => g.slug).includes(activePlatformFilter)));
    }

    return games;
  }

  sortGames(games: DetectedGameDto[]): DetectedGameDto[] {
    games = games.sort((g1, g2) => {
      // @ts-ignore
      let f1 = g1[this.selectedSortOption.field];
      // @ts-ignore
      let f2 = g2[this.selectedSortOption.field];

      if (f1 > f2) return 1;
      if (f1 < f2) return -1;
      return 0;
    });
    if (this.selectedSortOption.direction === "desc") games = games.reverse();
    return games;
  }

  toggleGenreFilter(slug: string): void {
    if (this.activeGenreFilters.includes(slug)) {

      const index = this.activeGenreFilters.indexOf(slug, 0);
      if (index > -1) {
        this.activeGenreFilters.splice(index, 1);
      }

    } else {
      this.activeGenreFilters.push(slug);
    }

    this.refreshLibraryView();
  }

  toggleThemeFilter(slug: string) {
    if (this.activeThemeFilters.includes(slug)) {

      const index = this.activeThemeFilters.indexOf(slug, 0);
      if (index > -1) {
        this.activeThemeFilters.splice(index, 1);
      }

    } else {
      this.activeThemeFilters.push(slug);
    }

    this.refreshLibraryView();
  }

  togglePlayerPerspectiveFilter(slug: string) {
    if (this.activePlayerPerspectiveFilters.includes(slug)) {

      const index = this.activePlayerPerspectiveFilters.indexOf(slug, 0);
      if (index > -1) {
        this.activePlayerPerspectiveFilters.splice(index, 1);
      }

    } else {
      this.activePlayerPerspectiveFilters.push(slug);
    }

    this.refreshLibraryView();
  }

  togglePlatformFilter(slug: string): void {
    if (this.activePlatformFilters.includes(slug)) {

      const index = this.activePlatformFilters.indexOf(slug, 0);
      if (index > -1) {
        this.activePlatformFilters.splice(index, 1);
      }

    } else {
      this.activePlatformFilters.push(slug);
    }

    this.refreshLibraryView();
  }

  private saveStateToRoute(): void {
    let newStateParams: Params = {};

    if (this.searchTerm.trim().length > 0) newStateParams['search'] = this.searchTerm;
    if (this.selectedSortOption !== this.defaultSortOption) newStateParams['sort'] = LibraryOverviewComponent.toParam(this.selectedSortOption);
    if (this.getActiveGameModesFilters().length > 0) newStateParams['gamemodes'] = this.getActiveGameModesFilters().join(',');
    if (this.activeGenreFilters.length > 0) newStateParams['genres'] = this.activeGenreFilters.join(',');
    if (this.activeThemeFilters.length > 0) newStateParams['themes'] = this.activeThemeFilters.join(',');
    if (this.activePlayerPerspectiveFilters.length > 0) newStateParams['playerPerspectives'] = this.activePlayerPerspectiveFilters.join(',');
    if (this.activePlatformFilters.length > 0) newStateParams['platforms'] = this.activePlatformFilters.join(',');

    // only update the route if it changed
    if (JSON.stringify(this.previousStateParams) !== JSON.stringify(newStateParams)) {
      const url = this.router.createUrlTree([], {relativeTo: this.route, queryParams: newStateParams}).toString();
      this.previousStateParams = newStateParams;
      this.location.go(url);
    }
  }

  private static toParam(sortOption: SortOption): string {
    return `${sortOption.field}_${sortOption.direction}`;
  }

  private matchSelectedSortOptionFromParam(sortParam: string): SortOption {
    return this.sortOptions.find(s => sortParam === LibraryOverviewComponent.toParam(s)) ?? this.defaultSortOption;
  }

  private matchSelectedFilters(options: any[], paramString: string): string[] {
    let params: string[] = paramString.split(",");
    return options.filter(o => params.includes(o.slug)).map(o => o.slug);
  }

  private getActiveGameModesFilters(): string[] {
    let activeFilters: string[] = [];

    if (this.offlineCoopFilterEnabled) activeFilters.push('offlineCoop');
    if (this.onlineCoopFilterEnabled) activeFilters.push('onlineCoop');
    if (this.lanSupportFilterEnabled) activeFilters.push('lanSupport');

    return activeFilters;
  }

  private setSelectedGamemodesFromParam(paramString: string): void {
    let params: string[] = paramString.split(",");

    if (params.includes('offlineCoop')) this.offlineCoopFilterEnabled = true;
    if (params.includes('onlineCoop')) this.onlineCoopFilterEnabled = true;
    if (params.includes('lanSupport')) this.lanSupportFilterEnabled = true;
  }

}
