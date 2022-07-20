import { Icon } from '../enums/Icon';

export class DropDownMenuItem {
  title: string;
  icon: Icon;
  action: () => void;
  enabled: boolean;

  public constructor(title: string, icon: Icon, action: () => void, enabled: boolean) {
    this.title = title;
    this.icon = icon;
    this.action = action;
    this.enabled = enabled;
  }
}
