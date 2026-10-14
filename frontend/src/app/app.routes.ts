import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { LoginComponent } from './pages/login/login.component';
import { HomeComponent } from './pages/home/home.component';
import { CategoryListComponent } from './pages/categories/category-list.component';
import { ProductListComponent } from './pages/products/product-list.component';
import { WarehouseListComponent } from './pages/warehouses/warehouse-list.component';
import { SupplierListComponent } from './pages/suppliers/supplier-list.component';
import { InventoryListComponent } from './pages/inventory/inventory-list.component';
import { StockOperationsComponent } from './pages/inventory/stock-operations.component';
import { TransferFormComponent } from './pages/inventory/transfer-form.component';
import { TransactionHistoryComponent } from './pages/inventory/transaction-history.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'home', component: HomeComponent, canActivate: [authGuard] },
  { path: 'categories', component: CategoryListComponent, canActivate: [authGuard] },
  { path: 'products', component: ProductListComponent, canActivate: [authGuard] },
  { path: 'warehouses', component: WarehouseListComponent, canActivate: [authGuard] },
  { path: 'suppliers', component: SupplierListComponent, canActivate: [authGuard] },
  { path: 'inventory', component: InventoryListComponent, canActivate: [authGuard] },
  { path: 'inventory/operations', component: StockOperationsComponent, canActivate: [authGuard] },
  { path: 'inventory/transfers', component: TransferFormComponent, canActivate: [authGuard] },
  { path: 'inventory/history', component: TransactionHistoryComponent, canActivate: [authGuard] },
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: '**', redirectTo: '/home' },
];
