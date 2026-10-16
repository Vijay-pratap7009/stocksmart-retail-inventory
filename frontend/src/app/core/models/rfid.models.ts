/** Day 5 RFID models mirroring the backend DTOs. */

export type RfidReadStatus = 'RESOLVED' | 'INACTIVE_TAG' | 'UNKNOWN_TAG';

export interface RfidTag {
  id: number;
  epcCode: string;
  productId: number;
  sku: string;
  productName: string;
  active: boolean;
  registeredByUsername: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface RfidTagRequest {
  epcCode: string;
  productId: number;
  active?: boolean | null;
}

export interface RfidTagUpdateRequest {
  productId: number;
  active: boolean;
}

export interface RfidReadRequest {
  epcCode?: string | null;
  eventReference?: string | null;
  simulate?: boolean | null;
  readerName?: string | null;
  eventAt?: string | null;
}

export interface RfidReadEvent {
  id: number;
  eventReference: string;
  epcCode: string;
  status: RfidReadStatus;
  simulated: boolean;
  readerName: string;
  tagId?: number | null;
  productId?: number | null;
  sku?: string | null;
  productName?: string | null;
  eventAt?: string;
  receivedAt?: string;
  performedByUsername: string;
}

export interface RfidReaderInfo {
  readerName: string;
  simulated: boolean;
  note: string;
}
