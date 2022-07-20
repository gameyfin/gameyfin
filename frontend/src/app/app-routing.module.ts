import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {FullpageLayoutComponent} from "./layouts/fullpage-layout/fullpage-layout.component";
import {PageNotFoundComponent} from "./components/page-not-found/page-not-found.component";
import {NavbarLayoutComponent} from "./layouts/navbar-layout/navbar-layout.component";
import {NotImplementedComponent} from "./components/not-implemented/not-implemented.component";
import {GameserverListComponent} from "./components/gameserver-list/gameserver-list.component";

const appRoutes: Routes = [
  {
    path: '',
    component: NavbarLayoutComponent,
    children: [
      {
        path: 'library',
        component: GameserverListComponent
      },
      {
        path: 'games',
        component: NotImplementedComponent
      },
      {
        path: 'info',
        component: NotImplementedComponent
      },
      {
        path: 'config',
        component: NotImplementedComponent
      },
      {
        path: '',
        redirectTo: '/library',
        pathMatch: 'full'
      }
    ]
  },
  {
    path: '',
    component: FullpageLayoutComponent,
    children: [
      {
        path: '',
        redirectTo: '/login',
        pathMatch: 'full'
      }
    ]
  },
  {
    path: '**',
    component: PageNotFoundComponent
  }
];

@NgModule({
  imports: [RouterModule.forRoot(appRoutes)],
  exports: [RouterModule]
})

export class AppRoutingModule {
}
