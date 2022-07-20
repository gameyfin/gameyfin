import {Component, OnInit} from '@angular/core';
import {NavMenuItem} from '../../models/objects/NavMenuItem';
import {Title} from '@angular/platform-browser';
import {Config} from '../../config/Config';
import {Icon} from '../../models/enums/Icon';
import {DropDownMenuItem} from "../../models/objects/DropDownMenuItem";

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit {

  tabNavItems: NavMenuItem[] = [
    new NavMenuItem('Servers', Icon.dns, '/servers', true),
    new NavMenuItem('Games', Icon.controller, '/games', true),
    new NavMenuItem('Info', Icon.info, '/info', true),
    new NavMenuItem('Config', Icon.settings, '/config', true),
  ];

  dropDownItems: DropDownMenuItem[] = [
    new DropDownMenuItem('Log out', Icon.lock_open, () => {
      alert("Logout not implemented");
    }, true)
  ];

  activeItem: NavMenuItem | undefined;

  constructor(private titleService: Title) {
  }

  ngOnInit() {
  }

  setActiveItem(item: NavMenuItem) {
    this.activeItem = item;
    this.titleService.setTitle(`${Config.baseTitle} - ${item.title}`);
  }
}
