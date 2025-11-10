import { Routes } from '@angular/router';
import { SearchWidgetComponent } from './components/search-widget/search-widget';

export const routes: Routes = [
  { path: ':partnerId', component: SearchWidgetComponent },
  { path: '', redirectTo: '8d3a83ff-5a9f-4d57-8671-9397c1b02a25', pathMatch: 'full' }
];
