import {Injectable} from '@angular/core';
import {HttpClient, HttpParams} from "@angular/common/http";
import {Observable} from "rxjs";
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";
import {PathToSlugDto} from "../models/dtos/PathToSlugDto";
import {UnmappedFileDto} from "../models/dtos/UnmappedFileDto";
import {LibraryManagementApi} from "../api/LibraryManagementApi";
import {GamesService} from "./games.service";
import {AutocompleteSuggestionDto} from "../models/dtos/AutocompleteSuggestionDto";
import {LibraryDto} from "../models/dtos/LibraryDto";
import {PlatformDto} from "../models/dtos/PlatformDto";

@Injectable({
  providedIn: 'root'
})
export class LibraryManagementService implements LibraryManagementApi {

  private readonly apiPath = '/library-management';

  constructor(private http: HttpClient,
              private gamesService: GamesService) {
  }

  mapGame(pathToSlugDto: PathToSlugDto): Observable<DetectedGameDto> {
    return this.http.post<DetectedGameDto>(`${this.apiPath}/map-path`, pathToSlugDto);
  }

  getUnmappedFiles(): Observable<UnmappedFileDto[]> {
    return this.http.get<UnmappedFileDto[]>(`${this.apiPath}/unmapped-files`);
  }

  confirmGameMapping(slug: string, confirm: boolean): Observable<DetectedGameDto> {
    let queryParams = new HttpParams();
    queryParams = queryParams.append("confirm", confirm);

    return this.http.get<DetectedGameDto>(`${this.apiPath}/confirm-game/${slug}`, {params:queryParams});
  }

  deleteGame(slug: string): Observable<Response> {
    this.gamesService.removeGameFromCache(slug);
    return this.http.delete<Response>(`${this.apiPath}/delete-game/${slug}`);
  }

  deleteUnmappedFile(id: number): Observable<Response> {
    return this.http.delete<Response>(`${this.apiPath}/delete-unmapped-file/${id}`);
  }

  getAutocompleteSuggestions(searchTerm: string, limit: number): Observable<AutocompleteSuggestionDto[]> {
    let queryParams = new HttpParams();
    queryParams = queryParams.append("searchTerm", searchTerm);
    queryParams = queryParams.append("limit", limit);

    return this.http.get<AutocompleteSuggestionDto[]>(`${this.apiPath}/autocomplete-suggestions`, {params:queryParams})
  }

  getPlatforms(searchTerm: string, limit: number): Observable<PlatformDto[]> {
    let queryParams = new HttpParams();
    queryParams = queryParams.append("searchTerm", searchTerm);
    queryParams = queryParams.append("limit", limit);

    return this.http.get<PlatformDto[]>(`${this.apiPath}/platforms`, {params:queryParams})
  }

  mapLibrary(pathToSlugDto: PathToSlugDto): Observable<LibraryDto> {
    return this.http.post<LibraryDto>(`${this.apiPath}/map-library`, pathToSlugDto);
  }

  getLibraries(): Observable<LibraryDto[]> {
    return this.http.get<LibraryDto[]>(`${this.apiPath}/libraries`);
  }
}
