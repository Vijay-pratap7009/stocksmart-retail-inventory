import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page, Product, Warehouse } from '../../core/models/master-data.models';
import { StockTransaction } from '../../core/models/inventory.models';
import { InventoryService } from '../../core/services/inventory.service';
import { ProductService } from '../../core/services/product.service';
import { WarehouseService } from '../../core/services/warehouse.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-stock-operations',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/inventory" class="back">← Inventory</a>
          <h1>Receipts &amp; adjustments</h1>
        </div>
      </header>

      <p class="notice error" *ngIf="error">{{ error }}</p>
      <p class="notice success" *ngIf="message">{{ message }}</p>

      <section class="card" *ngIf="canOperate">
        <h2>Manual stock receipt</h2>
        <form [formGroup]="receiptForm" (ngSubmit)="onReceipt()">
          <div class="grid">
            <label>Product
              <select formControlName="productId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let p of products" [ngValue]="p.id">{{ p.sku }} — {{ p.name }}</option>
              </select>
            </label>
            <label>Warehouse
              <select formControlName="warehouseId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.code }} — {{ w.name }}</option>
              </select>
            </label>
            <label>Quantity<input type="number" step="0.0001" min="0" formControlName="quantity" /></label>
          </div>
          <label>Notes (optional)<input type="text" formControlName="notes" /></label>
          <button type="submit" [disabled]="receiptForm.invalid || saving">
            {{ saving ? 'Recording…' : 'Record receipt' }}
          </button>
        </form>
      </section>

      <section class="card" *ngIf="canOperate">
        <h2>Stock adjustment (reason required)</h2>
        <form [formGroup]="adjustForm" (ngSubmit)="onAdjust()">
          <div class="grid">
            <label>Product
              <select formControlName="productId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let p of products" [ngValue]="p.id">{{ p.sku }} — {{ p.name }}</option>
              </select>
            </label>
            <label>Warehouse
              <select formControlName="warehouseId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.code }} — {{ w.name }}</option>
              </select>
            </label>
            <label>Direction
              <select formControlName="direction">
                <option value="IN">Increase</option>
                <option value="OUT">Decrease</option>
              </select>
            </label>
            <label>Quantity<input type="number" step="0.0001" min="0" formControlName="quantity" /></label>
          </div>
          <label>Reason<input type="text" formControlName="reason" placeholder="Cycle count correction" /></label>
          <button type="submit" [disabled]="adjustForm.invalid || saving">
            {{ saving ? 'Applying…' : 'Apply adjustment' }}
          </button>
        </form>
      </section>

      <section class="card" *ngIf="lastTxn">
        <h2>Last recorded movement</h2>
        <p><code>{{ lastTxn.referenceNumber }}</code> · {{ lastTxn.type }} ·
          {{ lastTxn.quantity }} ({{ lastTxn.quantityBefore }} → {{ lastTxn.quantityAfter }}) ·
          {{ lastTxn.sku }} at {{ lastTxn.warehouseCode }} · by {{ lastTxn.performedByUsername }}</p>
      </section>

      <section class="card" *ngIf="!canOperate">
        <p class="muted">Your role ({{ role }}) has read-only inventory access. Receipts and adjustments require an operational role.</p>
      </section>
    </main>
  `,
  styles: [`
    .page { min-height: 100vh; background: #0f172a; color: #e2e8f0; padding: 2rem; box-sizing: border-box; }
    header { max-width: 900px; margin: 0 auto 1.5rem; }
    .back { color: #93c5fd; text-decoration: none; font-size: .85rem; }
    h1 { margin: .25rem 0 0; } h2 { margin-top: 0; font-size: 1.1rem; }
    .card { max-width: 900px; margin: 0 auto 1rem; background: #1e293b; border-radius: 12px; padding: 1.25rem 1.5rem; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0 .9rem; }
    label { display: block; margin-bottom: .8rem; font-size: .9rem; }
    label input, label select { width: 100%; margin-top: .3rem; padding: .55rem .7rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; box-sizing: border-box; }
    button { padding: .55rem 1.1rem; border-radius: 8px; border: 0; background: #2563eb; color: #fff; font-weight: 600; cursor: pointer; }
    button:disabled { opacity: .6; cursor: default; }
    .muted { color: #94a3b8; }
    .notice { max-width: 900px; margin: 0 auto 1rem; padding: .6rem 1rem; border-radius: 8px; }
    .error { background: #451a1a; color: #fca5a5; }
    .success { background: #14532d; color: #bbf7d0; }
  `],
})
export class StockOperationsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(InventoryService);
  private readonly productsApi = inject(ProductService);
  private readonly warehousesApi = inject(WarehouseService);
  private readonly auth = inject(AuthService);

  products: Product[] = [];
  warehouses: Warehouse[] = [];
  saving = false;
  error: string | null = null;
  message: string | null = null;
  lastTxn: StockTransaction | null = null;

  get role(): string | null {
    return this.auth.role;
  }

  get canOperate(): boolean {
    return this.role === 'ADMIN' || this.role === 'INVENTORY_MANAGER' || this.role === 'WAREHOUSE_STAFF';
  }

  receiptForm = this.fb.group({
    productId: [null as number | null, [Validators.required]],
    warehouseId: [null as number | null, [Validators.required]],
    quantity: [null as number | null, [Validators.required, Validators.min(0.0001)]],
    notes: [''],
  });

  adjustForm = this.fb.group({
    productId: [null as number | null, [Validators.required]],
    warehouseId: [null as number | null, [Validators.required]],
    direction: ['IN', [Validators.required]],
    quantity: [null as number | null, [Validators.required, Validators.min(0.0001)]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    this.productsApi.list('', null, null, 0, 200).subscribe({
      next: (p: Page<Product>) => {
        this.products = p.content;
      },
      error: () => {
        this.error = 'Failed to load products.';
      },
    });
    this.warehousesApi.list('', null, 0, 200).subscribe({
      next: (p: Page<Warehouse>) => {
        this.warehouses = p.content;
      },
      error: () => {
        this.error = 'Failed to load warehouses.';
      },
    });
  }

  onReceipt(): void {
    if (this.receiptForm.invalid) return;
    const v = this.receiptForm.getRawValue();
    if (!confirm(`Receive ${v.quantity} units?`)) return;
    this.submit({
      productId: v.productId!,
      warehouseId: v.warehouseId!,
      quantity: Number(v.quantity),
      notes: v.notes?.trim() || null,
    }, 'receipt');
  }

  onAdjust(): void {
    if (this.adjustForm.invalid) return;
    const v = this.adjustForm.getRawValue();
    if (!confirm(`Apply ${v.direction === 'IN' ? 'increase' : 'decrease'} of ${v.quantity}?`)) return;
    this.submit({
      productId: v.productId!,
      warehouseId: v.warehouseId!,
      direction: v.direction as 'IN' | 'OUT',
      quantity: Number(v.quantity),
      reason: v.reason!.trim(),
    }, 'adjustment');
  }

  private submit(
    req: { productId: number; warehouseId: number; quantity: number; notes?: string | null; direction?: 'IN' | 'OUT'; reason?: string },
    kind: 'receipt' | 'adjustment'
  ): void {
    this.saving = true;
    this.error = null;
    this.message = null;
    const done = {
      next: (txn: StockTransaction) => {
        this.saving = false;
        this.lastTxn = txn;
        this.message = `${kind === 'receipt' ? 'Receipt' : 'Adjustment'} recorded: ${txn.quantityBefore} → ${txn.quantityAfter} (${txn.referenceNumber}).`;
        this.receiptForm.reset();
        this.adjustForm.reset({ direction: 'IN' });
      },
      error: (err: unknown) => {
        this.saving = false;
        this.error = this.describeError(err, `Failed to record ${kind}.`);
      },
    };
    if (kind === 'receipt') this.service.receipt(req).subscribe(done);
    else {
      this.service.adjust({
        productId: req.productId,
        warehouseId: req.warehouseId,
        direction: req.direction!,
        quantity: req.quantity,
        reason: req.reason!,
      }).subscribe(done);
    }
  }

  private describeError(err: unknown, fallback: string): string {
    const e = err as { status?: number; error?: { message?: string } };
    if (e?.error?.message) return e.error.message;
    if (e?.status === 409) return 'Conflict: insufficient stock or duplicate record.';
    if (e?.status === 403) return 'Forbidden: insufficient role.';
    if (e?.status === 404) return 'Product or warehouse not found.';
    return fallback;
  }
}
