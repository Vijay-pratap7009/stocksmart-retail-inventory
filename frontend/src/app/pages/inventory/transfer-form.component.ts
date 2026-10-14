import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page, Product, Warehouse } from '../../core/models/master-data.models';
import { TransferResult } from '../../core/models/inventory.models';
import { InventoryService } from '../../core/services/inventory.service';
import { ProductService } from '../../core/services/product.service';
import { WarehouseService } from '../../core/services/warehouse.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-transfer-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/inventory" class="back">← Inventory</a>
          <h1>Warehouse transfer</h1>
        </div>
      </header>

      <p class="notice error" *ngIf="error">{{ error }}</p>
      <p class="notice success" *ngIf="message">{{ message }}</p>

      <section class="card" *ngIf="canOperate">
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <div class="grid">
            <label>Product
              <select formControlName="productId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let p of products" [ngValue]="p.id">{{ p.sku }} — {{ p.name }}</option>
              </select>
            </label>
            <label>Source warehouse
              <select formControlName="sourceWarehouseId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.code }} — {{ w.name }}</option>
              </select>
            </label>
            <label>Destination warehouse
              <select formControlName="destinationWarehouseId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.code }} — {{ w.name }}</option>
              </select>
            </label>
            <label>Quantity<input type="number" step="0.0001" min="0" formControlName="quantity" /></label>
          </div>
          <label>Reference (optional)<input type="text" formControlName="reference" placeholder="Auto-generated when blank" /></label>
          <label>Notes (optional)<input type="text" formControlName="notes" /></label>
          <p class="warn" *ngIf="sameWarehouse()">Source and destination must be different.</p>
          <button type="submit" [disabled]="form.invalid || sameWarehouse() || saving">
            {{ saving ? 'Transferring…' : 'Submit transfer' }}
          </button>
        </form>
      </section>

      <section class="card" *ngIf="result">
        <h2>Transfer {{ result.referenceNumber }} committed</h2>
        <p>OUT: {{ result.transferOut.quantity }} ({{ result.transferOut.quantityBefore }} → {{ result.transferOut.quantityAfter }}) at {{ result.transferOut.warehouseCode }}</p>
        <p>IN: {{ result.transferIn.quantity }} ({{ result.transferIn.quantityBefore }} → {{ result.transferIn.quantityAfter }}) at {{ result.transferIn.warehouseCode }}</p>
      </section>

      <section class="card" *ngIf="!canOperate">
        <p class="muted">Your role ({{ role }}) has read-only inventory access. Transfers require an operational role.</p>
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
    .warn { color: #fca5a5; }
    .notice { max-width: 900px; margin: 0 auto 1rem; padding: .6rem 1rem; border-radius: 8px; }
    .error { background: #451a1a; color: #fca5a5; }
    .success { background: #14532d; color: #bbf7d0; }
  `],
})
export class TransferFormComponent implements OnInit {
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
  result: TransferResult | null = null;

  get role(): string | null {
    return this.auth.role;
  }

  get canOperate(): boolean {
    return this.role === 'ADMIN' || this.role === 'INVENTORY_MANAGER' || this.role === 'WAREHOUSE_STAFF';
  }

  form = this.fb.group({
    productId: [null as number | null, [Validators.required]],
    sourceWarehouseId: [null as number | null, [Validators.required]],
    destinationWarehouseId: [null as number | null, [Validators.required]],
    quantity: [null as number | null, [Validators.required, Validators.min(0.0001)]],
    reference: [''],
    notes: [''],
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

  sameWarehouse(): boolean {
    const v = this.form.getRawValue();
    return v.sourceWarehouseId != null && v.sourceWarehouseId === v.destinationWarehouseId;
  }

  onSubmit(): void {
    if (this.form.invalid || this.sameWarehouse()) return;
    const v = this.form.getRawValue();
    if (!confirm(`Transfer ${v.quantity} units between warehouses?`)) return;
    this.saving = true;
    this.error = null;
    this.message = null;
    this.result = null;
    this.service.transfer({
      productId: v.productId!,
      sourceWarehouseId: v.sourceWarehouseId!,
      destinationWarehouseId: v.destinationWarehouseId!,
      quantity: Number(v.quantity),
      reference: v.reference?.trim() || null,
      notes: v.notes?.trim() || null,
    }).subscribe({
      next: (res) => {
        this.saving = false;
        this.result = res;
        this.message = `Transfer ${res.referenceNumber} committed atomically.`;
        this.form.reset();
      },
      error: (err: unknown) => {
        this.saving = false;
        const e = err as { status?: number; error?: { message?: string } };
        this.error = e?.error?.message
          ?? (e?.status === 409 ? 'Conflict: insufficient stock at source.'
          : e?.status === 403 ? 'Forbidden: insufficient role.'
          : 'Failed to submit transfer.');
      },
    });
  }
}
