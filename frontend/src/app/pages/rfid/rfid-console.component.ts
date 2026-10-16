import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Product } from '../../core/models/master-data.models';
import {
  RfidReadEvent,
  RfidReaderInfo,
  RfidTag,
} from '../../core/models/rfid.models';
import { ProductService } from '../../core/services/product.service';
import { RfidService } from '../../core/services/rfid.service';
import { AuthService } from '../../core/services/auth.service';

/**
 * Day 5 RFID console: reader info, tag registry (mapping writes are ADMIN),
 * simulated/external read processing and the processed-read log. Reads are
 * informational and never change stock — stock movements keep using the
 * inventory/order workflows.
 */
@Component({
  selector: 'app-rfid-console',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/home" class="back">← Home</a>
          <h1>RFID console</h1>
        </div>
      </header>

      <section class="card reader" *ngIf="reader">
        <div>
          <span class="chip" [class.hw]="!reader.simulated">
            {{ reader.simulated ? 'SIMULATOR' : 'HARDWARE' }}
          </span>
          <h2 style="margin:.5rem 0 .25rem">{{ reader.readerName }}</h2>
          <p class="hint">{{ reader.note }}</p>
        </div>
      </section>

      <section class="card form-card" *ngIf="canProcessReads">
        <h2>Process a read</h2>
        <p class="hint">
          With the simulator enabled, EPC is optional (one active tag is picked automatically).
          Uncheck it to submit an external/physical reader read — then EPC is required.
          Processing a read never changes stock.
        </p>
        <form [formGroup]="readForm" (ngSubmit)="onProcessRead()">
          <div class="grid">
            <label>EPC code<input type="text" formControlName="epcCode" placeholder="3034F9C2A1B0" /></label>
            <label>Event reference<input type="text" formControlName="eventReference" placeholder="Optional idempotency key" /></label>
            <label *ngIf="!simulate">Reader name<input type="text" formControlName="readerName" placeholder="DOCK-DOOR-1" /></label>
            <label class="check"><input type="checkbox" formControlName="simulate" /> Use simulator</label>
          </div>
          <div class="row">
            <button type="submit" [disabled]="processing">{{ processing ? 'Processing…' : 'Process read' }}</button>
          </div>
        </form>
        <p class="notice error" *ngIf="readError">{{ readError }}</p>
        <div class="read-result" *ngIf="lastRead">
          <span class="chip" [class.ok]="lastRead.status === 'RESOLVED'" [class.bad]="lastRead.status !== 'RESOLVED'">
            {{ lastRead.status }}
          </span>
          <span>EPC <code>{{ lastRead.epcCode }}</code></span>
          <span *ngIf="lastRead.sku">→ <code>{{ lastRead.sku }}</code> {{ lastRead.productName }}</span>
          <span class="muted">ref {{ lastRead.eventReference }}</span>
          <span class="muted">{{ lastRead.simulated ? 'simulated' : 'hardware' }} · {{ lastRead.readerName }}</span>
        </div>
      </section>

      <section class="card form-card" *ngIf="canManageTags && showTagForm">
        <h2>{{ editingTag ? 'Remap tag ' + editingTag.epcCode : 'Register tag' }}</h2>
        <form [formGroup]="tagForm" (ngSubmit)="onSubmitTag()">
          <div class="grid">
            <label>EPC code
              <input type="text" formControlName="epcCode" placeholder="3034F9C2A1B0" [readOnly]="!!editingTag" />
            </label>
            <label>Product
              <select formControlName="productId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let p of products" [ngValue]="p.id">{{ p.sku }} — {{ p.name }}</option>
              </select>
            </label>
            <label class="check"><input type="checkbox" formControlName="active" /> Active</label>
          </div>
          <div class="row">
            <button type="submit" [disabled]="tagForm.invalid || savingTag">{{ savingTag ? 'Saving…' : 'Save' }}</button>
            <button type="button" class="ghost" (click)="onCancelTag()">Cancel</button>
          </div>
        </form>
      </section>

      <section class="card">
        <div class="card-head">
          <h2>Tag registry</h2>
          <button *ngIf="canManageTags && !showTagForm" (click)="onNewTag()">+ Register tag</button>
        </div>
        <div class="filters">
          <input type="text" placeholder="Search EPC, SKU or product…" [value]="tagSearch"
            (input)="onTagSearch($any($event.target).value)" />
          <select [value]="tagActiveFilter" (change)="onTagActiveFilter($any($event.target).value)">
            <option value="">All statuses</option>
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </select>
        </div>
        <p class="notice error" *ngIf="tagError">{{ tagError }}</p>
        <p class="notice success" *ngIf="tagMessage">{{ tagMessage }}</p>
        <p *ngIf="tagsLoading" class="muted">Loading…</p>
        <p *ngIf="!tagsLoading && tags.length === 0" class="muted">No RFID tags registered yet.</p>
        <table *ngIf="!tagsLoading && tags.length > 0">
          <thead><tr><th>EPC</th><th>Product</th><th>Status</th><th>Registered by</th><th *ngIf="canManageTags">Actions</th></tr></thead>
          <tbody>
            <tr *ngFor="let tag of tags">
              <td><code>{{ tag.epcCode }}</code></td>
              <td><code>{{ tag.sku }}</code> {{ tag.productName }}</td>
              <td>{{ tag.active ? 'Active' : 'Inactive' }}</td>
              <td>{{ tag.registeredByUsername }}</td>
              <td *ngIf="canManageTags">
                <button class="link" (click)="onEditTag(tag)">Remap</button>
                <button class="link" [style.color]="tag.active ? '#fca5a5' : null" (click)="onToggleTag(tag)">{{ tag.active ? 'Deactivate' : 'Activate' }}</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p class="muted" *ngIf="tagsTotal > 0">Total: {{ tagsTotal }}</p>
      </section>

      <section class="card">
        <div class="card-head">
          <h2>Read events</h2>
          <button class="ghost" (click)="loadReads()">Refresh</button>
        </div>
        <div class="filters">
          <input type="text" placeholder="Filter by EPC…" [value]="readFilterEpc"
            (input)="onReadFilter($any($event.target).value)" />
          <select [value]="readFilterStatus" (change)="onReadStatusFilter($any($event.target).value)">
            <option value="">All statuses</option>
            <option value="RESOLVED">RESOLVED</option>
            <option value="INACTIVE_TAG">INACTIVE_TAG</option>
            <option value="UNKNOWN_TAG">UNKNOWN_TAG</option>
          </select>
        </div>
        <p *ngIf="readsLoading" class="muted">Loading…</p>
        <p *ngIf="!readsLoading && reads.length === 0" class="muted">No read events yet.</p>
        <table *ngIf="!readsLoading && reads.length > 0">
          <thead><tr><th>Received</th><th>EPC</th><th>Status</th><th>Source</th><th>Product</th><th>By</th></tr></thead>
          <tbody>
            <tr *ngFor="let ev of reads">
              <td>{{ ev.receivedAt | date:'short' }}</td>
              <td><code>{{ ev.epcCode }}</code></td>
              <td>
                <span class="chip" [class.ok]="ev.status === 'RESOLVED'" [class.bad]="ev.status !== 'RESOLVED'">
                  {{ ev.status }}
                </span>
              </td>
              <td>{{ ev.simulated ? 'simulator' : ev.readerName }}</td>
              <td><code>{{ ev.sku ?? '—' }}</code></td>
              <td>{{ ev.performedByUsername }}</td>
            </tr>
          </tbody>
        </table>
        <p class="muted" *ngIf="readsTotal > 0">Total: {{ readsTotal }}</p>
      </section>
    </main>
  `,
  styles: [`
    .page{min-height:100vh;background:#0f172a;color:#e2e8f0;padding:2rem;box-sizing:border-box}
    header{display:flex;justify-content:space-between;align-items:flex-end;max-width:1000px;margin:0 auto 1.5rem}
    .back{color:#93c5fd;text-decoration:none;font-size:.85rem}
    h1{margin:.25rem 0 0}h2{margin:0;font-size:1.05rem}
    .card{max-width:1000px;margin:0 auto 1rem;background:#1e293b;border-radius:12px;padding:1.25rem 1.5rem}
    .card-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:.75rem}
    .reader h2{margin:.5rem 0 .25rem}
    .hint,.muted{color:#94a3b8}
    .hint{font-size:.88rem}
    .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:0 .9rem}
    label{display:block;margin-bottom:.8rem;font-size:.9rem}
    label input,label select,.filters input,.filters select{padding:.55rem .7rem;border-radius:8px;border:1px solid #334155;background:#0f172a;color:#e2e8f0}
    label input,label select{width:100%;margin-top:.3rem;box-sizing:border-box}
    .check{display:flex;gap:.5rem;align-items:center}
    .check input{width:auto;margin:0}
    .row,.filters{display:flex;gap:.75rem}
    .filters{margin-bottom:.9rem}
    .filters input,.filters select{flex:1}
    button{padding:.55rem 1.1rem;border-radius:8px;border:0;background:#2563eb;color:#fff;font-weight:600;cursor:pointer}
    button:disabled{opacity:.6;cursor:default}
    button.ghost{background:#0f172a;border:1px solid #334155}
    button.link{background:none;border:0;color:#93c5fd;padding:.2rem .4rem}
    table{width:100%;border-collapse:collapse}
    th,td{text-align:left;padding:.55rem .5rem;border-bottom:1px solid #334155;font-size:.9rem}
    .chip{display:inline-block;background:#1e3a8a;color:#bfdbfe;padding:.15rem .6rem;border-radius:999px;font-size:.75rem}
    .chip.hw,.chip.ok,.success{background:#14532d;color:#bbf7d0}
    .chip.bad{background:#451a1a;color:#fca5a5}
    .read-result{display:flex;flex-wrap:wrap;gap:.9rem;align-items:center;margin-top:.9rem;padding:.7rem 1rem;background:#0f172a;border-radius:8px}
    .notice{margin:.6rem 0;padding:.6rem 1rem;border-radius:8px}
    .error{background:#451a1a;color:#fca5a5}
  `],
})
export class RfidConsoleComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly rfidApi = inject(RfidService);
  private readonly productsApi = inject(ProductService);
  private readonly auth = inject(AuthService);

  reader: RfidReaderInfo | null = null;

  products: Product[] = [];

  tags: RfidTag[] = [];
  tagsTotal = 0;
  tagsLoading = false;
  tagSearch = '';
  tagActiveFilter = '';
  tagError: string | null = null;
  tagMessage: string | null = null;
  showTagForm = false;
  editingTag: RfidTag | null = null;
  savingTag = false;
  private tagSearchTimer: ReturnType<typeof setTimeout> | null = null;

  reads: RfidReadEvent[] = [];
  readsTotal = 0;
  readsLoading = false;
  readFilterEpc = '';
  readFilterStatus = '';
  private readSearchTimer: ReturnType<typeof setTimeout> | null = null;

  lastRead: RfidReadEvent | null = null;
  readError: string | null = null;
  processing = false;

  tagForm = this.fb.group({
    epcCode: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9.\-:]{4,96}$/)]],
    productId: [null as number | null, [Validators.required]],
    active: [true],
  });

  readForm = this.fb.group({
    epcCode: ['', [Validators.pattern(/^[A-Za-z0-9.\-:]{4,96}$/)]],
    eventReference: [''],
    readerName: [''],
    simulate: [true],
  });

  get role(): string | null {
    return this.auth.role;
  }

  get canManageTags(): boolean {
    return this.role === 'ADMIN';
  }

  get canProcessReads(): boolean {
    return ['ADMIN', 'INVENTORY_MANAGER', 'WAREHOUSE_STAFF', 'PROCUREMENT_USER', 'SALES_USER']
      .includes(this.role ?? '');
  }

  get simulate(): boolean {
    return !!this.readForm.value.simulate;
  }

  ngOnInit(): void {
    this.rfidApi.readerInfo().subscribe({
      next: (info) => (this.reader = info),
      error: () => (this.reader = null),
    });
    this.productsApi.list('', true, null, 0, 200).subscribe({
      next: (page) => (this.products = page.content),
      error: () => (this.products = []),
    });
    this.loadTags();
    this.loadReads();
  }

  loadTags(): void {
    this.tagsLoading = true;
    this.tagError = null;
    const active = this.tagActiveFilter === '' ? null : this.tagActiveFilter === 'true';
    this.rfidApi.listTags(this.tagSearch, active, null, 0, 20).subscribe({
      next: (page) => {
        this.tagsLoading = false;
        this.tags = page.content;
        this.tagsTotal = page.totalElements;
      },
      error: (err) => {
        this.tagsLoading = false;
        this.tagError = this.describeError(err, 'Failed to load RFID tags.');
      },
    });
  }

  onTagSearch(value: string): void {
    this.tagSearch = value;
    if (this.tagSearchTimer) clearTimeout(this.tagSearchTimer);
    this.tagSearchTimer = setTimeout(() => this.loadTags(), 350);
  }

  onTagActiveFilter(value: string): void {
    this.tagActiveFilter = value;
    this.loadTags();
  }

  onNewTag(): void {
    this.editingTag = null;
    this.tagForm.reset({ epcCode: '', productId: null, active: true });
    this.showTagForm = true;
    this.tagError = null;
    this.tagMessage = null;
  }

  onEditTag(tag: RfidTag): void {
    this.editingTag = tag;
    this.tagForm.reset({ epcCode: tag.epcCode, productId: tag.productId, active: tag.active });
    this.showTagForm = true;
    this.tagError = null;
    this.tagMessage = null;
  }

  onCancelTag(): void {
    this.showTagForm = false;
    this.editingTag = null;
  }

  onSubmitTag(): void {
    if (this.tagForm.invalid) return;
    const v = this.tagForm.getRawValue();
    this.savingTag = true;
    this.tagError = null;
    this.tagMessage = null;
    const done = {
      next: () => {
        this.savingTag = false;
        this.showTagForm = false;
        this.editingTag = null;
        this.tagMessage = 'RFID tag saved.';
        this.loadTags();
      },
      error: (err: unknown) => {
        this.savingTag = false;
        this.tagError = this.describeError(err, 'Failed to save RFID tag.');
      },
    };
    if (this.editingTag) {
      this.rfidApi
        .updateTag(this.editingTag.epcCode, { productId: v.productId!, active: !!v.active })
        .subscribe(done);
    } else {
      this.rfidApi
        .registerTag({
          epcCode: v.epcCode!.trim().toUpperCase(),
          productId: v.productId!,
          active: !!v.active,
        })
        .subscribe(done);
    }
  }

  onToggleTag(tag: RfidTag): void {
    const action = tag.active ? 'Deactivate' : 'Activate';
    if (!confirm(`${action} tag "${tag.epcCode}"?`)) return;
    this.tagError = null;
    this.tagMessage = null;
    this.rfidApi.updateTag(tag.epcCode, { productId: tag.productId, active: !tag.active }).subscribe({
      next: () => {
        this.tagMessage = `Tag ${tag.epcCode} ${tag.active ? 'deactivated' : 'activated'}.`;
        this.loadTags();
      },
      error: (err) => (this.tagError = this.describeError(err, 'Failed to update tag.')),
    });
  }

  onProcessRead(): void {
    const v = this.readForm.getRawValue();
    if (!v.simulate && !v.epcCode?.trim()) {
      this.readError = 'EPC code is required for external/hardware reads.';
      return;
    }
    this.processing = true;
    this.readError = null;
    this.lastRead = null;
    this.rfidApi
      .processRead({
        epcCode: v.epcCode?.trim() ? v.epcCode.trim().toUpperCase() : null,
        eventReference: v.eventReference?.trim() || null,
        simulate: !!v.simulate,
        readerName: !v.simulate && v.readerName?.trim() ? v.readerName.trim() : null,
      })
      .subscribe({
        next: (event) => {
          this.processing = false;
          this.lastRead = event;
          this.loadReads();
        },
        error: (err) => {
          this.processing = false;
          this.readError = this.describeError(err, 'Failed to process read.');
        },
      });
  }

  loadReads(): void {
    this.readsLoading = true;
    this.rfidApi
      .listReads(
        {
          epcCode: this.readFilterEpc,
          status: (this.readFilterStatus || null) as RfidReadEvent['status'] | null,
        },
        0,
        20
      )
      .subscribe({
        next: (page) => {
          this.readsLoading = false;
          this.reads = page.content;
          this.readsTotal = page.totalElements;
        },
        error: () => (this.readsLoading = false),
      });
  }

  onReadFilter(value: string): void {
    this.readFilterEpc = value;
    if (this.readSearchTimer) clearTimeout(this.readSearchTimer);
    this.readSearchTimer = setTimeout(() => this.loadReads(), 350);
  }

  onReadStatusFilter(value: string): void {
    this.readFilterStatus = value;
    this.loadReads();
  }

  private describeError(err: unknown, fallback: string): string {
    const e = err as { status?: number; error?: { message?: string } };
    if (e?.error?.message) return e.error.message;
    if (e?.status === 409) return 'Conflict: duplicate EPC code or replayed event reference.';
    if (e?.status === 403) return 'Forbidden: your role cannot perform this action.';
    if (e?.status === 404) return 'Tag or read event not found.';
    return fallback;
  }
}
