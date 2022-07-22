import { Component, OnInit } from '@angular/core';
import {ActivatedRoute, Router} from "@angular/router";
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";
import {GamesService} from "../../services/games.service";
import {HttpErrorResponse} from "@angular/common/http";

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

}
