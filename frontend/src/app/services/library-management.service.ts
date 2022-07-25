import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";
import {PathToSlugDto} from "../models/dtos/PathToSlugDto";
import {UnmappedFileDto} from "../models/dtos/UnmappedFileDto";
import {LibraryManagementApi} from "../api/LibraryManagementApi";

@Injectable({
  providedIn: 'root'
})
export class LibraryManagementService implements LibraryManagementApi {

  private readonly apiPath = '/library-management';

  constructor(private http: HttpClient) {
  }

  mapGame(pathToSlugDto: PathToSlugDto): Observable<DetectedGameDto> {
    return this.http.post<DetectedGameDto>(`${this.apiPath}/map-path`, pathToSlugDto);
  }

  getUnmappedFiles(): Observable<UnmappedFileDto[]> {
    return this.http.get<UnmappedFileDto[]>(`${this.apiPath}/unmapped-files`);
  }

  confirmGameMapping(slug: string): Observable<DetectedGameDto> {
    return this.http.get<DetectedGameDto>(`${this.apiPath}/confirm-game/${slug}`);
  }

  deleteGame(slug: string): Observable<Response> {
    return this.http.delete<Response>(`${this.apiPath}/delete-game/${slug}`);
  }

  deleteUnmappedFile(id: number): Observable<Response> {
    return this.http.delete<Response>(`${this.apiPath}/delete-unmapped-file/${id}`);
  }

}
