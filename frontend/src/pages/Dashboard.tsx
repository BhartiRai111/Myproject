import { Card, Col, Row } from 'react-bootstrap';

interface SummaryCard {
  label: string;
  value: number;
}

const SUMMARY_CARDS: SummaryCard[] = [
  { label: 'Total Products', value: 0 },
  { label: "Today's Sales", value: 0 },
  { label: 'Total Purchases', value: 0 },
  { label: 'Total Customers', value: 0 },
  { label: 'Low Stock Products', value: 0 },
  { label: 'Total Suppliers', value: 0 },
];

export default function Dashboard() {
  return (
    <div>
      <h3 className="mb-4">Dashboard</h3>

      <Row className="g-3 mb-4">
        {SUMMARY_CARDS.map((card) => (
          <Col key={card.label} xs={12} sm={6} lg={4} xl={2}>
            <Card className="sh-card h-100">
              <Card.Body>
                <div className="text-muted small mb-2">{card.label}</div>
                <div className="fs-3 fw-semibold">{card.value}</div>
              </Card.Body>
            </Card>
          </Col>
        ))}
      </Row>

      <Row className="g-3">
        <Col xs={12} lg={6}>
          <Card className="sh-card h-100">
            <Card.Body>
              <Card.Title className="fs-6">Recent Sales</Card.Title>
              <div className="text-muted text-center py-5">
                No sales data available yet.
              </div>
            </Card.Body>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card className="sh-card h-100">
            <Card.Body>
              <Card.Title className="fs-6">Low Stock Products</Card.Title>
              <div className="text-muted text-center py-5">
                No inventory data available yet.
              </div>
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
