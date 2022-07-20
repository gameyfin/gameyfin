import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiErrorResponse } from '../models/dtos/ApiErrorResponse';
import { DialogService } from '../services/dialog.service';

@Injectable()
export class ErrorInterceptor implements HttpInterceptor {
  constructor(private dialogService: DialogService) {
  }

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(request).pipe(catchError((err: ApiErrorResponse) => {
      switch (err.status) {
        case 400:
          if (err.error.message === 'Validation error') {
            this.dialogService.showErrorDialog(JSON.stringify(err.error.errors));
          } else {
            this.dialogService.showErrorDialog(err.error.message);
          }
          break;
        case 401:
          this.dialogService.showErrorDialog(err.error.message);
          break;
        case 409:
        case 500:
          this.dialogService.showErrorDialog(err.error.message);
          break;
        case 503:
        case 504:
          this.dialogService.showErrorDialog('Can\'t reach the backend at the moment.\n' +
            'Please ensure that the backend is running and try again');
          break;
      }
      return throwError(err);
    }));
  }
}
