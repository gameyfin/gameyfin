import {Injectable} from '@angular/core';
import {HttpClient, HttpResponse} from "@angular/common/http";
import {Observable} from "rxjs";
import {LibraryApi} from "../api/LibraryApi";

@Injectable({
  providedIn: 'root'
})
export class LibraryService implements LibraryApi {

  private readonly apiPath = '/library';

  constructor(private http: HttpClient) {
  }

  scanLibrary(): Observable<HttpResponse<Response>> {
    return this.http.get<HttpResponse<Response>>(`${this.apiPath}/scan`);
  }

  downloadImages(): Observable<HttpResponse<Response>> {
    return this.http.get<HttpResponse<Response>>(`${this.apiPath}/download-images`);
  }

  getFiles(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiPath}/files`);
  }
}
