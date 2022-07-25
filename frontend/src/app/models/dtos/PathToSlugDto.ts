import {FormControl} from "@angular/forms";

export class PathToSlugDto {
  slug: string;
  path: string;

  constructor(slug: string, path: string) {
    this.slug = slug;
    this.path = path;
  }
}
