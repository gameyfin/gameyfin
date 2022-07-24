import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameScreenshotComponent } from './game-screenshot.component';

describe('GameScreenshotComponent', () => {
  let component: GameScreenshotComponent;
  let fixture: ComponentFixture<GameScreenshotComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ GameScreenshotComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GameScreenshotComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
