import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {PageNotFoundComponent} from "./components/page-not-found/page-not-found.component";
import {NavbarLayoutComponent} from "./layouts/navbar-layout/navbar-layout.component";
import {LibraryOverviewComponent} from "./components/library-overview/library-overview.component";
import {GameDetailViewComponent} from "./components/game-detail-view/game-detail-view.component";
import {LibraryManagementComponent} from "./components/library-management/library-management.component";
import {MappedGamesTableComponent} from "./components/mapped-games-table/mapped-games-table.component";

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
        path: 'library-management',
        component: LibraryManagementComponent
      },
      {
        path: 'test',
        component: MappedGamesTableComponent
      },
      {
        path: '',
        redirectTo: '/library',
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
  imports: [RouterModule.forRoot(appRoutes, { scrollPositionRestoration: 'enabled' })],
  exports: [RouterModule]
})

export class AppRoutingModule {
}
