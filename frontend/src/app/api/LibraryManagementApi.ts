import {PathToSlugDto} from "../models/dtos/PathToSlugDto";
import {Observable} from "rxjs";
import {DetectedGameDto} from "../models/dtos/DetectedGameDto";
import {UnmappedFileDto} from "../models/dtos/UnmappedFileDto";

export interface LibraryManagementApi {
  mapGame(pathToSlugDto: PathToSlugDto): Observable<DetectedGameDto>;
  getUnmappedFiles(): Observable<UnmappedFileDto[]>;
  confirmGameMapping(slug: string): Observable<DetectedGameDto>;
  deleteGame(slug: string): Observable<Response>;
  deleteUnmappedFile(id: number): Observable<Response>;
}
