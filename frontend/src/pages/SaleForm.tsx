import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Form, Row, Spinner, Table } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';
import CustomerQuickAddModal from '../components/CustomerQuickAddModal';
import ProductQuickAddModal from '../components/ProductQuickAddModal';
import { customerApi } from '../api/customerApi';
import { productApi } from '../api/productApi';
import { saleApi } from '../api/saleApi';
import { PaymentStatus, Product } from '../types/purchase';
import { Customer, Sale, SaleStatus } from '../types/sale';
import { ApiErrorResponse } from '../types/user';

interface ItemRow {
  productId: string;
  quantity: string;
  sellingPrice: string;
  discount: string;
  tax: string;
}

const EMPTY_ROW: ItemRow = { productId: '', quantity: '1', sellingPrice: '', discount: '0', tax: '0' };

function toNumber(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function rowSubtotal(row: ItemRow): number {
  return toNumber(row.quantity) * toNumber(row.sellingPrice) - toNumber(row.discount) + toNumber(row.tax);
}

export default function SaleForm() {
  const { id } = useParams();
  const isEdit = !!id;
  const navigate = useNavigate();

  const [customers, setCustomers] = useState<Customer[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(isEdit);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Tracks quantities already committed by THIS sale before edits, so we can
  // compute the true available stock while editing an already-completed sale
  // (its own committed quantity is not yet "free" until the edit is saved).
  const [reservedQuantityByProduct, setReservedQuantityByProduct] = useState<Record<number, number>>({});

  const [customerId, setCustomerId] = useState('');
  const [saleDate, setSaleDate] = useState('');
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>('UNPAID');
  const [status, setStatus] = useState<SaleStatus>('COMPLETED');
  const [notes, setNotes] = useState('');
  const [items, setItems] = useState<ItemRow[]>([{ ...EMPTY_ROW }]);

  const [showCustomerModal, setShowCustomerModal] = useState(false);
  const [showProductModal, setShowProductModal] = useState(false);

  useEffect(() => {
    const loadReferenceData = async () => {
      const [customerRes, productRes] = await Promise.all([customerApi.list(), productApi.list()]);
      setCustomers(customerRes.data);
      setProducts(productRes.data);
    };

    const loadSale = async () => {
      if (!isEdit) return;
      const res = await saleApi.getById(Number(id));
      const sale: Sale = res.data;
      setCustomerId(sale.customer ? String(sale.customer.id) : '');
      setSaleDate(sale.saleDate);
      setPaymentStatus(sale.paymentStatus);
      setStatus(sale.status);
      setNotes(sale.notes || '');
      setItems(
        sale.items.map((item) => ({
          productId: String(item.product.id),
          quantity: String(item.quantity),
          sellingPrice: String(item.sellingPrice),
          discount: String(item.discount),
          tax: String(item.tax),
        }))
      );

      if (sale.status === 'COMPLETED') {
        const reserved: Record<number, number> = {};
        sale.items.forEach((item) => {
          reserved[item.product.id] = (reserved[item.product.id] || 0) + item.quantity;
        });
        setReservedQuantityByProduct(reserved);
      }
    };

    Promise.all([loadReferenceData(), loadSale()]).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const getProduct = (productId: string) => products.find((p) => String(p.id) === productId);

  const availableStock = (productId: string): number | null => {
    const product = getProduct(productId);
    if (!product) return null;
    const reserved = reservedQuantityByProduct[product.id] || 0;
    return product.stockQuantity + reserved;
  };

  const updateItem = (index: number, field: keyof ItemRow, value: string) => {
    setItems((prev) =>
      prev.map((row, i) => {
        if (i !== index) return row;
        const updated = { ...row, [field]: value };
        if (field === 'productId') {
          const product = getProduct(value);
          updated.sellingPrice = product ? String(product.sellingPrice) : '';
        }
        return updated;
      })
    );
  };

  const addItemRow = () => setItems((prev) => [...prev, { ...EMPTY_ROW }]);

  const removeItemRow = (index: number) => {
    setItems((prev) => prev.filter((_, i) => i !== index));
  };

  const subtotalAmount = items.reduce((sum, row) => sum + toNumber(row.quantity) * toNumber(row.sellingPrice), 0);
  const totalDiscount = items.reduce((sum, row) => sum + toNumber(row.discount), 0);
  const totalTax = items.reduce((sum, row) => sum + toNumber(row.tax), 0);
  const grandTotal = items.reduce((sum, row) => sum + rowSubtotal(row), 0);

  const validate = (): string | null => {
    if (!saleDate) return 'Sale date is required';
    if (items.length === 0) return 'At least one sale item is required';

    const seenProducts = new Set<string>();
    for (const row of items) {
      if (!row.productId) return 'Product is required for every item';
      if (seenProducts.has(row.productId)) {
        return 'The same product cannot be added more than once. Update the existing item instead.';
      }
      seenProducts.add(row.productId);

      if (toNumber(row.quantity) <= 0) return 'Quantity must be greater than 0';
      if (toNumber(row.sellingPrice) < 0) return 'Selling price must be greater than or equal to 0';
      if (toNumber(row.discount) < 0) return 'Discount cannot be negative';
      if (toNumber(row.tax) < 0) return 'Tax cannot be negative';

      const available = availableStock(row.productId);
      if (available !== null && toNumber(row.quantity) > available) {
        const product = getProduct(row.productId);
        return `Quantity for ${product?.name} exceeds available stock (available: ${available})`;
      }
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
      customerId: customerId ? Number(customerId) : null,
      saleDate,
      paymentStatus,
      notes,
      items: items.map((row) => ({
        productId: Number(row.productId),
        quantity: toNumber(row.quantity),
        sellingPrice: toNumber(row.sellingPrice),
        discount: toNumber(row.discount),
        tax: toNumber(row.tax),
      })),
    };

    setSubmitting(true);
    try {
      if (isEdit) {
        await saleApi.update(Number(id), { ...payload, status });
      } else {
        await saleApi.create(payload);
      }
      navigate('/sales');
    } catch (err: any) {
      const apiError: ApiErrorResponse | undefined = err.response?.data;
      setError(apiError?.message || 'Failed to save sale');
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
      <h3 className="mb-4">{isEdit ? 'Edit Sale' : 'Create Sale'}</h3>

      {error && <Alert variant="danger">{error}</Alert>}

      <Form onSubmit={handleSubmit}>
        <Card className="sh-card mb-3">
          <Card.Body>
            <Row className="g-3">
              <Col md={4}>
                <Form.Label>Customer</Form.Label>
                <div className="d-flex gap-2">
                  <Form.Select
                    value={customerId}
                    onChange={(e) => setCustomerId(e.target.value)}
                    isInvalid={!!fieldErrors.customerId}
                  >
                    <option value="">Walk-in / no customer</option>
                    {customers.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.firstName} {c.lastName} ({c.mobile})
                      </option>
                    ))}
                  </Form.Select>
                  <Button variant="outline-success" onClick={() => setShowCustomerModal(true)}>
                    +
                  </Button>
                </div>
              </Col>
              <Col md={3}>
                <Form.Label>Sale Date</Form.Label>
                <Form.Control
                  type="date"
                  value={saleDate}
                  onChange={(e) => setSaleDate(e.target.value)}
                  isInvalid={!!fieldErrors.saleDate}
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
                  <Form.Label>Sale Status</Form.Label>
                  <Form.Select value={status} onChange={(e) => setStatus(e.target.value as SaleStatus)}>
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
              <Card.Title className="fs-6 mb-0">Sale Items</Card.Title>
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
                  <th style={{ minWidth: 220 }}>Product</th>
                  <th style={{ width: 100 }}>Quantity</th>
                  <th style={{ width: 130 }}>Selling Price</th>
                  <th style={{ width: 110 }}>Discount</th>
                  <th style={{ width: 110 }}>Tax</th>
                  <th className="text-end" style={{ width: 120 }}>
                    Subtotal
                  </th>
                  <th style={{ width: 50 }} />
                </tr>
              </thead>
              <tbody>
                {items.map((row, index) => {
                  const stock = availableStock(row.productId);
                  return (
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
                        {stock !== null && <div className="text-muted small mt-1">Available stock: {stock}</div>}
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
                          value={row.sellingPrice}
                          onChange={(e) => updateItem(index, 'sellingPrice', e.target.value)}
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
                  );
                })}
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
            {submitting ? <Spinner size="sm" animation="border" /> : isEdit ? 'Save Changes' : 'Create Sale'}
          </Button>
          <Button type="button" variant="outline-secondary" onClick={() => navigate('/sales')}>
            Cancel
          </Button>
        </div>
      </Form>

      <CustomerQuickAddModal
        show={showCustomerModal}
        onClose={() => setShowCustomerModal(false)}
        onCreated={(customer) => {
          setCustomers((prev) => [...prev, customer]);
          setCustomerId(String(customer.id));
          setShowCustomerModal(false);
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
