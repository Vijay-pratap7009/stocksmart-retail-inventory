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
import { PurchaseOrderListComponent } from './pages/purchase-orders/purchase-order-list.component';
import { PurchaseOrderDetailComponent } from './pages/purchase-orders/purchase-order-detail.component';
import { SalesOrderListComponent } from './pages/sales-orders/sales-order-list.component';
import { SalesOrderDetailComponent } from './pages/sales-orders/sales-order-detail.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { BarcodeScanComponent } from './pages/barcode/barcode-scan.component';
import { RfidConsoleComponent } from './pages/rfid/rfid-console.component';

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
  { path: 'purchase-orders', component: PurchaseOrderListComponent, canActivate: [authGuard] },
  { path: 'purchase-orders/new', component: PurchaseOrderDetailComponent, canActivate: [authGuard] },
  { path: 'purchase-orders/:id', component: PurchaseOrderDetailComponent, canActivate: [authGuard] },
  { path: 'sales-orders', component: SalesOrderListComponent, canActivate: [authGuard] },
  { path: 'sales-orders/new', component: SalesOrderDetailComponent, canActivate: [authGuard] },
  { path: 'sales-orders/:id', component: SalesOrderDetailComponent, canActivate: [authGuard] },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'scan', component: BarcodeScanComponent, canActivate: [authGuard] },
  { path: 'rfid', component: RfidConsoleComponent, canActivate: [authGuard] },
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: '**', redirectTo: '/home' },
];
