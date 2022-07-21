import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameCoverComponent } from './game-cover.component';

describe('GameCoverComponent', () => {
  let component: GameCoverComponent;
  let fixture: ComponentFixture<GameCoverComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ GameCoverComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GameCoverComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
