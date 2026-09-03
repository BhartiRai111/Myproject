import { Badge, Modal, Table } from 'react-bootstrap';
import { Purchase } from '../types/purchase';

interface Props {
  show: boolean;
  purchase: Purchase | null;
  onClose: () => void;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

function formatMoney(value: number) {
  return value.toFixed(2);
}

function paymentStatusVariant(status: Purchase['paymentStatus']) {
  if (status === 'PAID') return 'success';
  if (status === 'PARTIAL') return 'warning';
  return 'danger';
}

function purchaseStatusVariant(status: Purchase['status']) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'CANCELLED') return 'danger';
  return 'secondary';
}

export default function PurchaseViewModal({ show, purchase, onClose }: Props) {
  return (
    <Modal show={show} onHide={onClose} centered size="lg">
      <Modal.Header closeButton>
        <Modal.Title>Purchase Details</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {purchase && (
          <>
            <Table borderless size="sm" className="mb-4">
              <tbody>
                <tr>
                  <td className="text-muted" style={{ width: 180 }}>
                    Purchase Number
                  </td>
                  <td className="fw-semibold">{purchase.purchaseNumber}</td>
                  <td className="text-muted" style={{ width: 140 }}>
                    Supplier
                  </td>
                  <td>{purchase.supplier.name}</td>
                </tr>
                <tr>
                  <td className="text-muted">Purchase Date</td>
                  <td>{purchase.purchaseDate}</td>
                  <td className="text-muted">Payment Status</td>
                  <td>
                    <Badge bg={paymentStatusVariant(purchase.paymentStatus)}>{purchase.paymentStatus}</Badge>
                  </td>
                </tr>
                <tr>
                  <td className="text-muted">Purchase Status</td>
                  <td>
                    <Badge bg={purchaseStatusVariant(purchase.status)}>{purchase.status}</Badge>
                  </td>
                  <td className="text-muted">Notes</td>
                  <td>{purchase.notes || '-'}</td>
                </tr>
              </tbody>
            </Table>

            <Table responsive size="sm" bordered>
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="text-end">Quantity</th>
                  <th className="text-end">Purchase Price</th>
                  <th className="text-end">Discount</th>
                  <th className="text-end">Tax</th>
                  <th className="text-end">Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {purchase.items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      {item.product.name}
                      {item.product.unit ? <span className="text-muted"> ({item.product.unit})</span> : null}
                    </td>
                    <td className="text-end">{item.quantity}</td>
                    <td className="text-end">{formatMoney(item.purchasePrice)}</td>
                    <td className="text-end">{formatMoney(item.discount)}</td>
                    <td className="text-end">{formatMoney(item.tax)}</td>
                    <td className="text-end">{formatMoney(item.subtotal)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>

            <div className="d-flex justify-content-end">
              <Table borderless size="sm" className="mb-0" style={{ width: 280 }}>
                <tbody>
                  <tr>
                    <td className="text-muted">Subtotal</td>
                    <td className="text-end">{formatMoney(purchase.subtotalAmount)}</td>
                  </tr>
                  <tr>
                    <td className="text-muted">Total Discount</td>
                    <td className="text-end">-{formatMoney(purchase.totalDiscount)}</td>
                  </tr>
                  <tr>
                    <td className="text-muted">Total Tax</td>
                    <td className="text-end">+{formatMoney(purchase.totalTax)}</td>
                  </tr>
                  <tr>
                    <td className="fw-semibold">Grand Total</td>
                    <td className="text-end fw-semibold">{formatMoney(purchase.totalAmount)}</td>
                  </tr>
                </tbody>
              </Table>
            </div>

            <hr />

            <div className="d-flex justify-content-between text-muted small">
              <span>Created At: {formatDate(purchase.createdAt)}</span>
              <span>Updated At: {formatDate(purchase.updatedAt)}</span>
            </div>
          </>
        )}
      </Modal.Body>
    </Modal>
  );
}
