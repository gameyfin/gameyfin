import { Component, OnInit } from '@angular/core';
import {ActivatedRoute, Router} from "@angular/router";
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";
import {GamesService} from "../../services/games.service";
import {HttpErrorResponse} from "@angular/common/http";
import {takeWhile} from "rxjs";

@Component({
  selector: 'app-game-detail-view',
  templateUrl: './game-detail-view.component.html',
  styleUrls: ['./game-detail-view.component.scss']
})
export class GameDetailViewComponent implements OnInit {

  game!: DetectedGameDto;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private gamesService: GamesService) {
    this.route.params.subscribe( params => {
      this.gamesService.getGame(params['slug']).subscribe({
          next: game => this.game = game,
          error: error => {
            if(error.status === 404) {
              this.router.navigate(['/library']);
            } else {
              console.error(error);
            }
          }
        });
    });
  }

  ngOnInit(): void {
  }

  public downloadGame(): void {
    this.gamesService.downloadGame(this.game.slug);
  }

  public bytesAsHumanReadableString(bytes: number): string {
    const thresh = 1024;

    if (Math.abs(bytes) < thresh) {
      return bytes + ' B';
    }

    const units = ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
    const dp = 1;
    let u = -1;
    const r = 10**dp;

    do {
      bytes /= thresh;
      ++u;
    } while (Math.round(Math.abs(bytes) * r) / r >= thresh && u < units.length - 1);

    return bytes.toFixed(dp) + ' ' + units[u];
  }

}
