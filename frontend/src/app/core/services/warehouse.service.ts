import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page, Warehouse, WarehouseRequest } from '../models/master-data.models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class WarehouseService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/warehouses`;

  list(search?: string, active?: boolean | null, page = 0, size = 20): Observable<Page<Warehouse>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,asc');
    if (search?.trim()) params = params.set('search', search.trim());
    if (active !== undefined && active !== null) params = params.set('active', active);
    return this.http.get<Page<Warehouse>>(this.base, { params });
  }

  get(id: number): Observable<Warehouse> {
    return this.http.get<Warehouse>(`${this.base}/${id}`);
  }

  create(request: WarehouseRequest): Observable<Warehouse> {
    return this.http.post<Warehouse>(this.base, request);
  }

  update(id: number, request: WarehouseRequest): Observable<Warehouse> {
    return this.http.put<Warehouse>(`${this.base}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
