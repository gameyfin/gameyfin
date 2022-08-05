import {Injectable} from '@angular/core';
import {HttpClient, HttpParams} from "@angular/common/http";
import {Observable} from "rxjs";
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";
import {PathToSlugDto} from "../models/dtos/PathToSlugDto";
import {UnmappedFileDto} from "../models/dtos/UnmappedFileDto";
import {LibraryManagementApi} from "../api/LibraryManagementApi";
import {GamesService} from "./games.service";

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

}
