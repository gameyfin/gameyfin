import {Observable} from "rxjs";
import {LibraryScanResultDto} from "../models/dtos/LibraryScanResultDto";
import {ImageDownloadResultDto} from "../models/dtos/ImageDownloadResultDto";

export interface LibraryApi {
  scanLibrary(): Observable<LibraryScanResultDto>;

  downloadImages(): Observable<ImageDownloadResultDto>;

  getFiles(): Observable<string[]>;
}
