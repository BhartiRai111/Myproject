import { useEffect, useState } from 'react';
import { Alert, Badge, Button, Card, Col, Form, Modal, Pagination, Row, Spinner, Table } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { saleApi } from '../api/saleApi';
import SaleViewModal from '../components/SaleViewModal';
import { useAuth } from '../context/AuthContext';
import { PaymentStatus } from '../types/purchase';
import { Sale, SaleStatus } from '../types/sale';

const PAGE_SIZE = 10;

function paymentStatusVariant(status: PaymentStatus) {
  if (status === 'PAID') return 'success';
  if (status === 'PARTIAL') return 'warning';
  return 'danger';
}

function saleStatusVariant(status: SaleStatus) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'CANCELLED') return 'danger';
  return 'secondary';
}

export default function Sales() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManage = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER';

  const [sales, setSales] = useState<Sale[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [paymentStatusFilter, setPaymentStatusFilter] = useState<PaymentStatus | ''>('');
  const [statusFilter, setStatusFilter] = useState<SaleStatus | ''>('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [viewSale, setViewSale] = useState<Sale | null>(null);
  const [cancelTarget, setCancelTarget] = useState<Sale | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState('');

  const loadSales = async () => {
    setLoading(true);
    try {
      const res = await saleApi.list({
        search,
        paymentStatus: paymentStatusFilter,
        status: statusFilter,
        fromDate,
        toDate,
        page,
        size: PAGE_SIZE,
      });
      setSales(res.data.content);
      setTotalPages(res.data.totalPages);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSales();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, paymentStatusFilter, statusFilter, fromDate, toDate]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadSales();
  };

  const handleCancelConfirm = async () => {
    if (!cancelTarget) return;
    setCancelling(true);
    setError('');
    try {
      await saleApi.cancel(cancelTarget.id);
      setCancelTarget(null);
      loadSales();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to cancel sale');
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h3 className="mb-0">Sales</h3>
        <Button variant="success" onClick={() => navigate('/sales/new')}>
          Add Sale
        </Button>
      </div>

      {error && <Alert variant="danger">{error}</Alert>}

      <Card className="sh-card mb-3">
        <Card.Body>
          <Form onSubmit={handleSearchSubmit}>
            <Row className="g-3 align-items-end">
              <Col md={3}>
                <Form.Label>Search</Form.Label>
                <Form.Control
                  placeholder="Invoice number or customer"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </Col>
              <Col md={2}>
                <Form.Label>Payment Status</Form.Label>
                <Form.Select
                  value={paymentStatusFilter}
                  onChange={(e) => {
                    setPage(0);
                    setPaymentStatusFilter(e.target.value as PaymentStatus | '');
                  }}
                >
                  <option value="">All</option>
                  <option value="PAID">Paid</option>
                  <option value="PARTIAL">Partial</option>
                  <option value="UNPAID">Unpaid</option>
                </Form.Select>
              </Col>
              <Col md={2}>
                <Form.Label>Sale Status</Form.Label>
                <Form.Select
                  value={statusFilter}
                  onChange={(e) => {
                    setPage(0);
                    setStatusFilter(e.target.value as SaleStatus | '');
                  }}
                >
                  <option value="">All</option>
                  <option value="PENDING">Pending</option>
                  <option value="COMPLETED">Completed</option>
                  <option value="CANCELLED">Cancelled</option>
                </Form.Select>
              </Col>
              <Col md={2}>
                <Form.Label>From Date</Form.Label>
                <Form.Control
                  type="date"
                  value={fromDate}
                  onChange={(e) => {
                    setPage(0);
                    setFromDate(e.target.value);
                  }}
                />
              </Col>
              <Col md={2}>
                <Form.Label>To Date</Form.Label>
                <Form.Control
                  type="date"
                  value={toDate}
                  onChange={(e) => {
                    setPage(0);
                    setToDate(e.target.value);
                  }}
                />
              </Col>
              <Col md={1}>
                <Button type="submit" variant="outline-success" className="w-100">
                  Go
                </Button>
              </Col>
            </Row>
          </Form>
        </Card.Body>
      </Card>

      <Card className="sh-card">
        <Card.Body>
          {loading ? (
            <div className="text-center py-5">
              <Spinner animation="border" variant="success" />
            </div>
          ) : sales.length === 0 ? (
            <div className="text-center text-muted py-5">No sales found.</div>
          ) : (
            <>
              <Table hover responsive>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Invoice Number</th>
                    <th>Customer</th>
                    <th>Sale Date</th>
                    <th className="text-end">Total Amount</th>
                    <th>Payment Status</th>
                    <th>Sale Status</th>
                    <th className="text-end">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {sales.map((s) => (
                    <tr key={s.id}>
                      <td>{s.id}</td>
                      <td>{s.invoiceNumber}</td>
                      <td>{s.customer ? `${s.customer.firstName} ${s.customer.lastName || ''}` : 'Walk-in'}</td>
                      <td>{s.saleDate}</td>
                      <td className="text-end">{s.totalAmount.toFixed(2)}</td>
                      <td>
                        <Badge bg={paymentStatusVariant(s.paymentStatus)}>{s.paymentStatus}</Badge>
                      </td>
                      <td>
                        <Badge bg={saleStatusVariant(s.status)}>{s.status}</Badge>
                      </td>
                      <td className="text-end">
                        <Button size="sm" variant="outline-secondary" className="me-2" onClick={() => setViewSale(s)}>
                          View
                        </Button>
                        {canManage && s.status !== 'CANCELLED' && (
                          <Button
                            size="sm"
                            variant="outline-primary"
                            className="me-2"
                            onClick={() => navigate(`/sales/${s.id}/edit`)}
                          >
                            Edit
                          </Button>
                        )}
                        {canManage && s.status !== 'CANCELLED' && (
                          <Button size="sm" variant="outline-danger" onClick={() => setCancelTarget(s)}>
                            Cancel
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>

              {totalPages > 1 && (
                <Pagination className="justify-content-end mb-0">
                  {Array.from({ length: totalPages }).map((_, idx) => (
                    <Pagination.Item key={idx} active={idx === page} onClick={() => setPage(idx)}>
                      {idx + 1}
                    </Pagination.Item>
                  ))}
                </Pagination>
              )}
            </>
          )}
        </Card.Body>
      </Card>

      <SaleViewModal show={!!viewSale} sale={viewSale} onClose={() => setViewSale(null)} />

      <Modal show={!!cancelTarget} onHide={() => setCancelTarget(null)} centered>
        <Modal.Header closeButton>
          <Modal.Title>Cancel Sale</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          Are you sure you want to cancel sale <strong>{cancelTarget?.invoiceNumber}</strong>? This cannot be undone.
          If this sale was completed, its sold quantities will be restored to stock, and it will remain in sales
          history with a Cancelled status.
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={() => setCancelTarget(null)} disabled={cancelling}>
            Keep Sale
          </Button>
          <Button variant="danger" onClick={handleCancelConfirm} disabled={cancelling}>
            {cancelling ? <Spinner size="sm" animation="border" /> : 'Cancel Sale'}
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
}
