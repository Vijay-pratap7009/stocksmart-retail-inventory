import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page, Supplier, SupplierRequest } from '../models/master-data.models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class SupplierService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/suppliers`;

  list(search?: string, active?: boolean | null, page = 0, size = 20): Observable<Page<Supplier>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,asc');
    if (search?.trim()) params = params.set('search', search.trim());
    if (active !== undefined && active !== null) params = params.set('active', active);
    return this.http.get<Page<Supplier>>(this.base, { params });
  }

  get(id: number): Observable<Supplier> {
    return this.http.get<Supplier>(`${this.base}/${id}`);
  }

  create(request: SupplierRequest): Observable<Supplier> {
    return this.http.post<Supplier>(this.base, request);
  }

  update(id: number, request: SupplierRequest): Observable<Supplier> {
    return this.http.put<Supplier>(`${this.base}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
