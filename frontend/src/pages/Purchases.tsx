import { useEffect, useState } from 'react';
import { Alert, Badge, Button, Card, Col, Form, Modal, Pagination, Row, Spinner, Table } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { purchaseApi } from '../api/purchaseApi';
import PurchaseViewModal from '../components/PurchaseViewModal';
import { PaymentStatus, Purchase, PurchaseStatus } from '../types/purchase';

const PAGE_SIZE = 10;

function paymentStatusVariant(status: PaymentStatus) {
  if (status === 'PAID') return 'success';
  if (status === 'PARTIAL') return 'warning';
  return 'danger';
}

function purchaseStatusVariant(status: PurchaseStatus) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'CANCELLED') return 'danger';
  return 'secondary';
}

export default function Purchases() {
  const navigate = useNavigate();

  const [purchases, setPurchases] = useState<Purchase[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [paymentStatusFilter, setPaymentStatusFilter] = useState<PaymentStatus | ''>('');
  const [statusFilter, setStatusFilter] = useState<PurchaseStatus | ''>('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [viewPurchase, setViewPurchase] = useState<Purchase | null>(null);
  const [cancelTarget, setCancelTarget] = useState<Purchase | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState('');

  const loadPurchases = async () => {
    setLoading(true);
    try {
      const res = await purchaseApi.list({
        search,
        paymentStatus: paymentStatusFilter,
        status: statusFilter,
        fromDate,
        toDate,
        page,
        size: PAGE_SIZE,
      });
      setPurchases(res.data.content);
      setTotalPages(res.data.totalPages);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPurchases();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, paymentStatusFilter, statusFilter, fromDate, toDate]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadPurchases();
  };

  const handleCancelConfirm = async () => {
    if (!cancelTarget) return;
    setCancelling(true);
    setError('');
    try {
      await purchaseApi.cancel(cancelTarget.id);
      setCancelTarget(null);
      loadPurchases();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to cancel purchase');
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h3 className="mb-0">Purchases</h3>
        <Button variant="success" onClick={() => navigate('/purchases/new')}>
          Add Purchase
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
                  placeholder="Purchase number or supplier"
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
                <Form.Label>Purchase Status</Form.Label>
                <Form.Select
                  value={statusFilter}
                  onChange={(e) => {
                    setPage(0);
                    setStatusFilter(e.target.value as PurchaseStatus | '');
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
          ) : purchases.length === 0 ? (
            <div className="text-center text-muted py-5">No purchases found.</div>
          ) : (
            <>
              <Table hover responsive>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Purchase Number</th>
                    <th>Supplier</th>
                    <th>Purchase Date</th>
                    <th className="text-end">Total Amount</th>
                    <th>Payment Status</th>
                    <th>Purchase Status</th>
                    <th className="text-end">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {purchases.map((p) => (
                    <tr key={p.id}>
                      <td>{p.id}</td>
                      <td>{p.purchaseNumber}</td>
                      <td>{p.supplier.name}</td>
                      <td>{p.purchaseDate}</td>
                      <td className="text-end">{p.totalAmount.toFixed(2)}</td>
                      <td>
                        <Badge bg={paymentStatusVariant(p.paymentStatus)}>{p.paymentStatus}</Badge>
                      </td>
                      <td>
                        <Badge bg={purchaseStatusVariant(p.status)}>{p.status}</Badge>
                      </td>
                      <td className="text-end">
                        <Button size="sm" variant="outline-secondary" className="me-2" onClick={() => setViewPurchase(p)}>
                          View
                        </Button>
                        {p.status !== 'CANCELLED' && (
                          <Button
                            size="sm"
                            variant="outline-primary"
                            className="me-2"
                            onClick={() => navigate(`/purchases/${p.id}/edit`)}
                          >
                            Edit
                          </Button>
                        )}
                        {p.status !== 'CANCELLED' && (
                          <Button size="sm" variant="outline-danger" onClick={() => setCancelTarget(p)}>
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

      <PurchaseViewModal show={!!viewPurchase} purchase={viewPurchase} onClose={() => setViewPurchase(null)} />

      <Modal show={!!cancelTarget} onHide={() => setCancelTarget(null)} centered>
        <Modal.Header closeButton>
          <Modal.Title>Cancel Purchase</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          Are you sure you want to cancel purchase{' '}
          <strong>{cancelTarget?.purchaseNumber}</strong>? This cannot be undone, and the purchase will remain in
          history with a Cancelled status.
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={() => setCancelTarget(null)} disabled={cancelling}>
            Keep Purchase
          </Button>
          <Button variant="danger" onClick={handleCancelConfirm} disabled={cancelling}>
            {cancelling ? <Spinner size="sm" animation="border" /> : 'Cancel Purchase'}
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
}
