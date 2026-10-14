import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../models/master-data.models';
import {
  PurchaseOrder,
  PurchaseOrderRequest,
  PurchaseOrderStatus,
  PurchaseReceipt,
} from '../models/order.models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class PurchaseOrderService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/purchase-orders`;

  list(
    search?: string,
    status?: PurchaseOrderStatus | null,
    supplierId?: number | null,
    page = 0,
    size = 20
  ): Observable<Page<PurchaseOrder>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,desc');
    if (search?.trim()) params = params.set('search', search.trim());
    if (status) params = params.set('status', status);
    if (supplierId) params = params.set('supplierId', supplierId);
    return this.http.get<Page<PurchaseOrder>>(this.base, { params });
  }

  get(id: number): Observable<PurchaseOrder> {
    return this.http.get<PurchaseOrder>(`${this.base}/${id}`);
  }

  receipts(id: number): Observable<PurchaseReceipt[]> {
    return this.http.get<PurchaseReceipt[]>(`${this.base}/${id}/receipts`);
  }

  create(request: PurchaseOrderRequest): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(this.base, request);
  }

  update(id: number, request: PurchaseOrderRequest): Observable<PurchaseOrder> {
    return this.http.put<PurchaseOrder>(`${this.base}/${id}`, request);
  }

  submit(id: number): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${this.base}/${id}/submit`, {});
  }

  receive(
    id: number,
    lines: { poItemId: number; quantity: number }[],
    reference?: string | null,
    notes?: string | null
  ): Observable<PurchaseReceipt> {
    return this.http.post<PurchaseReceipt>(`${this.base}/${id}/receipts`, {
      reference: reference?.trim() || null,
      notes: notes?.trim() || null,
      lines,
    });
  }

  cancel(id: number): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${this.base}/${id}/cancel`, {});
  }
}
