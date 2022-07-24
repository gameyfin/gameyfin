import {Component, Input, OnInit} from '@angular/core';
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";

@Component({
  selector: 'game-cover',
  templateUrl: './game-cover.component.html',
  styleUrls: ['./game-cover.component.scss']
})
export class GameCoverComponent implements OnInit {

  @Input() game!: DetectedGameDto;

  constructor() {
  }

  ngOnInit(): void {
  }

}
