import { HttpErrorResponse } from '@angular/common/http';

export interface ApiErrorResponse extends HttpErrorResponse {
  error: {
    timestamp: Date;
    error: string;
    status: number;
    errors: object[];
    message: string;
    path: string;
  };
}
