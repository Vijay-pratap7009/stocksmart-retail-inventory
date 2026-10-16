import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page, Product, ProductRequest } from '../models/master-data.models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/products`;

  list(
    search?: string,
    active?: boolean | null,
    categoryId?: number | null,
    page = 0,
    size = 20
  ): Observable<Page<Product>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,asc');
    if (search?.trim()) params = params.set('search', search.trim());
    if (active !== undefined && active !== null) params = params.set('active', active);
    if (categoryId !== undefined && categoryId !== null) params = params.set('categoryId', categoryId);
    return this.http.get<Page<Product>>(this.base, { params });
  }

  get(id: number): Observable<Product> {
    return this.http.get<Product>(`${this.base}/${id}`);
  }

  getByBarcode(barcode: string): Observable<Product> {
    return this.http.get<Product>(`${this.base}/barcode/${encodeURIComponent(barcode.trim())}`);
  }

  getBySku(sku: string): Observable<Product> {
    return this.http.get<Product>(`${this.base}/sku/${encodeURIComponent(sku.trim())}`);
  }

  create(request: ProductRequest): Observable<Product> {
    return this.http.post<Product>(this.base, request);
  }

  update(id: number, request: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.base}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
