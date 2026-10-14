import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { LoginComponent } from './pages/login/login.component';
import { HomeComponent } from './pages/home/home.component';
import { CategoryListComponent } from './pages/categories/category-list.component';
import { ProductListComponent } from './pages/products/product-list.component';
import { WarehouseListComponent } from './pages/warehouses/warehouse-list.component';
import { SupplierListComponent } from './pages/suppliers/supplier-list.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'home', component: HomeComponent, canActivate: [authGuard] },
  { path: 'categories', component: CategoryListComponent, canActivate: [authGuard] },
  { path: 'products', component: ProductListComponent, canActivate: [authGuard] },
  { path: 'warehouses', component: WarehouseListComponent, canActivate: [authGuard] },
  { path: 'suppliers', component: SupplierListComponent, canActivate: [authGuard] },
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: '**', redirectTo: '/home' },
];
