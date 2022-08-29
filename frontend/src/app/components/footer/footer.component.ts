import { Component, OnInit } from '@angular/core';
import packageJson from 'package.json';

@Component({
  selector: 'app-footer',
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.scss']
})
export class FooterComponent implements OnInit {

  githubUrl: string = "https://github.com/grimsi/gameyfin";
  gameyfinVersion: string = packageJson.version;
  date: Date = new Date();

  constructor() { }

  ngOnInit(): void {
  }

}
