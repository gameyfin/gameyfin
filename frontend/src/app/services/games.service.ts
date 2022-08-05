import {Injectable} from '@angular/core';
import {GamesApi} from "../api/GamesApi";
import {HttpClient} from "@angular/common/http";
import {map, Observable} from "rxjs";
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";
import {GameOverviewDto} from "../models/dtos/GameOverviewDto";

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
