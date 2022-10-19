import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {LibraryApi} from "../api/LibraryApi";
import {LibraryScanResultDto} from "../models/dtos/LibraryScanResultDto";
import {ImageDownloadResultDto} from "../models/dtos/ImageDownloadResultDto";
import {LibraryDto} from "../models/dtos/LibraryDto";
import {LibraryScanRequestDto} from "../models/dtos/LibraryScanRequestDto";

@Injectable({
  providedIn: 'root'
})
export class LibraryService implements LibraryApi {

  private readonly apiPath = '/library';

  constructor(private http: HttpClient) {
  }

  scanLibrary(library: LibraryScanRequestDto): Observable<LibraryScanResultDto> {
    return this.http.post<LibraryScanResultDto>(`${this.apiPath}/scan`, library);
  }

  downloadImages(): Observable<ImageDownloadResultDto> {
    return this.http.get<ImageDownloadResultDto>(`${this.apiPath}/download-images`);
  }

  getFiles(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiPath}/files`);
  }
}
