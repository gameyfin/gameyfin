import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {LibraryApi} from "../api/LibraryApi";
import {LibraryScanResultDto} from "../models/dtos/LibraryScanResultDto";
import {ImageDownloadResultDto} from "../models/dtos/ImageDownloadResultDto";

@Injectable({
  providedIn: 'root'
})
export class LibraryService implements LibraryApi {

  private readonly apiPath = '/library';

  constructor(private http: HttpClient) {
  }

  scanLibrary(): Observable<LibraryScanResultDto> {
    return this.http.get<LibraryScanResultDto>(`${this.apiPath}/scan`);
  }

  downloadImages(): Observable<ImageDownloadResultDto> {
    return this.http.get<ImageDownloadResultDto>(`${this.apiPath}/download-images`);
  }

  getFiles(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiPath}/files`);
  }
}
