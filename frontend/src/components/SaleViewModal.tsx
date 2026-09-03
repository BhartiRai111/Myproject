import { Badge, Modal, Table } from 'react-bootstrap';
import { Sale } from '../types/sale';

interface Props {
  show: boolean;
  sale: Sale | null;
  onClose: () => void;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

function formatMoney(value: number) {
  return value.toFixed(2);
}

function paymentStatusVariant(status: Sale['paymentStatus']) {
  if (status === 'PAID') return 'success';
  if (status === 'PARTIAL') return 'warning';
  return 'danger';
}

function saleStatusVariant(status: Sale['status']) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'CANCELLED') return 'danger';
  return 'secondary';
}

export default function SaleViewModal({ show, sale, onClose }: Props) {
  return (
    <Modal show={show} onHide={onClose} centered size="lg">
      <Modal.Header closeButton>
        <Modal.Title>Sale Details</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {sale && (
          <>
            <Table borderless size="sm" className="mb-4">
              <tbody>
                <tr>
                  <td className="text-muted" style={{ width: 180 }}>
                    Invoice Number
                  </td>
                  <td className="fw-semibold">{sale.invoiceNumber}</td>
                  <td className="text-muted" style={{ width: 140 }}>
                    Customer
                  </td>
                  <td>{sale.customer ? `${sale.customer.firstName} ${sale.customer.lastName || ''}` : 'Walk-in customer'}</td>
                </tr>
                <tr>
                  <td className="text-muted">Sale Date</td>
                  <td>{sale.saleDate}</td>
                  <td className="text-muted">Payment Status</td>
                  <td>
                    <Badge bg={paymentStatusVariant(sale.paymentStatus)}>{sale.paymentStatus}</Badge>
                  </td>
                </tr>
                <tr>
                  <td className="text-muted">Sale Status</td>
                  <td>
                    <Badge bg={saleStatusVariant(sale.status)}>{sale.status}</Badge>
                  </td>
                  <td className="text-muted">Notes</td>
                  <td>{sale.notes || '-'}</td>
                </tr>
              </tbody>
            </Table>

            <Table responsive size="sm" bordered>
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="text-end">Quantity</th>
                  <th className="text-end">Selling Price</th>
                  <th className="text-end">Discount</th>
                  <th className="text-end">Tax</th>
                  <th className="text-end">Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {sale.items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      {item.product.name}
                      {item.product.unit ? <span className="text-muted"> ({item.product.unit})</span> : null}
                    </td>
                    <td className="text-end">{item.quantity}</td>
                    <td className="text-end">{formatMoney(item.sellingPrice)}</td>
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
                    <td className="text-end">{formatMoney(sale.subtotalAmount)}</td>
                  </tr>
                  <tr>
                    <td className="text-muted">Total Discount</td>
                    <td className="text-end">-{formatMoney(sale.totalDiscount)}</td>
                  </tr>
                  <tr>
                    <td className="text-muted">Total Tax</td>
                    <td className="text-end">+{formatMoney(sale.totalTax)}</td>
                  </tr>
                  <tr>
                    <td className="fw-semibold">Grand Total</td>
                    <td className="text-end fw-semibold">{formatMoney(sale.totalAmount)}</td>
                  </tr>
                </tbody>
              </Table>
            </div>

            <hr />

            <div className="d-flex justify-content-between text-muted small">
              <span>Created At: {formatDate(sale.createdAt)}</span>
              <span>Updated At: {formatDate(sale.updatedAt)}</span>
            </div>
          </>
        )}
      </Modal.Body>
    </Modal>
  );
}
