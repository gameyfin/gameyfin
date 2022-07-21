import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameDetailViewComponent } from './game-detail-view.component';

describe('GameDetailViewComponent', () => {
  let component: GameDetailViewComponent;
  let fixture: ComponentFixture<GameDetailViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ GameDetailViewComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GameDetailViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
