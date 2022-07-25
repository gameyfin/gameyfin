import { TestBed } from '@angular/core/testing';

import { LibraryManagementService } from './library-management.service';

describe('LibraryManagementService', () => {
  let service: LibraryManagementService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(LibraryManagementService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
