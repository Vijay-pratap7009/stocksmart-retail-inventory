import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page, Product, Warehouse } from '../../core/models/master-data.models';
import { StockTransaction, TransactionType } from '../../core/models/inventory.models';
import { StockTransactionService } from '../../core/services/stock-transaction.service';
import { ProductService } from '../../core/services/product.service';
import { WarehouseService } from '../../core/services/warehouse.service';

const TYPES: TransactionType[] = [
  'OPENING_BALANCE',
  'RECEIPT',
  'ISSUE',
  'ADJUSTMENT_IN',
  'ADJUSTMENT_OUT',
  'TRANSFER_IN',
  'TRANSFER_OUT',
];

@Component({
  selector: 'app-transaction-history',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/inventory" class="back">← Inventory</a>
          <h1>Stock transaction history</h1>
        </div>
      </header>

      <section class="card filters">
        <select [(ngModel)]="typeFilter" (change)="load()">
          <option value="">All types</option>
          <option *ngFor="let t of types" [value]="t">{{ t }}</option>
        </select>
        <select [(ngModel)]="productFilter" (change)="load()">
          <option value="">All products</option>
          <option *ngFor="let p of products" [value]="p.id">{{ p.sku }}</option>
        </select>
        <select [(ngModel)]="warehouseFilter" (change)="load()">
          <option value="">All warehouses</option>
          <option *ngFor="let w of warehouses" [value]="w.id">{{ w.code }}</option>
        </select>
        <input type="text" placeholder="Reference…" [(ngModel)]="referenceFilter"
          (keyup.enter)="load()" />
        <button class="ghost" (click)="load()">Apply</button>
      </section>

      <p class="notice error" *ngIf="error">{{ error }}</p>

      <section class="card">
        <p *ngIf="loading" class="muted">Loading…</p>
        <p *ngIf="!loading && items.length === 0" class="muted">No movements recorded yet.</p>
        <table *ngIf="!loading && items.length > 0">
          <thead><tr><th>Reference</th><th>Type</th><th>SKU</th><th>Warehouse</th><th>Qty</th><th>Before → After</th><th>By</th><th>At</th></tr></thead>
          <tbody>
            <tr *ngFor="let t of items" [title]="t.reason || t.notes || ''">
              <td><code>{{ t.referenceNumber }}</code></td>
              <td>{{ t.type }}</td>
              <td>{{ t.sku }}</td>
              <td>{{ t.warehouseCode }}</td>
              <td>{{ t.quantity }}</td>
              <td>{{ t.quantityBefore }} → {{ t.quantityAfter }}</td>
              <td>{{ t.performedByUsername }}</td>
              <td>{{ t.createdAt || '—' }}</td>
            </tr>
          </tbody>
        </table>
        <div class="pager" *ngIf="totalPages > 1">
          <button class="ghost" [disabled]="page === 0" (click)="onPage(page - 1)">‹ Prev</button>
          <span class="muted">Page {{ page + 1 }} of {{ totalPages }} ({{ total }} records)</span>
          <button class="ghost" [disabled]="page + 1 >= totalPages" (click)="onPage(page + 1)">Next ›</button>
        </div>
        <p class="muted" *ngIf="totalPages <= 1 && total > 0">Total: {{ total }}</p>
      </section>
    </main>
  `,
  styles: [`
    .page { min-height: 100vh; background: #0f172a; color: #e2e8f0; padding: 2rem; box-sizing: border-box; }
    header { max-width: 1100px; margin: 0 auto 1.5rem; }
    .back { color: #93c5fd; text-decoration: none; font-size: .85rem; }
    h1 { margin: .25rem 0 0; }
    .card { max-width: 1100px; margin: 0 auto 1rem; background: #1e293b; border-radius: 12px; padding: 1.25rem 1.5rem; }
    .filters { display: flex; gap: .75rem; flex-wrap: wrap; }
    .filters input, .filters select { flex: 1; min-width: 150px; padding: .55rem .7rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: .55rem .5rem; border-bottom: 1px solid #334155; font-size: .85rem; }
    .muted { color: #94a3b8; }
    .notice { max-width: 1100px; margin: 0 auto 1rem; padding: .6rem 1rem; border-radius: 8px; }
    .error { background: #451a1a; color: #fca5a5; }
    .pager { display: flex; gap: 1rem; align-items: center; margin-top: 1rem; }
    button.ghost { padding: .45rem .9rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; cursor: pointer; }
    button.ghost:disabled { opacity: .5; cursor: default; }
  `],
})
export class TransactionHistoryComponent implements OnInit {
  private readonly service = inject(StockTransactionService);
  private readonly productsApi = inject(ProductService);
  private readonly warehousesApi = inject(WarehouseService);

  items: StockTransaction[] = [];
  products: Product[] = [];
  warehouses: Warehouse[] = [];
  types = TYPES;
  total = 0;
  totalPages = 0;
  page = 0;
  loading = false;
  error: string | null = null;

  typeFilter = '';
  productFilter = '';
  warehouseFilter = '';
  referenceFilter = '';

  ngOnInit(): void {
    this.productsApi.list('', null, null, 0, 200).subscribe({
      next: (p: Page<Product>) => {
        this.products = p.content;
      },
      error: () => undefined,
    });
    this.warehousesApi.list('', null, 0, 200).subscribe({
      next: (p: Page<Warehouse>) => {
        this.warehouses = p.content;
      },
      error: () => undefined,
    });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.service.search({
      productId: this.productFilter === '' ? null : Number(this.productFilter),
      warehouseId: this.warehouseFilter === '' ? null : Number(this.warehouseFilter),
      type: this.typeFilter === '' ? null : (this.typeFilter as TransactionType),
      reference: this.referenceFilter || null,
    }, this.page).subscribe({
      next: (page) => {
        this.loading = false;
        this.items = page.content;
        this.total = page.totalElements;
        this.totalPages = page.totalPages;
      },
      error: (err) => {
        this.loading = false;
        this.error = (err as { error?: { message?: string } })?.error?.message
          ?? 'Failed to load history.';
      },
    });
  }

  onPage(page: number): void {
    this.page = page;
    this.load();
  }
}
