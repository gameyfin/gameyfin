import {CompanyDto} from "./CompanyDto";
import {GenreDto} from "./GenreDto";
import {KeywordDto} from "./KeywordDto";
import {PlayerPerspectiveDto} from "./PlayerPerspectiveDto";
import {ThemeDto} from "./ThemeDto";

export class DetectedGameDto {

  slug!: string;
  title!: string;
  summary?: string;
  releaseDate?: Date;
  userRating?: number;
  criticsRating?: number;
  totalRating?: number;
  category?: string;
  offlineCoop?: boolean;
  onlineCoop?: boolean;
  lanSupport?: boolean;
  maxPlayers?: boolean;
  coverId!: string;
  screenshotIds?: string[];
  videoIds?: string[];
  companies?: CompanyDto[];
  genres?: GenreDto[];
  keywords?: KeywordDto[];
  themes?: ThemeDto[];
  playerPerspectives?: PlayerPerspectiveDto[];

  path!: string;
  isFolder!: boolean;
  confirmedMatch!: boolean;
}
