import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Form, Modal, Row, Spinner, Table } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';
import { productApi } from '../api/productApi';
import { purchaseApi } from '../api/purchaseApi';
import { supplierApi } from '../api/supplierApi';
import { ApiErrorResponse } from '../types/user';
import { PaymentStatus, Product, Purchase, PurchaseStatus, Supplier } from '../types/purchase';

interface ItemRow {
  productId: string;
  quantity: string;
  purchasePrice: string;
  discount: string;
  tax: string;
}

const EMPTY_ROW: ItemRow = { productId: '', quantity: '1', purchasePrice: '', discount: '0', tax: '0' };

function toNumber(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function rowSubtotal(row: ItemRow): number {
  return toNumber(row.quantity) * toNumber(row.purchasePrice) - toNumber(row.discount) + toNumber(row.tax);
}

function SupplierQuickAddModal({
  show,
  onClose,
  onCreated,
}: {
  show: boolean;
  onClose: () => void;
  onCreated: (supplier: Supplier) => void;
}) {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (show) {
      setName('');
      setPhone('');
      setEmail('');
      setAddress('');
      setError('');
    }
  }, [show]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const res = await supplierApi.create({ name, phone, email, address });
      onCreated(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to add supplier');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal show={show} onHide={onClose} centered>
      <Form onSubmit={handleSubmit}>
        <Modal.Header closeButton>
          <Modal.Title>Add Supplier</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {error && <Alert variant="danger">{error}</Alert>}
          <Form.Group className="mb-3">
            <Form.Label>Name</Form.Label>
            <Form.Control required value={name} onChange={(e) => setName(e.target.value)} />
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Phone</Form.Label>
            <Form.Control value={phone} onChange={(e) => setPhone(e.target.value)} />
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Email</Form.Label>
            <Form.Control type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Address</Form.Label>
            <Form.Control value={address} onChange={(e) => setAddress(e.target.value)} />
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button variant="success" type="submit" disabled={submitting}>
            {submitting ? <Spinner size="sm" animation="border" /> : 'Add Supplier'}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}

function ProductQuickAddModal({
  show,
  onClose,
  onCreated,
}: {
  show: boolean;
  onClose: () => void;
  onCreated: (product: Product) => void;
}) {
  const [name, setName] = useState('');
  const [unit, setUnit] = useState('pcs');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (show) {
      setName('');
      setUnit('pcs');
      setError('');
    }
  }, [show]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const res = await productApi.create({ name, unit });
      onCreated(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to add product');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal show={show} onHide={onClose} centered>
      <Form onSubmit={handleSubmit}>
        <Modal.Header closeButton>
          <Modal.Title>Add Product</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {error && <Alert variant="danger">{error}</Alert>}
          <Form.Group className="mb-3">
            <Form.Label>Name</Form.Label>
            <Form.Control required value={name} onChange={(e) => setName(e.target.value)} />
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Unit</Form.Label>
            <Form.Control value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="pcs, kg, box..." />
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button variant="success" type="submit" disabled={submitting}>
            {submitting ? <Spinner size="sm" animation="border" /> : 'Add Product'}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}

export default function PurchaseForm() {
  const { id } = useParams();
  const isEdit = !!id;
  const navigate = useNavigate();

  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(isEdit);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [supplierId, setSupplierId] = useState('');
  const [purchaseDate, setPurchaseDate] = useState('');
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>('UNPAID');
  const [status, setStatus] = useState<PurchaseStatus>('PENDING');
  const [notes, setNotes] = useState('');
  const [items, setItems] = useState<ItemRow[]>([{ ...EMPTY_ROW }]);

  const [showSupplierModal, setShowSupplierModal] = useState(false);
  const [showProductModal, setShowProductModal] = useState(false);

  useEffect(() => {
    const loadReferenceData = async () => {
      const [supplierRes, productRes] = await Promise.all([supplierApi.list(), productApi.list()]);
      setSuppliers(supplierRes.data);
      setProducts(productRes.data);
    };

    const loadPurchase = async () => {
      if (!isEdit) return;
      const res = await purchaseApi.getById(Number(id));
      const purchase: Purchase = res.data;
      setSupplierId(String(purchase.supplier.id));
      setPurchaseDate(purchase.purchaseDate);
      setPaymentStatus(purchase.paymentStatus);
      setStatus(purchase.status);
      setNotes(purchase.notes || '');
      setItems(
        purchase.items.map((item) => ({
          productId: String(item.product.id),
          quantity: String(item.quantity),
          purchasePrice: String(item.purchasePrice),
          discount: String(item.discount),
          tax: String(item.tax),
        }))
      );
    };

    Promise.all([loadReferenceData(), loadPurchase()]).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const updateItem = (index: number, field: keyof ItemRow, value: string) => {
    setItems((prev) => prev.map((row, i) => (i === index ? { ...row, [field]: value } : row)));
  };

  const addItemRow = () => setItems((prev) => [...prev, { ...EMPTY_ROW }]);

  const removeItemRow = (index: number) => {
    setItems((prev) => prev.filter((_, i) => i !== index));
  };

  const subtotalAmount = items.reduce((sum, row) => sum + toNumber(row.quantity) * toNumber(row.purchasePrice), 0);
  const totalDiscount = items.reduce((sum, row) => sum + toNumber(row.discount), 0);
  const totalTax = items.reduce((sum, row) => sum + toNumber(row.tax), 0);
  const grandTotal = items.reduce((sum, row) => sum + rowSubtotal(row), 0);

  const validate = (): string | null => {
    if (!supplierId) return 'Supplier is required';
    if (!purchaseDate) return 'Purchase date is required';
    if (items.length === 0) return 'At least one purchase item is required';

    const seenProducts = new Set<string>();
    for (const row of items) {
      if (!row.productId) return 'Product is required for every item';
      if (seenProducts.has(row.productId)) {
        return 'The same product cannot be added more than once. Update the existing item instead.';
      }
      seenProducts.add(row.productId);

      if (toNumber(row.quantity) <= 0) return 'Quantity must be greater than 0';
      if (toNumber(row.purchasePrice) < 0) return 'Purchase price must be greater than or equal to 0';
      if (toNumber(row.discount) < 0) return 'Discount cannot be negative';
      if (toNumber(row.tax) < 0) return 'Tax cannot be negative';
    }

    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    const payload = {
      supplierId: Number(supplierId),
      purchaseDate,
      paymentStatus,
      notes,
      items: items.map((row) => ({
        productId: Number(row.productId),
        quantity: toNumber(row.quantity),
        purchasePrice: toNumber(row.purchasePrice),
        discount: toNumber(row.discount),
        tax: toNumber(row.tax),
      })),
    };

    setSubmitting(true);
    try {
      if (isEdit) {
        await purchaseApi.update(Number(id), { ...payload, status });
      } else {
        await purchaseApi.create(payload);
      }
      navigate('/purchases');
    } catch (err: any) {
      const apiError: ApiErrorResponse | undefined = err.response?.data;
      setError(apiError?.message || 'Failed to save purchase');
      setFieldErrors(apiError?.fieldErrors || {});
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="text-center py-5">
        <Spinner animation="border" variant="success" />
      </div>
    );
  }

  return (
    <div>
      <h3 className="mb-4">{isEdit ? 'Edit Purchase' : 'Create Purchase'}</h3>

      {error && <Alert variant="danger">{error}</Alert>}

      <Form onSubmit={handleSubmit}>
        <Card className="sh-card mb-3">
          <Card.Body>
            <Row className="g-3">
              <Col md={4}>
                <Form.Label>Supplier</Form.Label>
                <div className="d-flex gap-2">
                  <Form.Select
                    value={supplierId}
                    onChange={(e) => setSupplierId(e.target.value)}
                    isInvalid={!!fieldErrors.supplierId}
                  >
                    <option value="">Select supplier</option>
                    {suppliers.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                  </Form.Select>
                  <Button variant="outline-success" onClick={() => setShowSupplierModal(true)}>
                    +
                  </Button>
                </div>
              </Col>
              <Col md={3}>
                <Form.Label>Purchase Date</Form.Label>
                <Form.Control
                  type="date"
                  value={purchaseDate}
                  onChange={(e) => setPurchaseDate(e.target.value)}
                  isInvalid={!!fieldErrors.purchaseDate}
                />
              </Col>
              <Col md={2}>
                <Form.Label>Payment Status</Form.Label>
                <Form.Select value={paymentStatus} onChange={(e) => setPaymentStatus(e.target.value as PaymentStatus)}>
                  <option value="UNPAID">Unpaid</option>
                  <option value="PARTIAL">Partial</option>
                  <option value="PAID">Paid</option>
                </Form.Select>
              </Col>
              {isEdit && (
                <Col md={3}>
                  <Form.Label>Purchase Status</Form.Label>
                  <Form.Select value={status} onChange={(e) => setStatus(e.target.value as PurchaseStatus)}>
                    <option value="PENDING">Pending</option>
                    <option value="COMPLETED">Completed</option>
                  </Form.Select>
                </Col>
              )}
              <Col md={12}>
                <Form.Label>Notes</Form.Label>
                <Form.Control as="textarea" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
              </Col>
            </Row>
          </Card.Body>
        </Card>

        <Card className="sh-card mb-3">
          <Card.Body>
            <div className="d-flex justify-content-between align-items-center mb-3">
              <Card.Title className="fs-6 mb-0">Purchase Items</Card.Title>
              <div className="d-flex gap-2">
                <Button size="sm" variant="outline-success" onClick={() => setShowProductModal(true)}>
                  + New Product
                </Button>
                <Button size="sm" variant="success" onClick={addItemRow}>
                  + Add Item
                </Button>
              </div>
            </div>

            <Table responsive size="sm">
              <thead>
                <tr>
                  <th style={{ minWidth: 200 }}>Product</th>
                  <th style={{ width: 100 }}>Quantity</th>
                  <th style={{ width: 130 }}>Purchase Price</th>
                  <th style={{ width: 110 }}>Discount</th>
                  <th style={{ width: 110 }}>Tax</th>
                  <th className="text-end" style={{ width: 120 }}>
                    Subtotal
                  </th>
                  <th style={{ width: 50 }} />
                </tr>
              </thead>
              <tbody>
                {items.map((row, index) => (
                  <tr key={index}>
                    <td>
                      <Form.Select value={row.productId} onChange={(e) => updateItem(index, 'productId', e.target.value)}>
                        <option value="">Select product</option>
                        {products.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name} ({p.unit})
                          </option>
                        ))}
                      </Form.Select>
                    </td>
                    <td>
                      <Form.Control
                        type="number"
                        min={1}
                        value={row.quantity}
                        onChange={(e) => updateItem(index, 'quantity', e.target.value)}
                      />
                    </td>
                    <td>
                      <Form.Control
                        type="number"
                        min={0}
                        step="0.01"
                        value={row.purchasePrice}
                        onChange={(e) => updateItem(index, 'purchasePrice', e.target.value)}
                      />
                    </td>
                    <td>
                      <Form.Control
                        type="number"
                        min={0}
                        step="0.01"
                        value={row.discount}
                        onChange={(e) => updateItem(index, 'discount', e.target.value)}
                      />
                    </td>
                    <td>
                      <Form.Control
                        type="number"
                        min={0}
                        step="0.01"
                        value={row.tax}
                        onChange={(e) => updateItem(index, 'tax', e.target.value)}
                      />
                    </td>
                    <td className="text-end align-middle">{rowSubtotal(row).toFixed(2)}</td>
                    <td className="text-end align-middle">
                      <Button
                        size="sm"
                        variant="outline-danger"
                        onClick={() => removeItemRow(index)}
                        disabled={items.length === 1}
                      >
                        &times;
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>

            <div className="d-flex justify-content-end">
              <Table borderless size="sm" className="mb-0" style={{ width: 280 }}>
                <tbody>
                  <tr>
                    <td className="text-muted">Subtotal</td>
                    <td className="text-end">{subtotalAmount.toFixed(2)}</td>
                  </tr>
                  <tr>
                    <td className="text-muted">Discount</td>
                    <td className="text-end">-{totalDiscount.toFixed(2)}</td>
                  </tr>
                  <tr>
                    <td className="text-muted">Tax</td>
                    <td className="text-end">+{totalTax.toFixed(2)}</td>
                  </tr>
                  <tr>
                    <td className="fw-semibold">Grand Total</td>
                    <td className="text-end fw-semibold">{grandTotal.toFixed(2)}</td>
                  </tr>
                </tbody>
              </Table>
            </div>
          </Card.Body>
        </Card>

        <div className="d-flex gap-2">
          <Button type="submit" variant="success" disabled={submitting}>
            {submitting ? <Spinner size="sm" animation="border" /> : isEdit ? 'Save Changes' : 'Create Purchase'}
          </Button>
          <Button type="button" variant="outline-secondary" onClick={() => navigate('/purchases')}>
            Cancel
          </Button>
        </div>
      </Form>

      <SupplierQuickAddModal
        show={showSupplierModal}
        onClose={() => setShowSupplierModal(false)}
        onCreated={(supplier) => {
          setSuppliers((prev) => [...prev, supplier]);
          setSupplierId(String(supplier.id));
          setShowSupplierModal(false);
        }}
      />

      <ProductQuickAddModal
        show={showProductModal}
        onClose={() => setShowProductModal(false)}
        onCreated={(product) => {
          setProducts((prev) => [...prev, product]);
          setShowProductModal(false);
        }}
      />
    </div>
  );
}
