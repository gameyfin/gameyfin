import {AfterViewInit, Component} from '@angular/core';
import {GamesService} from "../../services/games.service";
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";

@Component({
  selector: 'app-gameserver-list',
  templateUrl: './gameserver-list.component.html',
  styleUrls: ['./gameserver-list.component.css']
})
export class GameserverListComponent implements AfterViewInit {

  detectedGames: DetectedGameDto[] = [];
  loading: boolean = true;

  constructor(private gameServerService: GamesService) {
  }

  ngAfterViewInit(): void {
    this.gameServerService.getAllGames().subscribe(
      (detectedGames: DetectedGameDto[]) => {
        this.detectedGames = detectedGames;
        this.loading = false;
      }
    );
  }

}
