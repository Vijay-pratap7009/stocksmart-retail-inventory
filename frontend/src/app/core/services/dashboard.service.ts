import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../models/master-data.models';
import { StockTransaction } from '../models/inventory.models';
import {
  DashboardSummary,
  LowStockItem,
  PurchaseOrderSummary,
  SalesFulfillmentSummary,
  StockoutItem,
} from '../models/dashboard.models';
import { environment } from '../../../environments/environment';

/** Day 5 inventory KPI dashboard API client (real persisted data only). */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/dashboard`;

  summary(warehouseId: number | null = null): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.base}/summary`, {
      params: this.warehouseParams(warehouseId),
    });
  }

  lowStock(warehouseId: number | null = null, page = 0, size = 50): Observable<Page<LowStockItem>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,asc');
    if (warehouseId !== null) params = params.set('warehouseId', warehouseId);
    return this.http.get<Page<LowStockItem>>(`${this.base}/low-stock`, { params });
  }

  stockouts(warehouseId: number | null = null, page = 0, size = 50): Observable<Page<StockoutItem>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,asc');
    if (warehouseId !== null) params = params.set('warehouseId', warehouseId);
    return this.http.get<Page<StockoutItem>>(`${this.base}/stockouts`, { params });
  }

  purchaseOrdersSummary(warehouseId: number | null = null): Observable<PurchaseOrderSummary> {
    return this.http.get<PurchaseOrderSummary>(`${this.base}/purchase-orders`, {
      params: this.warehouseParams(warehouseId),
    });
  }

  salesFulfillmentSummary(warehouseId: number | null = null): Observable<SalesFulfillmentSummary> {
    return this.http.get<SalesFulfillmentSummary>(`${this.base}/sales-fulfillment`, {
      params: this.warehouseParams(warehouseId),
    });
  }

  recentTransactions(
    warehouseId: number | null = null,
    limit = 10
  ): Observable<StockTransaction[]> {
    let params = new HttpParams().set('limit', limit);
    if (warehouseId !== null) params = params.set('warehouseId', warehouseId);
    return this.http.get<StockTransaction[]>(`${this.base}/recent-transactions`, { params });
  }

  private warehouseParams(warehouseId: number | null): HttpParams {
    let params = new HttpParams();
    if (warehouseId !== null) params = params.set('warehouseId', warehouseId);
    return params;
  }
}
