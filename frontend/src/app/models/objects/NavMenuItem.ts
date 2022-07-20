import { Icon } from '../enums/Icon';

export class NavMenuItem {
  title: string;
  icon: Icon;
  route: string;
  enabled: boolean;

  public constructor(title: string, icon: Icon, route: string, enabled: boolean) {
    this.title = title;
    this.icon = icon;
    this.route = route;
    this.enabled = enabled;
  }
}
