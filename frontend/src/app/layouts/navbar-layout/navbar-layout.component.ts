import {Component, OnInit} from '@angular/core';

@Component({
  selector: 'app-navbar-layout',
  template: `
    <div class="main-container" fxLayout="column">
      <div fxFlex="none" style="position: sticky; top: 0; z-index: 999">
        <app-header></app-header>
      </div>
      <div fxFlex>
        <router-outlet></router-outlet><!-- class="hidden-router" -->
      </div>
      <div fxLayout="row" fxLayoutAlign="center center">
        <app-footer></app-footer>
      </div>
    </div>
  `,
  styles: [`
    .main-container {
      min-height: 100vh;
    }
  `]
})
export class NavbarLayoutComponent implements OnInit {

  constructor() {
  }

  ngOnInit() {
  }

}
