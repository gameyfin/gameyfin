import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {FullpageLayoutComponent} from "./layouts/fullpage-layout/fullpage-layout.component";
import {PageNotFoundComponent} from "./components/page-not-found/page-not-found.component";
import {NavbarLayoutComponent} from "./layouts/navbar-layout/navbar-layout.component";
import {NotImplementedComponent} from "./components/not-implemented/not-implemented.component";
import {LibraryOverviewComponent} from "./components/library-overview/library-overview.component";
import {GameDetailViewComponent} from "./components/game-detail-view/game-detail-view.component";

const appRoutes: Routes = [
  {
    path: '',
    component: NavbarLayoutComponent,
    children: [
      {
        path: 'library',
        component: LibraryOverviewComponent
      },
      {
        path: 'game/:slug',
        component: GameDetailViewComponent
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
