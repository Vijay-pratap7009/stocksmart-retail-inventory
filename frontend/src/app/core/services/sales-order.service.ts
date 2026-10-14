import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../models/master-data.models';
import {
  SalesFulfillment,
  SalesOrder,
  SalesOrderRequest,
  SalesOrderStatus,
} from '../models/order.models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class SalesOrderService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/sales-orders`;

  list(
    search?: string,
    status?: SalesOrderStatus | null,
    warehouseId?: number | null,
    page = 0,
    size = 20
  ): Observable<Page<SalesOrder>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,desc');
    if (search?.trim()) params = params.set('search', search.trim());
    if (status) params = params.set('status', status);
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    return this.http.get<Page<SalesOrder>>(this.base, { params });
  }

  get(id: number): Observable<SalesOrder> {
    return this.http.get<SalesOrder>(`${this.base}/${id}`);
  }

  fulfillments(id: number): Observable<SalesFulfillment[]> {
    return this.http.get<SalesFulfillment[]>(`${this.base}/${id}/fulfillments`);
  }

  create(request: SalesOrderRequest): Observable<SalesOrder> {
    return this.http.post<SalesOrder>(this.base, request);
  }

  update(id: number, request: SalesOrderRequest): Observable<SalesOrder> {
    return this.http.put<SalesOrder>(`${this.base}/${id}`, request);
  }

  confirm(id: number): Observable<SalesOrder> {
    return this.http.post<SalesOrder>(`${this.base}/${id}/confirm`, {});
  }

  fulfill(
    id: number,
    lines: { soItemId: number; quantity: number }[],
    reference?: string | null,
    notes?: string | null
  ): Observable<SalesFulfillment> {
    return this.http.post<SalesFulfillment>(`${this.base}/${id}/fulfillments`, {
      reference: reference?.trim() || null,
      notes: notes?.trim() || null,
      lines,
    });
  }

  cancel(id: number): Observable<SalesOrder> {
    return this.http.post<SalesOrder>(`${this.base}/${id}/cancel`, {});
  }
}
