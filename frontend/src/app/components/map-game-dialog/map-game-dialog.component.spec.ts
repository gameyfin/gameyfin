import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MapGameDialogComponent } from './map-game-dialog.component';

describe('MapGameDialogComponent', () => {
  let component: MapGameDialogComponent;
  let fixture: ComponentFixture<MapGameDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ MapGameDialogComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MapGameDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
