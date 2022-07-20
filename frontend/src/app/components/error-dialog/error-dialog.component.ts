import {Component, Inject, OnInit} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';

@Component({
  selector: 'app-error-dialog',
  template: `
    <h1 mat-dialog-title>Error</h1>
    <mat-dialog-content>
      {{message}}
    </mat-dialog-content>
    <mat-dialog-actions style="justify-content: end">
      <button mat-raised-button color="primary" (click)="onClick()">OK</button>
    </mat-dialog-actions>
  `
})
export class ErrorDialogComponent implements OnInit {

  message: string;

  constructor(public dialogRef: MatDialogRef<ErrorDialogComponent>,
              @Inject(MAT_DIALOG_DATA) data: any) {
    this.message = data.message;
  }

  ngOnInit() {
  }

  onClick(): void {
    this.dialogRef.close();
  }

}
