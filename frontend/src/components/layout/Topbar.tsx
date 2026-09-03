import { Button, Container, Navbar } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

function formatRole(role: string) {
  return role
    .split('_')
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(' ');
}

export default function Topbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <Navbar className="sh-navbar px-3" expand="lg">
      <Container fluid>
        <Navbar.Brand className="sh-brand">StoreHub</Navbar.Brand>
        <div className="d-flex align-items-center gap-3 ms-auto">
          <div className="text-end">
            <div className="fw-semibold">
              {user?.firstName} {user?.lastName}
            </div>
            <div className="text-muted small">{user ? formatRole(user.role) : ''}</div>
          </div>
          <Button variant="outline-success" size="sm" onClick={handleLogout}>
            Logout
          </Button>
        </div>
      </Container>
    </Navbar>
  );
}
