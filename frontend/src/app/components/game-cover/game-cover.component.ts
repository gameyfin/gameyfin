import {Component, Input, OnInit} from '@angular/core';
import {GameOverviewDto} from "../../models/dtos/GameOverviewDto";

@Component({
  selector: 'game-cover',
  templateUrl: './game-cover.component.html',
  styleUrls: ['./game-cover.component.scss']
})
export class GameCoverComponent implements OnInit {

  @Input() game!: GameOverviewDto;

  constructor() {
  }

  ngOnInit(): void {
  }

}
