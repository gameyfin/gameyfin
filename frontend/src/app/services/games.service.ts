import {Injectable} from '@angular/core';
import {GamesApi} from "../api/GamesApi";
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";

@Injectable({
  providedIn: 'root'
})
export class GamesService implements GamesApi {

  private readonly apiPath = '/games';

  constructor(private http: HttpClient) {
  }

  getAllGames(): Observable<DetectedGameDto[]> {
    return this.http.get<DetectedGameDto[]>(this.apiPath);
  }

  getAllGameMappings(): Observable<Map<string, string>> {
    return this.http.get<Map<string, string>>(`${this.apiPath}/game-mappings`);
  }
}
