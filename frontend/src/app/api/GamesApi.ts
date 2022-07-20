import {Observable} from "rxjs";
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";

export interface GamesApi {
  getAllGames(): Observable<DetectedGameDto[]>;
  getAllGameMappings(): Observable<Map<string, string>>;
}
