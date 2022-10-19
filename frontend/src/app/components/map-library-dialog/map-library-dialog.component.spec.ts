import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MapLibraryDialogComponent } from './map-library-dialog.component';

describe('MapLibraryDialogComponent', () => {
  let component: MapLibraryDialogComponent;
  let fixture: ComponentFixture<MapLibraryDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ MapLibraryDialogComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MapLibraryDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
