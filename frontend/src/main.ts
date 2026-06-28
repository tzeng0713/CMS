import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, Routes } from '@angular/router';
import { AppComponent } from './app/app.component';
import { RouteSinkComponent } from './app/route-sink.component';

const routes: Routes = [
  { path: '', redirectTo: 'home', pathMatch: 'full' },
  { path: 'home', component: RouteSinkComponent },
  { path: 'customers', component: RouteSinkComponent },
  { path: 'customers/new', component: RouteSinkComponent },
  { path: 'customers/:id', component: RouteSinkComponent },
  { path: 'contracts', component: RouteSinkComponent },
  { path: 'contracts/new', component: RouteSinkComponent },
  { path: 'rent-payments', component: RouteSinkComponent },
  { path: 'rent-payments/new', component: RouteSinkComponent },
  { path: 'staff', component: RouteSinkComponent },
  { path: 'charges', component: RouteSinkComponent },
  { path: 'refunds', component: RouteSinkComponent },
  { path: 'targets', component: RouteSinkComponent },
  { path: '**', redirectTo: 'home' }
];

bootstrapApplication(AppComponent, {
  providers: [provideHttpClient(), provideRouter(routes)]
}).catch((err) => console.error(err));
