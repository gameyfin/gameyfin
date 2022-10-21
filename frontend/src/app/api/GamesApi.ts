import {Observable} from "rxjs";
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";
import {GameOverviewDto} from "../models/dtos/GameOverviewDto";

export interface GamesApi {
  getAllGames(): Observable<DetectedGameDto[]>;
  getGame(slug: String): Observable<DetectedGameDto>;
  getGameOverviews(): Observable<GameOverviewDto[]>;
  getAllGameMappings(): Observable<Map<string, string>>;
  refreshGame(slug: String): Observable<DetectedGameDto>;
}
