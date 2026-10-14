import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../models/master-data.models';
import { StockTransaction, TransactionType } from '../models/inventory.models';
import { environment } from '../../../environments/environment';

export interface TransactionFilter {
  productId?: number | null;
  warehouseId?: number | null;
  type?: TransactionType | null;
  reference?: string | null;
  from?: string | null;
  to?: string | null;
}

@Injectable({ providedIn: 'root' })
export class StockTransactionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/stock-transactions`;

  search(filter: TransactionFilter = {}, page = 0, size = 20): Observable<Page<StockTransaction>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filter.productId) params = params.set('productId', filter.productId);
    if (filter.warehouseId) params = params.set('warehouseId', filter.warehouseId);
    if (filter.type) params = params.set('type', filter.type);
    if (filter.reference?.trim()) params = params.set('reference', filter.reference.trim());
    if (filter.from) params = params.set('from', filter.from);
    if (filter.to) params = params.set('to', filter.to);
    return this.http.get<Page<StockTransaction>>(this.base, { params });
  }

  get(id: number): Observable<StockTransaction> {
    return this.http.get<StockTransaction>(`${this.base}/${id}`);
  }
}
