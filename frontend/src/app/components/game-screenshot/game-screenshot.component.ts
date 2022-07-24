import {Component, Input, OnInit} from '@angular/core';

@Component({
  selector: 'game-screenshot',
  templateUrl: './game-screenshot.component.html',
  styleUrls: ['./game-screenshot.component.scss']
})
export class GameScreenshotComponent implements OnInit {

  @Input() screenshotId!: string;

  constructor() { }

  ngOnInit(): void {
  }

}
