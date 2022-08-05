import {AfterContentInit, AfterViewInit, Component} from '@angular/core';
import {GamesService} from "../../services/games.service";
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";

@Component({
  selector: 'app-gameserver-list',
  templateUrl: './library-overview.component.html',
  styleUrls: ['./library-overview.component.scss']
})
export class LibraryOverviewComponent implements AfterContentInit {

  detectedGames: DetectedGameDto[] = [];
  loading: boolean = true;

  constructor(private gameServerService: GamesService) {
  }

  ngAfterContentInit(): void {
    this.gameServerService.getAllGames().subscribe(
      (detectedGames: DetectedGameDto[]) => {
        this.detectedGames = detectedGames;
        this.loading = false;
      }
    );
  }

}
