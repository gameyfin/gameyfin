import {Component, OnInit} from '@angular/core';

@Component({
  selector: 'app-navbar-layout',
  template: `
    <div fxFlexFill>
      <app-header></app-header>
      <div fxLayout="column" fxLayoutAlign="space-around stretch">
        <div fxFlex>
          <router-outlet class="hidden-router"></router-outlet>
        </div>
      </div>
    </div>
  `,
  styles: []
})
export class NavbarLayoutComponent implements OnInit {

  constructor() {
  }

  ngOnInit() {
  }

}
