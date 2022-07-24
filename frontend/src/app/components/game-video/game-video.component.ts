import {Component, Input, OnInit} from '@angular/core';

@Component({
  selector: 'game-video',
  templateUrl: './game-video.component.html',
  styleUrls: ['./game-video.component.scss']
})
export class GameVideoComponent implements OnInit {

  @Input() videoId!: string;
  @Input() height!: number;
  @Input() width!: number;

  constructor() { }

  ngOnInit(): void {
    const tag = document.createElement('script');
    tag.src = 'https://www.youtube.com/iframe_api';
    document.body.appendChild(tag);
  }

}
