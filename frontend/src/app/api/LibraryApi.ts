import {Observable} from "rxjs";
import {HttpResponse} from "@angular/common/http";

export interface LibraryApi {
  scanLibrary(): Observable<HttpResponse<Response>>;
  downloadImages(): Observable<HttpResponse<Response>>;
  getFiles(): Observable<string[]>;
}
