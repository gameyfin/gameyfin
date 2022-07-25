import {Component, Inject, OnInit} from '@angular/core';
import {FormBuilder, FormControl} from "@angular/forms";
import {LibraryManagementService} from "../../services/library-management.service";
import {MAT_DIALOG_DATA, MatDialogRef} from "@angular/material/dialog";
import {PathToSlugDto} from "../../models/dtos/PathToSlugDto";

@Component({
  selector: 'app-map-game-dialog',
  templateUrl: './map-game-dialog.component.html',
  styleUrls: ['./map-game-dialog.component.scss']
})
export class MapGameDialogComponent implements OnInit {

  path: string;
  currentSlug?: string;
  newSlugInput: FormControl;

  constructor(private fb: FormBuilder,
              private libraryManagementService: LibraryManagementService,
              public dialogRef: MatDialogRef<MapGameDialogComponent>,
              @Inject(MAT_DIALOG_DATA) data: any) {
    this.path = data.path;
    this.currentSlug = data.slug;
    this.newSlugInput = new FormControl(this.currentSlug);
  }

  ngOnInit() {
  }

  close() {
    this.dialogRef.close();
  }

  submit(): void {
    this.libraryManagementService.mapGame(new PathToSlugDto(this.newSlugInput.value, this.path)).subscribe(() => this.close())
  }
}
