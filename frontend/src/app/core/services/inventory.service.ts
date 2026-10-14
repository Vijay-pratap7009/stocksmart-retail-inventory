import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../models/master-data.models';
import {
  AdjustmentRequest,
  Inventory,
  ReceiptRequest,
  TransferRequest,
  TransferResult,
} from '../models/inventory.models';
import { StockTransaction } from '../models/inventory.models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class InventoryService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/inventory`;

  list(
    search?: string,
    productId?: number | null,
    warehouseId?: number | null,
    page = 0,
    size = 20
  ): Observable<Page<Inventory>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,asc');
    if (search?.trim()) params = params.set('search', search.trim());
    if (productId !== undefined && productId !== null) params = params.set('productId', productId);
    if (warehouseId !== undefined && warehouseId !== null)
      params = params.set('warehouseId', warehouseId);
    return this.http.get<Page<Inventory>>(this.base, { params });
  }

  byProduct(productId: number): Observable<Inventory[]> {
    return this.http.get<Inventory[]>(`${this.base}/product/${productId}`);
  }

  byWarehouse(warehouseId: number): Observable<Inventory[]> {
    return this.http.get<Inventory[]>(`${this.base}/warehouse/${warehouseId}`);
  }

  receipt(request: ReceiptRequest): Observable<StockTransaction> {
    return this.http.post<StockTransaction>(`${this.base}/receipts`, request);
  }

  adjust(request: AdjustmentRequest): Observable<StockTransaction> {
    return this.http.post<StockTransaction>(`${this.base}/adjustments`, request);
  }

  transfer(request: TransferRequest): Observable<TransferResult> {
    return this.http.post<TransferResult>(`${this.base}/transfers`, request);
  }
}
