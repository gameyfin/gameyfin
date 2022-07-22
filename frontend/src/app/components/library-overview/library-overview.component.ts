import {AfterViewInit, Component} from '@angular/core';
import {GamesService} from "../../services/games.service";
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";
import {GameOverviewDto} from "../../models/dtos/GameOverviewDto";

@Component({
  selector: 'app-gameserver-list',
  templateUrl: './library-overview.component.html',
  styleUrls: ['./library-overview.component.css']
})
export class LibraryOverviewComponent implements AfterViewInit {

  detectedGames: GameOverviewDto[] = [];
  loading: boolean = true;

  constructor(private gameServerService: GamesService) {
  }

  ngAfterViewInit(): void {
    this.gameServerService.getGameOverviews().subscribe(
      (detectedGames: GameOverviewDto[]) => {
        this.detectedGames = detectedGames;
        this.loading = false;
      }
    );
  }

}
