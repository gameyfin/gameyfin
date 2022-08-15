import { TestBed } from '@angular/core/testing';

import { ThemingService } from './theming.service';

describe('ThemingService', () => {
  let service: ThemingService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ThemingService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
