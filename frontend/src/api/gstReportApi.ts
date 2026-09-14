import api from './axios';
import {
  GstLiabilityResponse,
  GstReportListResponse,
  Gstr1Response,
  Gstr3bResponse,
  HsnSummaryReportResponse,
  PurchaseGstReportResponse,
  ReconciliationResponse,
  TaxRateSummaryReportResponse,
} from '../types/gstReport';

export interface DateRangeQuery {
  fromDate?: string;
  toDate?: string;
}

export interface PageQuery {
  page?: number;
  size?: number;
}

export const gstReportApi = {
  gstr1: (query: DateRangeQuery & { returnPeriod?: string }) =>
    api.get<Gstr1Response>('/gst-reports/gstr1', {
      params: { fromDate: query.fromDate, toDate: query.toDate, returnPeriod: query.returnPeriod || undefined },
    }),

  purchaseGstReport: (query: DateRangeQuery & { returnPeriod?: string } & PageQuery) =>
    api.get<PurchaseGstReportResponse>('/gst-reports/purchase', {
      params: {
        fromDate: query.fromDate,
        toDate: query.toDate,
        returnPeriod: query.returnPeriod || undefined,
        page: query.page ?? 0,
        size: query.size ?? 20,
      },
    }),

  gstr3b: (returnPeriod: string) => api.get<Gstr3bResponse>('/gst-reports/gstr3b', { params: { returnPeriod } }),

  outputGst: (query: DateRangeQuery & PageQuery) =>
    api.get<GstReportListResponse>('/gst-reports/output', {
      params: { fromDate: query.fromDate, toDate: query.toDate, page: query.page ?? 0, size: query.size ?? 20 },
    }),

  inputGst: (query: DateRangeQuery & PageQuery) =>
    api.get<GstReportListResponse>('/gst-reports/input', {
      params: { fromDate: query.fromDate, toDate: query.toDate, page: query.page ?? 0, size: query.size ?? 20 },
    }),

  hsnSummary: (query: DateRangeQuery) =>
    api.get<HsnSummaryReportResponse>('/gst-reports/hsn', { params: query }),

  taxRateSummary: (query: DateRangeQuery) =>
    api.get<TaxRateSummaryReportResponse>('/gst-reports/tax-rate', { params: query }),

  liability: (returnPeriod: string) =>
    api.get<GstLiabilityResponse>('/gst-reports/liability', { params: { returnPeriod } }),

  reconciliation: (query: DateRangeQuery) =>
    api.get<ReconciliationResponse>('/gst-reports/reconciliation', { params: query }),
};
