import {Component, Inject, OnInit} from '@angular/core';
import {LibraryManagementService} from "../../services/library-management.service";
import {MAT_DIALOG_DATA, MatDialogRef} from "@angular/material/dialog";
import {PathToSlugDto} from "../../models/dtos/PathToSlugDto";
import {DialogService} from "../../services/dialog.service";
import {ApiErrorResponse} from "../../models/dtos/ApiErrorResponse";
import {PlatformDto} from "../../models/dtos/PlatformDto";

@Component({
  selector: 'app-map-library-dialog',
  templateUrl: './map-library-dialog.component.html',
  styleUrls: ['./map-library-dialog.component.scss']
})
export class MapLibraryDialogComponent implements OnInit {

  path: string;
  slugs: string;
  previousSlugs: string;

  autocompletePlatformSuggestions: PlatformDto[] = [];

  submitLoading: boolean = false;
  suggestionsLoading: boolean = false;

  constructor(private libraryManagementService: LibraryManagementService,
              private dialogService: DialogService,
              public dialogRef: MatDialogRef<MapLibraryDialogComponent>,
              @Inject(MAT_DIALOG_DATA) data: any) {
    this.path = data.path;
    this.slugs = data.slugs ?? '';
    this.previousSlugs = data.previousSlugs ?? '';
  }

  ngOnInit() {
    this.loadInitialSuggestions();
  }

  submit(): void {
    this.submitLoading = true;
    this.libraryManagementService.mapLibrary(new PathToSlugDto(Array.isArray(this.slugs) ? this.slugs.join(',') : this.slugs, this.path)).subscribe({
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
    let extractedPlatformFromPath: string = this.path.match(/([^\\/]*)[\\/]*$/)![1];
    // Match it until the first special characters
    extractedPlatformFromPath = extractedPlatformFromPath.match(/^[a-zA-Z0-9:\- ]+/)![0];

    if(extractedPlatformFromPath == null) {
      this.suggestionsLoading = false;
      return;
    }

    this.libraryManagementService.getPlatforms(extractedPlatformFromPath, 10).subscribe({
      next: suggestions => {
        this.autocompletePlatformSuggestions = suggestions;
        this.suggestionsLoading = false;
      },
      error: () => this.suggestionsLoading = false
    })
  }

  loadSuggestions(): void {
    this.suggestionsLoading = true;
    let searchTerm = '';
    if (this.slugs.length > 0) {
      let slugArray = this.slugs.split(',');
      // pop off the search term after the last comma
      searchTerm = slugArray.pop() ?? '';
      // if we already had slugs in our input field we need to add them back again
      this.previousSlugs = (slugArray.length > 0 ? slugArray.join(',') + ',' : '');
    }
    this.libraryManagementService.getPlatforms(searchTerm, 50).subscribe({
      next: suggestions => {
        this.autocompletePlatformSuggestions = suggestions;
        this.suggestionsLoading = false;
      },
      error: () => this.suggestionsLoading = false
    })
  }
}
