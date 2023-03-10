import {NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';

import {AppComponent} from './app.component';
import {NavbarLayoutComponent} from "./layouts/navbar-layout/navbar-layout.component";
import {PageNotFoundComponent} from "./components/page-not-found/page-not-found.component";
import {HeaderComponent} from "./components/header/header.component";
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {MatLegacyFormFieldModule as MatFormFieldModule} from "@angular/material/legacy-form-field";
import {MatLegacyCardModule as MatCardModule} from "@angular/material/legacy-card";
import {MatLegacyTabsModule as MatTabsModule} from "@angular/material/legacy-tabs";
import {MatToolbarModule} from "@angular/material/toolbar";
import {MatLegacyMenuModule as MatMenuModule} from "@angular/material/legacy-menu";
import {MatIconModule} from "@angular/material/icon";
import {AppRoutingModule} from "./app-routing.module";
import {HTTP_INTERCEPTORS, HttpClientModule} from "@angular/common/http";
import {ErrorInterceptor} from "./interceptor/error.interceptor";
import {ApiUrlInterceptor} from "./interceptor/api-url.interceptor";
import {ErrorDialogComponent} from "./components/error-dialog/error-dialog.component";
import {MatLegacyDialogModule as MatDialogModule} from "@angular/material/legacy-dialog";
import {MatLegacyButtonModule as MatButtonModule} from "@angular/material/legacy-button";
import {MatLegacyInputModule as MatInputModule} from "@angular/material/legacy-input";
import {FlexLayoutModule, FlexModule, GridModule} from "@angular/flex-layout";
import {LibraryOverviewComponent} from './components/library-overview/library-overview.component';
import {MatLegacyProgressSpinnerModule as MatProgressSpinnerModule} from "@angular/material/legacy-progress-spinner";
import {MatLegacyTableModule as MatTableModule} from "@angular/material/legacy-table";
import {MatLegacyPaginatorModule as MatPaginatorModule} from "@angular/material/legacy-paginator";
import {MatSortModule} from "@angular/material/sort";
import {GameCoverComponent} from './components/game-cover/game-cover.component';
import {GameDetailViewComponent} from './components/game-detail-view/game-detail-view.component';
import {MAT_LEGACY_SNACK_BAR_DEFAULT_OPTIONS as MAT_SNACK_BAR_DEFAULT_OPTIONS, MatLegacySnackBarModule as MatSnackBarModule} from '@angular/material/legacy-snack-bar';
import {MatGridListModule} from "@angular/material/grid-list";
import {GameScreenshotComponent} from './components/game-screenshot/game-screenshot.component';
import {YouTubePlayerModule} from "@angular/youtube-player";
import {GameVideoComponent} from './components/game-video/game-video.component';
import {MatLegacyChipsModule as MatChipsModule} from "@angular/material/legacy-chips";
import { LibraryManagementComponent } from './components/library-management/library-management.component';
import {MatLegacyTooltipModule as MatTooltipModule} from "@angular/material/legacy-tooltip";
import {MapGameDialogComponent} from "./components/map-game-dialog/map-game-dialog.component";
import {MapLibraryDialogComponent} from "./components/map-library-dialog/map-library-dialog.component";
import {MatLegacySlideToggleModule as MatSlideToggleModule} from "@angular/material/legacy-slide-toggle";
import {MatLegacyCheckboxModule as MatCheckboxModule} from "@angular/material/legacy-checkbox";
import {A11yModule} from "@angular/cdk/a11y";
import { MappedGamesTableComponent } from './components/mapped-games-table/mapped-games-table.component';
import { MappedLibrariesTableComponent } from './components/mapped-libraries-table/mapped-libraries-table.component';
import {MatTableFilterModule} from "mat-table-filter";
import { UnmappedFilesTableComponent } from './components/unmapped-files-table/unmapped-files-table.component';
import {MatDividerModule} from "@angular/material/divider";
import {MatLegacyListModule as MatListModule} from "@angular/material/legacy-list";
import {MatLegacyAutocompleteModule as MatAutocompleteModule} from "@angular/material/legacy-autocomplete";
import { NgModelChangeDebouncedDirective } from './directives/ng-model-change-debounced.directive';
import { FooterComponent } from './components/footer/footer.component';
import {MatExpansionModule} from "@angular/material/expansion";
import {MatLegacySelectModule as MatSelectModule} from "@angular/material/legacy-select";
import {MatLegacyProgressBarModule as MatProgressBarModule} from "@angular/material/legacy-progress-bar";
import { ProgressBarColorDirective } from './directives/progress-bar-color.directive';

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
    MapGameDialogComponent,
    MapLibraryDialogComponent,
    MappedGamesTableComponent,
    MappedLibrariesTableComponent,
    UnmappedFilesTableComponent,
    NgModelChangeDebouncedDirective,
    ProgressBarColorDirective,
    FooterComponent
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
    MatTooltipModule,
    MatSlideToggleModule,
    MatCheckboxModule,
    A11yModule,
    MatTableFilterModule,
    MatDividerModule,
    MatListModule,
    MatAutocompleteModule,
    MatExpansionModule,
    MatSelectModule,
    MatProgressBarModule,
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
      useValue: { panelClass: ['formatted-snackbar'] },
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {
}
