import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameserverListComponent } from './gameserver-list.component';

describe('GameserverListComponent', () => {
  let component: GameserverListComponent;
  let fixture: ComponentFixture<GameserverListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ GameserverListComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(GameserverListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
