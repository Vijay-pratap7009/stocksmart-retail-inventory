import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Page } from '../../core/models/master-data.models';
import { PurchaseOrder, PurchaseOrderStatus } from '../../core/models/order.models';
import { PurchaseOrderService } from '../../core/services/purchase-order.service';
import { AuthService } from '../../core/services/auth.service';

const STATUSES: PurchaseOrderStatus[] = ['DRAFT', 'SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'];

@Component({
  selector: 'app-purchase-order-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/home" class="back">← Home</a>
          <h1>Purchase orders</h1>
        </div>
        <button *ngIf="canManage" routerLink="/purchase-orders/new">+ New order</button>
      </header>

      <section class="card filters">
        <input type="text" placeholder="Search PO number…" [value]="search"
          (input)="onSearch($any($event.target).value)" />
        <select [value]="statusFilter" (change)="onStatusFilter($any($event.target).value)">
          <option value="">All statuses</option>
          <option *ngFor="let s of statuses" [value]="s">{{ s }}</option>
        </select>
      </section>

      <p class="notice error" *ngIf="error">{{ error }}</p>

      <section class="card">
        <p *ngIf="loading" class="muted">Loading…</p>
        <p *ngIf="!loading && items.length === 0" class="muted">No purchase orders found.</p>
        <table *ngIf="!loading && items.length > 0">
          <thead><tr><th>PO number</th><th>Supplier</th><th>Warehouse</th><th>Total</th><th>Status</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let o of items">
              <td><code>{{ o.poNumber }}</code></td>
              <td>{{ o.supplierName }}</td>
              <td>{{ o.warehouseCode }}</td>
              <td>{{ o.orderTotal }}</td>
              <td><span class="badge">{{ o.status }}</span></td>
              <td><a [routerLink]="['/purchase-orders', o.id]">Open</a></td>
            </tr>
          </tbody>
        </table>
        <div class="pager" *ngIf="totalPages > 1">
          <button class="ghost" [disabled]="page === 0" (click)="onPage(page - 1)">‹ Prev</button>
          <span class="muted">Page {{ page + 1 }} of {{ totalPages }} ({{ total }})</span>
          <button class="ghost" [disabled]="page + 1 >= totalPages" (click)="onPage(page + 1)">Next ›</button>
        </div>
      </section>
    </main>
  `,
  styles: [`
    .page { min-height: 100vh; background: #0f172a; color: #e2e8f0; padding: 2rem; box-sizing: border-box; }
    header { display: flex; justify-content: space-between; align-items: flex-end; max-width: 1000px; margin: 0 auto 1.5rem; }
    .back { color: #93c5fd; text-decoration: none; font-size: .85rem; }
    h1 { margin: .25rem 0 0; }
    .card { max-width: 1000px; margin: 0 auto 1rem; background: #1e293b; border-radius: 12px; padding: 1.25rem 1.5rem; }
    .filters { display: flex; gap: .75rem; }
    .filters input, .filters select { flex: 1; padding: .55rem .7rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; }
    button { padding: .55rem 1.1rem; border-radius: 8px; border: 0; background: #2563eb; color: #fff; font-weight: 600; cursor: pointer; }
    button.ghost { background: #0f172a; border: 1px solid #334155; }
    button.ghost:disabled { opacity: .5; cursor: default; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: .55rem .5rem; border-bottom: 1px solid #334155; font-size: .9rem; }
    td a { color: #93c5fd; }
    .badge { background: #0f172a; border: 1px solid #334155; border-radius: 6px; padding: .15rem .5rem; font-size: .8rem; }
    .muted { color: #94a3b8; }
    .notice { max-width: 1000px; margin: 0 auto 1rem; padding: .6rem 1rem; border-radius: 8px; }
    .error { background: #451a1a; color: #fca5a5; }
    .pager { display: flex; gap: 1rem; align-items: center; margin-top: 1rem; }
  `],
})
export class PurchaseOrderListComponent implements OnInit {
  private readonly service = inject(PurchaseOrderService);
  private readonly auth = inject(AuthService);

  items: PurchaseOrder[] = [];
  statuses = STATUSES;
  total = 0;
  totalPages = 0;
  page = 0;
  loading = false;
  error: string | null = null;
  search = '';
  statusFilter = '';
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  get canManage(): boolean {
    return this.auth.role === 'ADMIN' || this.auth.role === 'PROCUREMENT_USER';
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.service.list(
      this.search,
      this.statusFilter === '' ? null : (this.statusFilter as PurchaseOrderStatus),
      null,
      this.page
    ).subscribe({
      next: (p: Page<PurchaseOrder>) => {
        this.loading = false;
        this.items = p.content;
        this.total = p.totalElements;
        this.totalPages = p.totalPages;
      },
      error: (err) => {
        this.loading = false;
        this.error = (err as { error?: { message?: string } })?.error?.message
          ?? 'Failed to load purchase orders.';
      },
    });
  }

  onSearch(value: string): void {
    this.search = value;
    this.page = 0;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(), 350);
  }

  onStatusFilter(value: string): void {
    this.statusFilter = value;
    this.page = 0;
    this.load();
  }

  onPage(page: number): void {
    this.page = page;
    this.load();
  }
}
