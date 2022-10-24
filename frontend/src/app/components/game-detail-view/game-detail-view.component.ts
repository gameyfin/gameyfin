import {Component, HostListener} from '@angular/core';
import {ActivatedRoute, Params, Router} from "@angular/router";
import {DetectedGameDto} from "../../models/dtos/DetectedGameDto";
import {GamesService} from "../../services/games.service";
import {CompanyDto} from "../../models/dtos/CompanyDto";
import {LibraryDto} from "../../models/dtos/LibraryDto";
import {PlatformDto} from "../../models/dtos/PlatformDto";

@Component({
  selector: 'app-game-detail-view',
  templateUrl: './game-detail-view.component.html',
  styleUrls: ['./game-detail-view.component.scss']
})
export class GameDetailViewComponent {

  game!: DetectedGameDto;

  companiesWithLogo: CompanyDto[]= [];

  gridColumnCount: number;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private gamesService: GamesService) {
    this.gamesService.getGame(this.route.snapshot.params['slug']).subscribe({
      next: game => {
        this.game = game;
        if(game.companies !== undefined) {
          this.companiesWithLogo = game.companies.filter(c => c.logoId !== undefined && c.logoId.length > 0);
        }
      },
      error: error => {
        if (error.status === 404) {
          this.router.navigate(['/library']);
        } else {
          console.error(error);
        }
      }
    });

    this.gridColumnCount = this.calculateColumnCount();
  }

  @HostListener('window:resize', ['$event'])
  onResize() {
    this.gridColumnCount = this.calculateColumnCount();
  }

  public downloadGame(): void {
    this.gamesService.downloadGame(this.game.slug);
  }

  public refreshGame(): void {
    this.gamesService.refreshGame(this.game.slug).subscribe({
      next: game => {
        this.game = game;
        if(game.companies !== undefined) {
          this.companiesWithLogo = game.companies.filter(c => c.logoId !== undefined && c.logoId.length > 0);
        }
      },
      error: error => {
        if (error.status === 404) {
          this.router.navigate(['/library']);
        } else {
          console.error(error);
        }
      }
   });
  }

  public bytesAsHumanReadableString(bytes: number): string {
    const thresh = 1024;

    if (Math.abs(bytes) < thresh) {
      return bytes + ' B';
    }

    const units = ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
    const dp = 1;
    let u = -1;
    const r = 10 ** dp;

    do {
      bytes /= thresh;
      ++u;
    } while (Math.round(Math.abs(bytes) * r) / r >= thresh && u < units.length - 1);

    return bytes.toFixed(dp) + ' ' + units[u];
  }

  goToLibraryWithFilter(field: string, value: string) {
    let params: Params = {};
    params[field] = value;
    this.router.navigate(['/library'], {queryParams: params});
  }

  mapRatingToColor(rating: number): string {
    if (rating >= 75) return '#388e3c';
    if (rating >= 50) return '#fbc02d';
    if (rating >= 25) return '#f57c00';
    return '#d32f2f';
  }

  private calculateColumnCount(): number {
    const elementWidth: number = 555;
    const containerWidth: number | undefined = document.getElementById('game-media')?.offsetWidth;
    const defaultColumnCount = 3;

    if (containerWidth === undefined) return defaultColumnCount
    if (containerWidth < elementWidth) return 1;

    return Math.floor(containerWidth / elementWidth);
  }

  hasPlatform(library: LibraryDto, platform: PlatformDto) {
    return library.platforms.some((libPlatform) => libPlatform.slug == platform.slug)
  }

}
