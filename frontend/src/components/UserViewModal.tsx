import { Badge, Modal, Table } from 'react-bootstrap';
import { User } from '../types/user';

interface Props {
  show: boolean;
  user: User | null;
  onClose: () => void;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

export default function UserViewModal({ show, user, onClose }: Props) {
  return (
    <Modal show={show} onHide={onClose} centered>
      <Modal.Header closeButton>
        <Modal.Title>User Details</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {user && (
          <Table borderless size="sm" className="mb-0">
            <tbody>
              <tr>
                <td className="text-muted">Full Name</td>
                <td className="fw-semibold">
                  {user.firstName} {user.lastName}
                </td>
              </tr>
              <tr>
                <td className="text-muted">Email</td>
                <td>{user.email}</td>
              </tr>
              <tr>
                <td className="text-muted">Mobile</td>
                <td>{user.mobile}</td>
              </tr>
              <tr>
                <td className="text-muted">Role</td>
                <td>{user.role}</td>
              </tr>
              <tr>
                <td className="text-muted">Status</td>
                <td>
                  <Badge bg={user.status === 'ACTIVE' ? 'success' : 'secondary'}>{user.status}</Badge>
                </td>
              </tr>
              <tr>
                <td className="text-muted">Created At</td>
                <td>{formatDate(user.createdAt)}</td>
              </tr>
              <tr>
                <td className="text-muted">Updated At</td>
                <td>{formatDate(user.updatedAt)}</td>
              </tr>
            </tbody>
          </Table>
        )}
      </Modal.Body>
    </Modal>
  );
}
