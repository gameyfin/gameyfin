import {Component, Inject, OnInit} from '@angular/core';
import {LibraryManagementService} from "../../services/library-management.service";
import {MAT_LEGACY_DIALOG_DATA as MAT_DIALOG_DATA, MatLegacyDialogRef as MatDialogRef} from "@angular/material/legacy-dialog";
import {PathToSlugDto} from "../../models/dtos/PathToSlugDto";
import {DialogService} from "../../services/dialog.service";
import {ApiErrorResponse} from "../../models/dtos/ApiErrorResponse";
import {AutocompleteSuggestionDto} from "../../models/dtos/AutocompleteSuggestionDto";

@Component({
  selector: 'app-map-game-dialog',
  templateUrl: './map-game-dialog.component.html',
  styleUrls: ['./map-game-dialog.component.scss']
})
export class MapGameDialogComponent implements OnInit {

  path: string;
  slug: string;

  autocompleteSuggestions: AutocompleteSuggestionDto[] = [];

  submitLoading: boolean = false;
  suggestionsLoading: boolean = false;

  constructor(private libraryManagementService: LibraryManagementService,
              private dialogService: DialogService,
              public dialogRef: MatDialogRef<MapGameDialogComponent>,
              @Inject(MAT_DIALOG_DATA) data: any) {
    this.path = data.path;
    this.slug = data.slug ?? '';
  }

  ngOnInit() {
    this.loadInitialSuggestions();
  }

  submit(): void {
    this.submitLoading = true;
    this.libraryManagementService.mapGame(new PathToSlugDto(this.slug, this.path)).subscribe({
        next: () => this.dialogRef.close(true),
        error: (error: ApiErrorResponse) => {
          this.dialogRef.close(false);
          this.dialogService.showErrorDialog(error.error.message);
        }
      }
    )
  }

  loadInitialSuggestions(): void {
    this.suggestionsLoading = true;

    // Extract the last path element (folder name / file name)
    let extractedTitleFromPath: string = this.path.match(/([^\\/]*)[\\/]*$/)![1];
    // Match it until the first special characters
    extractedTitleFromPath = extractedTitleFromPath.match(/^[a-zA-Z0-9:\- ]+/)![0];

    if(extractedTitleFromPath == null) {
      this.suggestionsLoading = false;
      return;
    }

    this.libraryManagementService.getAutocompleteSuggestions(extractedTitleFromPath, 10).subscribe({
      next: suggestions => {
        this.autocompleteSuggestions = suggestions;
        this.suggestionsLoading = false;
      },
      error: () => this.suggestionsLoading = false
    })
  }

  loadSuggestions(): void {
    this.suggestionsLoading = true;
    this.libraryManagementService.getAutocompleteSuggestions(this.slug, 50).subscribe({
      next: suggestions => {
        this.autocompleteSuggestions = suggestions;
        this.suggestionsLoading = false;
      },
      error: () => this.suggestionsLoading = false
    })
  }

  getFullYearFromTimestamp(timestamp: number): number {
    return new Date(timestamp).getFullYear();
  }
}
