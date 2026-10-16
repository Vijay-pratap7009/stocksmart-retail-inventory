import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Product } from '../../core/models/master-data.models';
import { ProductService } from '../../core/services/product.service';

interface ScanLogEntry {
  value: string;
  sku: string;
  name: string;
  at: Date;
}

/**
 * Day 5 barcode operations screen: resolves a scanned barcode (or SKU) to a
 * product. Scanning is strictly read-only — it never changes stock; stock
 * movements go through the inventory/order workflows.
 */
@Component({
  selector: 'app-barcode-scan',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/home" class="back">← Home</a>
          <h1>Barcode scan</h1>
        </div>
      </header>

      <section class="card">
        <p class="hint">
          Scan or type a barcode (digits) or product SKU, then press Enter.
          Resolving a scan only looks up the product — it never changes stock.
        </p>
        <div class="scan-row">
          <input
            type="text"
            name="query"
            [(ngModel)]="query"
            (keyup.enter)="onScan()"
            autofocus
            placeholder="e.g. 8901234567890 or PHN-001"
            [disabled]="loading"
          />
          <button (click)="onScan()" [disabled]="loading || !query.trim()">
            {{ loading ? 'Looking up…' : 'Scan' }}
          </button>
        </div>
        <p class="notice error" *ngIf="error">{{ error }}</p>
        <p class="notice error" *ngIf="notFound">
          No product found for "{{ lastQueried }}" — check the barcode/SKU or register it first.
        </p>
      </section>

      <section class="card" *ngIf="product">
        <div class="result-head">
          <h2>{{ product.name }}</h2>
          <span class="chip" [class.off]="!product.active">{{ product.active ? 'Active' : 'Inactive' }}</span>
        </div>
        <dl class="details">
          <div><dt>SKU</dt><dd><code>{{ product.sku }}</code></dd></div>
          <div><dt>Barcode</dt><dd><code>{{ product.barcode ?? '—' }}</code></dd></div>
          <div><dt>Category</dt><dd>{{ product.categoryName ?? '—' }}</dd></div>
          <div><dt>Brand</dt><dd>{{ product.brand ?? '—' }}</dd></div>
          <div><dt>Unit</dt><dd>{{ product.unit }}</dd></div>
          <div><dt>Cost price</dt><dd>{{ product.costPrice }}</dd></div>
          <div><dt>Selling price</dt><dd>{{ product.sellingPrice }}</dd></div>
          <div><dt>Minimum stock level</dt><dd>{{ product.minimumStockLevel ?? 0 }}</dd></div>
        </dl>
        <div class="row">
          <a class="btn" routerLink="/inventory/operations">Record receipt/adjustment</a>
          <a class="btn ghost" routerLink="/inventory">View stock levels</a>
        </div>
      </section>

      <section class="card" *ngIf="log.length > 0">
        <h2>Recent scans (this session)</h2>
        <table>
          <thead><tr><th>Scanned value</th><th>SKU</th><th>Product</th><th>When</th></tr></thead>
          <tbody>
            <tr *ngFor="let entry of log">
              <td><code>{{ entry.value }}</code></td>
              <td><code>{{ entry.sku }}</code></td>
              <td>{{ entry.name }}</td>
              <td>{{ entry.at | date:'short' }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </main>
  `,
  styles: [`
    .page { min-height: 100vh; background: #0f172a; color: #e2e8f0; padding: 2rem; box-sizing: border-box; }
    header { max-width: 860px; margin: 0 auto 1.5rem; }
    .back { color: #93c5fd; text-decoration: none; font-size: .85rem; }
    h1 { margin: .25rem 0 0; } h2 { margin-top: 0; font-size: 1.1rem; }
    .card { max-width: 860px; margin: 0 auto 1rem; background: #1e293b; border-radius: 12px; padding: 1.25rem 1.5rem; }
    .hint { color: #94a3b8; margin-top: 0; }
    .scan-row { display: flex; gap: .75rem; }
    .scan-row input { flex: 1; padding: .7rem .8rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; font-size: 1.05rem; }
    button { padding: .6rem 1.3rem; border-radius: 8px; border: 0; background: #2563eb; color: #fff; font-weight: 600; cursor: pointer; }
    button:disabled { opacity: .6; cursor: default; }
    .result-head { display: flex; justify-content: space-between; align-items: center; }
    .chip { background: #14532d; color: #bbf7d0; padding: .15rem .6rem; border-radius: 999px; font-size: .8rem; }
    .chip.off { background: #451a1a; color: #fca5a5; }
    .details { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: .4rem 1rem; margin: 1rem 0; }
    .details dt { color: #94a3b8; font-size: .78rem; text-transform: uppercase; letter-spacing: .03em; }
    .details dd { margin: .1rem 0 .6rem; }
    .row { display: flex; gap: .75rem; margin-top: .5rem; }
    .btn { display: inline-block; padding: .55rem 1.1rem; border-radius: 8px; background: #2563eb; color: #fff; font-weight: 600; text-decoration: none; }
    .btn.ghost { background: #0f172a; border: 1px solid #334155; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: .55rem .5rem; border-bottom: 1px solid #334155; font-size: .9rem; }
    .notice { margin: .8rem 0 0; padding: .6rem 1rem; border-radius: 8px; }
    .error { background: #451a1a; color: #fca5a5; }
  `],
})
export class BarcodeScanComponent {
  private readonly api = inject(ProductService);

  query = '';
  product: Product | null = null;
  loading = false;
  error: string | null = null;
  notFound = false;
  lastQueried = '';
  log: ScanLogEntry[] = [];

  onScan(): void {
    const raw = this.query.trim();
    if (!raw || this.loading) return;
    this.loading = true;
    this.error = null;
    this.notFound = false;
    this.product = null;
    this.lastQueried = raw;

    const looksLikeBarcode = /^\d{6,}$/.test(raw);
    const first$ = looksLikeBarcode ? this.api.getByBarcode(raw) : this.api.getBySku(raw);
    const fallback$ = looksLikeBarcode ? this.api.getBySku(raw) : this.api.getByBarcode(raw);

    first$.subscribe({
      next: (p) => this.onFound(p, raw),
      error: (err) => {
        if ((err as { status?: number })?.status === 404) {
          fallback$.subscribe({
            next: (p) => this.onFound(p, raw),
            error: () => {
              this.loading = false;
              this.notFound = true;
            },
          });
        } else {
          this.loading = false;
          this.error =
            (err as { error?: { message?: string } })?.error?.message ??
            `Lookup failed (HTTP ${(err as { status?: number })?.status ?? '?'}).`;
        }
      },
    });
  }

  private onFound(p: Product, raw: string): void {
    this.loading = false;
    this.product = p;
    this.log = [
      { value: raw, sku: p.sku, name: p.name, at: new Date() },
      ...this.log.slice(0, 4),
    ];
    this.query = '';
  }
}
