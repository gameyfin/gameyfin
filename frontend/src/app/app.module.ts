import {NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';

import {AppComponent} from './app.component';
import {NavbarLayoutComponent} from "./layouts/navbar-layout/navbar-layout.component";
import {PageNotFoundComponent} from "./components/page-not-found/page-not-found.component";
import {HeaderComponent} from "./components/header/header.component";
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {MatFormFieldModule} from "@angular/material/form-field";
import {MatCardModule} from "@angular/material/card";
import {MatTabsModule} from "@angular/material/tabs";
import {MatToolbarModule} from "@angular/material/toolbar";
import {MatMenuModule} from "@angular/material/menu";
import {MatIconModule} from "@angular/material/icon";
import {AppRoutingModule} from "./app-routing.module";
import {HTTP_INTERCEPTORS, HttpClientModule} from "@angular/common/http";
import {ErrorInterceptor} from "./interceptor/error.interceptor";
import {ApiUrlInterceptor} from "./interceptor/api-url.interceptor";
import {ErrorDialogComponent} from "./components/error-dialog/error-dialog.component";
import {MatDialogModule} from "@angular/material/dialog";
import {MatButtonModule} from "@angular/material/button";
import {MatInputModule} from "@angular/material/input";
import {FlexLayoutModule, FlexModule, GridModule} from "@angular/flex-layout";
import {LibraryOverviewComponent} from './components/library-overview/library-overview.component';
import {MatProgressSpinnerModule} from "@angular/material/progress-spinner";
import {MatTableModule} from "@angular/material/table";
import {MatPaginatorModule} from "@angular/material/paginator";
import {MatSortModule} from "@angular/material/sort";
import {GameCoverComponent} from './components/game-cover/game-cover.component';
import {GameDetailViewComponent} from './components/game-detail-view/game-detail-view.component';
import {MAT_SNACK_BAR_DEFAULT_OPTIONS, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatGridListModule} from "@angular/material/grid-list";
import {GameScreenshotComponent} from './components/game-screenshot/game-screenshot.component';
import {YouTubePlayerModule} from "@angular/youtube-player";
import {GameVideoComponent} from './components/game-video/game-video.component';
import {MatChipsModule} from "@angular/material/chips";
import { LibraryManagementComponent } from './components/library-management/library-management.component';
import {MatTooltipModule} from "@angular/material/tooltip";
import {MapGameDialogComponent} from "./components/map-game-dialog/map-game-dialog.component";

@NgModule({
  declarations: [
    AppComponent,
    HeaderComponent,
    NavbarLayoutComponent,
    PageNotFoundComponent,
    ErrorDialogComponent,
    LibraryOverviewComponent,
    GameCoverComponent,
    GameDetailViewComponent,
    GameScreenshotComponent,
    GameVideoComponent,
    LibraryManagementComponent,
    MapGameDialogComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    BrowserAnimationsModule,
    FormsModule,
    MatFormFieldModule,
    MatCardModule,
    MatTabsModule,
    MatToolbarModule,
    MatMenuModule,
    MatIconModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatInputModule,
    FlexModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatSnackBarModule,
    MatGridListModule,
    FlexLayoutModule,
    GridModule,
    YouTubePlayerModule,
    MatChipsModule,
    MatTooltipModule
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: ApiUrlInterceptor,
      multi: true
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: ErrorInterceptor,
      multi: true
    },
    {
      provide: MAT_SNACK_BAR_DEFAULT_OPTIONS,
      useValue: { panelClass: ['snackbar-dark'] },
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {
}
