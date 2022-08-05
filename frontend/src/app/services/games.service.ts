import {Injectable} from '@angular/core';
import {GamesApi} from "../api/GamesApi";
import {HttpClient} from "@angular/common/http";
import {distinct, map, Observable} from "rxjs";
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";
import {GameOverviewDto} from "../models/dtos/GameOverviewDto";
import {GenreDto} from "../models/dtos/GenreDto";
import {ThemeDto} from "../models/dtos/ThemeDto";

@Injectable({
  providedIn: 'root'
})
export class GamesService implements GamesApi {

  private readonly apiPath = '/games';

  private cache: Map<string, DetectedGameDto> = new Map<string, DetectedGameDto>();

  constructor(private http: HttpClient) {
  }

  getAllGames(forceReloadFromServer: boolean = false): Observable<DetectedGameDto[]> {

    if (this.cache.size === 0 || forceReloadFromServer) {
      let gamesObservable: Observable<DetectedGameDto[]> = this.http.get<DetectedGameDto[]>(this.apiPath).pipe(map(games => games.sort((g1, g2) => g1.title.localeCompare(g2.title))));
      gamesObservable.subscribe(g => this.cacheGames(g));
      return gamesObservable;
    }

    return new Observable<DetectedGameDto[]>(subscriber => {
      subscriber.next(Array.from(this.cache.values()));
      subscriber.complete();
    });
  }

  getGame(slug: string): Observable<DetectedGameDto> {
    if (this.cache.has(slug)) {
      return new Observable<DetectedGameDto>(subscriber => {
        subscriber.next(this.cache.get(slug));
        subscriber.complete();
      });
    }

    return this.http.get<DetectedGameDto>(`${this.apiPath}/game/${slug}`);
  }

  // TODO: This method of removing duplicates is most certainly an anti-pattern in RxJS
  // TODO: However, I did not get the 'distinct()' pipe to work properly, so I have to take another look in the future
  getAvailableGenres(): Observable<GenreDto[]> {
    return this.getAllGames().pipe(
      map<DetectedGameDto[], GenreDto[]>(
        games => {
          let availableGenresMap: Map<string, GenreDto> = new Map<string, GenreDto>;
          games.map(game => game.genres === undefined ? [] : game.genres).flat().forEach(genre => availableGenresMap.set(genre.slug, genre));
          return Array.from(availableGenresMap.values()).sort((g1, g2) => g1.name.localeCompare(g2.name));
        }
      )
    );
  }

  // TODO: This method of removing duplicates is most certainly an anti-pattern in RxJS
  // TODO: However, I did not get the 'distinct()' pipe to work properly, so I have to take another look in the future
  getAvailableThemes(): Observable<ThemeDto[]> {
    return this.getAllGames().pipe(
      map<DetectedGameDto[], ThemeDto[]>(
        games => {
          let availableThemesMap: Map<string, ThemeDto> = new Map<string, GenreDto>;
          games.map(game => game.themes === undefined ? [] : game.themes).flat().forEach(theme => availableThemesMap.set(theme.slug, theme));
          return Array.from(availableThemesMap.values()).sort((t1, t2) => t1.name.localeCompare(t2.name));
        }
      )
    );
  }

  downloadGame(slug: String): void {
    window.open(`v1${this.apiPath}/game/${slug}/download`, '_top');
  }

  getGameOverviews(): Observable<GameOverviewDto[]> {
    return this.http.get<GameOverviewDto[]>(`${this.apiPath}/game-overviews`);
  }

  getAllGameMappings(): Observable<Map<string, string>> {
    return this.http.get<Map<string, string>>(`${this.apiPath}/game-mappings`);
  }

  removeGameFromCache(slug: string): void {
    this.cache.delete(slug);
  }

  private cacheGames(gameList: DetectedGameDto[]): void {
    this.cache.clear();
    gameList.forEach(game => this.cache.set(game.slug, game));
  }
}
