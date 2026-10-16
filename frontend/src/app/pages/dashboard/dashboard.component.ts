import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { Warehouse } from '../../core/models/master-data.models';
import { StockTransaction } from '../../core/models/inventory.models';
import {
  DashboardSummary,
  LowStockItem,
  PurchaseOrderSummary,
  SalesFulfillmentSummary,
  StockoutItem,
} from '../../core/models/dashboard.models';
import { DashboardService } from '../../core/services/dashboard.service';
import { WarehouseService } from '../../core/services/warehouse.service';
import { AuthService } from '../../core/services/auth.service';

/**
 * Day 5 inventory KPI dashboard — every number comes from persisted data via
 * the backend dashboard endpoints (no client-side guesses, no fake metrics).
 * Order-summary cards mirror the Day 4 read matrix: a card the role cannot
 * read is hidden and its 403 is tolerated.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/home" class="back">← Home</a>
          <h1>Inventory dashboard</h1>
        </div>
        <div class="filter">
          <label for="wh" class="sub">Warehouse</label>
          <select id="wh" [value]="warehouseFilter" (change)="onWarehouseChange($any($event.target).value)">
            <option value="">All warehouses</option>
            <option *ngFor="let w of warehouses" [value]="w.id">{{ w.code }} — {{ w.name }}</option>
          </select>
        </div>
      </header>

      <p class="notice error" *ngIf="error">{{ error }}</p>
      <p class="muted loading" *ngIf="loading">Loading KPIs from the backend…</p>

      <ng-container *ngIf="!loading && summary">
        <section class="kpis">
          <div class="kpi">
            <span class="label">Products</span>
            <span class="value">{{ summary.totalProducts }}</span>
            <span class="sub">{{ summary.activeProducts }} active</span>
          </div>
          <div class="kpi">
            <span class="label">Warehouses</span>
            <span class="value">{{ summary.totalWarehouses }}</span>
            <span class="sub">{{ summary.activeWarehouses }} active</span>
          </div>
          <div class="kpi">
            <span class="label">Units on hand</span>
            <span class="value">{{ summary.totalInventoryUnits | number:'1.0-4' }}</span>
            <span class="sub">sum of inventory quantities</span>
          </div>
          <div class="kpi">
            <span class="label">Inventory value</span>
            <span class="value">{{ summary.inventoryValue | number:'1.2-2' }}</span>
            <span class="sub">cost basis estimate</span>
          </div>
          <div class="kpi accent">
            <span class="label">Low stock</span>
            <span class="value">{{ summary.lowStockCount }}</span>
            <span class="sub">&lt; minimum stock level</span>
          </div>
          <div class="kpi danger">
            <span class="label">Stockouts</span>
            <span class="value">{{ summary.stockoutCount }}</span>
            <span class="sub">zero on hand</span>
          </div>
        </section>

        <section class="card" *ngIf="showPo && poSummary">
          <div class="card-head">
            <h2>Purchase orders</h2>
            <span class="sub">{{ poSummary.totalOrders }} total · <strong>{{ poSummary.pendingCount }} pending</strong>
              (submitted / partially received)</span>
          </div>
          <div class="chips">
            <span class="chip">DRAFT {{ poSummary.byStatus.DRAFT ?? 0 }}</span>
            <span class="chip">SUBMITTED {{ poSummary.byStatus.SUBMITTED ?? 0 }}</span>
            <span class="chip">PARTIALLY RECEIVED {{ poSummary.byStatus.PARTIALLY_RECEIVED ?? 0 }}</span>
            <span class="chip">RECEIVED {{ poSummary.byStatus.RECEIVED ?? 0 }}</span>
            <span class="chip">CANCELLED {{ poSummary.byStatus.CANCELLED ?? 0 }}</span>
          </div>
        </section>

        <section class="card" *ngIf="showSo && soSummary">
          <div class="card-head">
            <h2>Sales fulfillment</h2>
            <span class="sub">{{ soSummary.totalOrders }} total · <strong>{{ soSummary.pendingCount }} pending</strong>
              · {{ soSummary.remainingToFulfillUnits | number:'1.0-4' }} units remaining</span>
          </div>
          <div class="chips">
            <span class="chip">DRAFT {{ soSummary.byStatus.DRAFT ?? 0 }}</span>
            <span class="chip">CONFIRMED {{ soSummary.byStatus.CONFIRMED ?? 0 }}</span>
            <span class="chip">PARTIALLY FULFILLED {{ soSummary.byStatus.PARTIALLY_FULFILLED ?? 0 }}</span>
            <span class="chip">FULFILLED {{ soSummary.byStatus.FULFILLED ?? 0 }}</span>
            <span class="chip">CANCELLED {{ soSummary.byStatus.CANCELLED ?? 0 }}</span>
          </div>
        </section>

        <div class="columns">
          <section class="card">
            <div class="card-head">
              <h2>Low stock</h2>
              <a class="link" routerLink="/products">Manage products</a>
            </div>
            <p *ngIf="lowStock.length === 0" class="muted">No low-stock items.</p>
            <table *ngIf="lowStock.length > 0">
              <thead><tr><th>SKU</th><th>Warehouse</th><th>On hand</th><th>Min</th><th>Deficit</th></tr></thead>
              <tbody>
                <tr *ngFor="let item of lowStock">
                  <td><code>{{ item.sku }}</code><br /><small class="muted">{{ item.productName }}</small></td>
                  <td>{{ item.warehouseCode }}</td>
                  <td>{{ item.quantityOnHand | number:'1.0-4' }} {{ item.unit }}</td>
                  <td>{{ item.minimumStockLevel | number:'1.0-4' }}</td>
                  <td class="warn">{{ item.deficit | number:'1.0-4' }}</td>
                </tr>
              </tbody>
            </table>
          </section>

          <section class="card">
            <div class="card-head">
              <h2>Stockouts</h2>
            </div>
            <p *ngIf="stockouts.length === 0" class="muted">No stockouts.</p>
            <table *ngIf="stockouts.length > 0">
              <thead><tr><th>SKU</th><th>Warehouse</th><th>On hand</th></tr></thead>
              <tbody>
                <tr *ngFor="let item of stockouts">
                  <td><code>{{ item.sku }}</code><br /><small class="muted">{{ item.productName }}</small></td>
                  <td>{{ item.warehouseCode }}</td>
                  <td class="warn">{{ item.quantityOnHand | number:'1.0-4' }} {{ item.unit }}</td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>

        <section class="card">
          <div class="card-head">
            <h2>Recent stock movements</h2>
            <a class="link" routerLink="/inventory/history">Full history</a>
          </div>
          <p *ngIf="recent.length === 0" class="muted">No stock movements recorded yet.</p>
          <table *ngIf="recent.length > 0">
            <thead><tr><th>When</th><th>Product</th><th>Warehouse</th><th>Type</th><th>Qty</th><th>After</th><th>Reference</th><th>By</th></tr></thead>
            <tbody>
              <tr *ngFor="let tx of recent">
                <td>{{ tx.createdAt | date:'short' }}</td>
                <td><code>{{ tx.sku }}</code></td>
                <td>{{ tx.warehouseCode }}</td>
                <td>{{ tx.type }}</td>
                <td [class.warn]="tx.quantity < 0">{{ tx.quantity | number:'1.0-4' }}</td>
                <td>{{ tx.quantityAfter | number:'1.0-4' }}</td>
                <td><code>{{ tx.referenceNumber }}</code></td>
                <td>{{ tx.performedByUsername }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <p class="muted generated">Generated {{ summary.generatedAt | date:'medium' }} — data comes from persisted inventory and order records.</p>
      </ng-container>
    </main>
  `,
  styles: [`
    .page{min-height:100vh;background:#0f172a;color:#e2e8f0;padding:2rem;box-sizing:border-box}
    header{display:flex;justify-content:space-between;align-items:flex-end;gap:1rem;flex-wrap:wrap;max-width:1100px;margin:0 auto 1.5rem}
    .back{color:#93c5fd;text-decoration:none;font-size:.85rem}
    h1{margin:.25rem 0 0}h2{margin:0;font-size:1.05rem}
    .filter{display:flex;align-items:center;gap:.5rem}
    .filter select{padding:.5rem .7rem;border-radius:8px;border:1px solid #334155;background:#1e293b;color:#e2e8f0}
    .kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:.9rem;max-width:1100px;margin:0 auto 1rem}
    .kpi{background:#1e293b;border-radius:12px;padding:1rem 1.2rem;display:flex;flex-direction:column;gap:.25rem;border-top:3px solid #334155}
    .kpi.accent{border-top-color:#f59e0b}.kpi.danger{border-top-color:#ef4444}
    .kpi .label{color:#94a3b8;font-size:.78rem;text-transform:uppercase;letter-spacing:.04em}
    .kpi .value{font-size:1.7rem;font-weight:700}
    .card{max-width:1100px;margin:0 auto 1rem;background:#1e293b;border-radius:12px;padding:1.1rem 1.4rem;box-sizing:border-box}
    .card-head{display:flex;justify-content:space-between;align-items:baseline;gap:1rem;flex-wrap:wrap;margin-bottom:.6rem}
    .sub{color:#94a3b8;font-size:.85rem}
    .chips{display:flex;flex-wrap:wrap;gap:.5rem}
    .chip{background:#0f172a;border:1px solid #334155;color:#cbd5e1;padding:.25rem .7rem;border-radius:999px;font-size:.8rem}
    .columns{max-width:1100px;margin:0 auto;display:grid;grid-template-columns:repeat(auto-fit,minmax(380px,1fr));gap:1rem}
    .columns .card{margin:0 0 1rem}
    table{width:100%;border-collapse:collapse}
    th,td{text-align:left;padding:.5rem .45rem;border-bottom:1px solid #334155;font-size:.88rem;vertical-align:top}
    .warn{color:#fbbf24}
    .muted{color:#94a3b8}
    .loading,.generated{max-width:1100px;margin:0 auto 1rem}
    .generated{font-size:.8rem}
    .link{color:#93c5fd;text-decoration:none;font-size:.85rem}
    .notice{max-width:1100px;margin:0 auto 1rem;padding:.6rem 1rem;border-radius:8px}
    .error{background:#451a1a;color:#fca5a5}
  `],
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(DashboardService);
  private readonly warehousesApi = inject(WarehouseService);
  private readonly auth = inject(AuthService);

  warehouses: Warehouse[] = [];
  warehouseFilter = '';

  summary: DashboardSummary | null = null;
  lowStock: LowStockItem[] = [];
  stockouts: StockoutItem[] = [];
  recent: StockTransaction[] = [];
  poSummary: PurchaseOrderSummary | null = null;
  soSummary: SalesFulfillmentSummary | null = null;

  loading = false;
  error: string | null = null;

  get showPo(): boolean {
    return this.auth.role !== 'SALES_USER';
  }

  get showSo(): boolean {
    return this.auth.role !== 'PROCUREMENT_USER';
  }

  ngOnInit(): void {
    this.warehousesApi.list('', null, 0, 100).subscribe({
      next: (page) => (this.warehouses = page.content),
      error: () => (this.warehouses = []),
    });
    this.loadAll();
  }

  onWarehouseChange(value: string): void {
    this.warehouseFilter = value;
    this.loadAll();
  }

  private get warehouseId(): number | null {
    return this.warehouseFilter === '' ? null : Number(this.warehouseFilter);
  }

  loadAll(): void {
    const w = this.warehouseId;
    this.loading = true;
    this.error = null;
    forkJoin({
      summary: this.api.summary(w),
      low: this.api.lowStock(w),
      out: this.api.stockouts(w),
      recent: this.api.recentTransactions(w, 10),
      po: this.showPo ? this.api.purchaseOrdersSummary(w).pipe(catchError(() => of(null))) : of(null),
      so: this.showSo ? this.api.salesFulfillmentSummary(w).pipe(catchError(() => of(null))) : of(null),
    }).subscribe({
      next: (res) => {
        this.loading = false;
        this.summary = res.summary;
        this.lowStock = res.low.content;
        this.stockouts = res.out.content;
        this.recent = res.recent;
        this.poSummary = res.po;
        this.soSummary = res.so;
      },
      error: (err) => {
        this.loading = false;
        this.summary = null;
        this.error =
          (err as { error?: { message?: string } })?.error?.message ??
          `Failed to load dashboard KPIs (HTTP ${(err as { status?: number })?.status ?? '?'}).`;
      },
    });
  }
}
