import {AfterContentInit, AfterViewInit, Component, Input} from '@angular/core';
import {GamesService} from "../../services/games.service";
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";
import {GenreDto} from "../../models/dtos/GenreDto";
import {ThemeDto} from "../../models/dtos/ThemeDto";
import {forkJoin, Observable} from "rxjs";

@Component({
  selector: 'app-gameserver-list',
  templateUrl: './library-overview.component.html',
  styleUrls: ['./library-overview.component.scss']
})
export class LibraryOverviewComponent implements AfterContentInit {

  searchTerm: string = "";
  offlineCoopFilterEnabled: boolean = false;
  onlineCoopFilterEnabled: boolean = false;
  lanSupportFilterEnabled: boolean = false;
  activeThemeFilters: string[] = [];
  activeGenreFilters: string[] = [];

  games: DetectedGameDto[] = [];
  availableGenres: GenreDto[] = [];
  availableThemes: ThemeDto[] = [];

  loading: boolean = true;
  gameLibraryIsEmpty: boolean = false;

  constructor(private gameServerService: GamesService) {
  }

  ngAfterContentInit(): void {
    this.gameServerService.getAllGames().subscribe(
      detectedGames => {
        if(detectedGames.length === 0) {
          this.gameLibraryIsEmpty = true;
          return;
        }

        this.games = detectedGames;

        let genreObservable: Observable<ThemeDto[]> = this.gameServerService.getAvailableGenres();
        let themeObservable: Observable<GenreDto[]> = this.gameServerService.getAvailableThemes();

        forkJoin([themeObservable, genreObservable]).subscribe(result => {
          this.availableThemes = result[0];
          this.availableGenres = result[1];
          this.filterGames();
          this.loading = false;
        });
      }
    );
  }

  filterGames(): void {
    this.gameServerService.getAllGames().subscribe(games => {
      let filteredGames: DetectedGameDto[] = games;

      if(this.searchTerm.trim().toLowerCase().length > 0) {
        filteredGames = filteredGames.filter(game => game.title.trim().toLowerCase().includes(this.searchTerm.trim().toLowerCase()));
      }

      if(this.offlineCoopFilterEnabled || this.onlineCoopFilterEnabled || this.lanSupportFilterEnabled) {
        filteredGames = filteredGames.filter(game => (game.offlineCoop === this.offlineCoopFilterEnabled || game.onlineCoop === this.onlineCoopFilterEnabled || game.lanSupport === this.lanSupportFilterEnabled));
      }

      if(this.activeGenreFilters.length > 0) {
        filteredGames = filteredGames.filter(game => this.activeGenreFilters.every(activeGenreFilter => game.genres?.map(g => g.slug).includes(activeGenreFilter)));
      }

      if(this.activeThemeFilters.length > 0) {
        filteredGames = filteredGames.filter(game => this.activeThemeFilters.every(activeThemeFilter => game.themes?.map(g => g.slug).includes(activeThemeFilter)));
      }

      this.games = filteredGames;
    })
  }

  toggleGenreFilter(slug: string): void {
    if(this.activeGenreFilters.includes(slug)) {

      const index = this.activeGenreFilters.indexOf(slug, 0);
      if (index > -1) {
        this.activeGenreFilters.splice(index, 1);
      }

    } else {
      this.activeGenreFilters.push(slug);
    }

    this.filterGames();
  }

  toggleThemeFilter(slug: string) {
    if(this.activeThemeFilters.includes(slug)) {

      const index = this.activeThemeFilters.indexOf(slug, 0);
      if (index > -1) {
        this.activeThemeFilters.splice(index, 1);
      }

    } else {
      this.activeThemeFilters.push(slug);
    }

    this.filterGames();
  }

}
