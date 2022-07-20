import {Component, OnInit} from '@angular/core';

@Component({
  selector: 'app-fullpage-layout',
  template: `
    <div fxLayout="column" fxFlexFill>
      <div fxFlex>
        <router-outlet class="hidden-router"></router-outlet>
      </div>
    </div>
  `,
  styles: []
})
export class FullpageLayoutComponent implements OnInit {

  constructor() {
  }

  ngOnInit() {
  }

}
