import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Page, Product, Warehouse } from '../../core/models/master-data.models';
import { Inventory } from '../../core/models/inventory.models';
import { InventoryService } from '../../core/services/inventory.service';
import { ProductService } from '../../core/services/product.service';
import { WarehouseService } from '../../core/services/warehouse.service';

@Component({
  selector: 'app-inventory-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/home" class="back">← Home</a>
          <h1>Inventory</h1>
        </div>
        <nav class="actions">
          <a routerLink="/inventory/operations">Receipts &amp; adjustments</a>
          <a routerLink="/inventory/transfers">Transfers</a>
          <a routerLink="/inventory/history">History</a>
        </nav>
      </header>

      <section class="card filters">
        <input type="text" placeholder="Search SKU or product…" [value]="search"
          (input)="onSearch($any($event.target).value)" />
        <select [value]="productFilter" (change)="onProductFilter($any($event.target).value)">
          <option value="">All products</option>
          <option *ngFor="let p of products" [value]="p.id">{{ p.sku }} — {{ p.name }}</option>
        </select>
        <select [value]="warehouseFilter" (change)="onWarehouseFilter($any($event.target).value)">
          <option value="">All warehouses</option>
          <option *ngFor="let w of warehouses" [value]="w.id">{{ w.code }} — {{ w.name }}</option>
        </select>
      </section>

      <p class="notice error" *ngIf="error">{{ error }}</p>

      <section class="card">
        <p *ngIf="loading" class="muted">Loading…</p>
        <p *ngIf="!loading && items.length === 0" class="muted">No inventory records found. Receive stock to create the first balance.</p>
        <table *ngIf="!loading && items.length > 0">
          <thead><tr><th>SKU</th><th>Product</th><th>Warehouse</th><th>On hand</th><th>Updated</th></tr></thead>
          <tbody>
            <tr *ngFor="let i of items">
              <td><code>{{ i.sku }}</code></td>
              <td>{{ i.productName }}</td>
              <td>{{ i.warehouseCode }} — {{ i.warehouseName }}</td>
              <td>{{ i.quantityOnHand }} {{ i.unit }}</td>
              <td>{{ i.updatedAt || i.createdAt || '—' }}</td>
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
    header { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 1rem; max-width: 1000px; margin: 0 auto 1.5rem; }
    .back { color: #93c5fd; text-decoration: none; font-size: .85rem; }
    h1 { margin: .25rem 0 0; }
    .actions { display: flex; gap: .6rem; flex-wrap: wrap; }
    .actions a { padding: .5rem .9rem; border-radius: 8px; background: #2563eb; color: #fff; text-decoration: none; font-size: .85rem; font-weight: 600; }
    .card { max-width: 1000px; margin: 0 auto 1rem; background: #1e293b; border-radius: 12px; padding: 1.25rem 1.5rem; }
    .filters { display: flex; gap: .75rem; }
    .filters input, .filters select { flex: 1; padding: .55rem .7rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: .55rem .5rem; border-bottom: 1px solid #334155; font-size: .9rem; }
    .muted { color: #94a3b8; }
    .notice { max-width: 1000px; margin: 0 auto 1rem; padding: .6rem 1rem; border-radius: 8px; }
    .error { background: #451a1a; color: #fca5a5; }
    .pager { display: flex; gap: 1rem; align-items: center; margin-top: 1rem; }
    button.ghost { padding: .45rem .9rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; cursor: pointer; }
    button.ghost:disabled { opacity: .5; cursor: default; }
  `],
})
export class InventoryListComponent implements OnInit {
  private readonly service = inject(InventoryService);
  private readonly productsApi = inject(ProductService);
  private readonly warehousesApi = inject(WarehouseService);

  items: Inventory[] = [];
  products: Product[] = [];
  warehouses: Warehouse[] = [];
  total = 0;
  totalPages = 0;
  page = 0;
  loading = false;
  error: string | null = null;
  search = '';
  productFilter = '';
  warehouseFilter = '';
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

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
    const productId = this.productFilter === '' ? null : Number(this.productFilter);
    const warehouseId = this.warehouseFilter === '' ? null : Number(this.warehouseFilter);
    this.service.list(this.search, productId, warehouseId, this.page).subscribe({
      next: (page) => {
        this.loading = false;
        this.items = page.content;
        this.total = page.totalElements;
        this.totalPages = page.totalPages;
      },
      error: (err) => {
        this.loading = false;
        this.error = (err as { error?: { message?: string } })?.error?.message
          ?? 'Failed to load inventory.';
      },
    });
  }

  onSearch(value: string): void {
    this.search = value;
    this.page = 0;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(), 350);
  }

  onProductFilter(value: string): void {
    this.productFilter = value;
    this.page = 0;
    this.load();
  }

  onWarehouseFilter(value: string): void {
    this.warehouseFilter = value;
    this.page = 0;
    this.load();
  }

  onPage(page: number): void {
    this.page = page;
    this.load();
  }
}
