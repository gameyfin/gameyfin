import {PathToSlugDto} from "../models/dtos/PathToSlugDto";
import {Observable} from "rxjs";
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";
import {UnmappedFileDto} from "../models/dtos/UnmappedFileDto";
import {AutocompleteSuggestionDto} from "../models/dtos/AutocompleteSuggestionDto";
import {LibraryDto} from "../models/dtos/LibraryDto";

export interface LibraryManagementApi {
  mapGame(pathToSlugDto: PathToSlugDto): Observable<DetectedGameDto>;
  getUnmappedFiles(): Observable<UnmappedFileDto[]>;
  confirmGameMapping(slug: string, confirm: boolean): Observable<DetectedGameDto>;
  deleteGame(slug: string): Observable<Response>;
  deleteUnmappedFile(id: number): Observable<Response>;
  getAutocompleteSuggestions(searchTerm: string, limit: number): Observable<AutocompleteSuggestionDto[]>;
  getLibraries(): Observable<LibraryDto[]>;
}
