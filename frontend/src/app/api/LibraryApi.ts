import {Observable} from "rxjs";
import {LibraryScanResultDto} from "../models/dtos/LibraryScanResultDto";
import {ImageDownloadResultDto} from "../models/dtos/ImageDownloadResultDto";
import {LibraryScanRequestDto} from "../models/dtos/LibraryScanRequestDto";

export interface LibraryApi {
  scanLibrary(mappedLibrary: LibraryScanRequestDto): Observable<LibraryScanResultDto>;

  downloadImages(): Observable<ImageDownloadResultDto>;

  getFiles(): Observable<string[]>;
}
