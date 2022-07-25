import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LibraryManagementComponent } from './library-management.component';

describe('LibraryManagementComponent', () => {
  let component: LibraryManagementComponent;
  let fixture: ComponentFixture<LibraryManagementComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ LibraryManagementComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LibraryManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
