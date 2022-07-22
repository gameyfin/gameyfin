import {Observable} from "rxjs";
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";
import {GameOverviewDto} from "../models/dtos/GameOverviewDto";
import {HttpResponse} from "@angular/common/http";

export interface LibraryApi {
  scanLibrary(): Observable<HttpResponse<Response>>;
  downloadImages(): Observable<HttpResponse<Response>>;
  getFiles(): Observable<string[]>;
}
