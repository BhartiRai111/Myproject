import { useEffect, useState } from 'react';
import { Badge, Button, Card, Col, Form, Pagination, Row, Spinner, Table } from 'react-bootstrap';
import { userApi } from '../api/userApi';
import UserFormModal, { UserFormValues } from '../components/UserFormModal';
import UserViewModal from '../components/UserViewModal';
import { Role, User, UserStatus } from '../types/user';

const PAGE_SIZE = 10;

export default function Users() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<Role | ''>('');
  const [statusFilter, setStatusFilter] = useState<UserStatus | ''>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [formModal, setFormModal] = useState<{ show: boolean; mode: 'add' | 'edit'; user: User | null }>({
    show: false,
    mode: 'add',
    user: null,
  });
  const [viewUser, setViewUser] = useState<User | null>(null);

  const loadUsers = async () => {
    setLoading(true);
    try {
      const res = await userApi.list({ search, role: roleFilter, status: statusFilter, page, size: PAGE_SIZE });
      setUsers(res.data.content);
      setTotalPages(res.data.totalPages);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, roleFilter, statusFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadUsers();
  };

  const openAddModal = () => setFormModal({ show: true, mode: 'add', user: null });
  const openEditModal = (user: User) => setFormModal({ show: true, mode: 'edit', user });
  const closeFormModal = () => setFormModal((prev) => ({ ...prev, show: false }));

  const handleFormSubmit = async (values: UserFormValues) => {
    if (formModal.mode === 'add') {
      await userApi.create({
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        mobile: values.mobile,
        password: values.password,
        role: values.role,
        status: values.status,
      });
    } else if (formModal.user) {
      await userApi.update(formModal.user.id, {
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        mobile: values.mobile,
        role: values.role,
        status: values.status,
      });
    }
    closeFormModal();
    loadUsers();
  };

  const toggleStatus = async (user: User) => {
    const newStatus: UserStatus = user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    await userApi.updateStatus(user.id, newStatus);
    loadUsers();
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h3 className="mb-0">User Management</h3>
        <Button variant="success" onClick={openAddModal}>
          Add User
        </Button>
      </div>

      <Card className="sh-card mb-3">
        <Card.Body>
          <Form onSubmit={handleSearchSubmit}>
            <Row className="g-3 align-items-end">
              <Col md={5}>
                <Form.Label>Search</Form.Label>
                <Form.Control
                  placeholder="Search by name or email"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </Col>
              <Col md={3}>
                <Form.Label>Role</Form.Label>
                <Form.Select value={roleFilter} onChange={(e) => { setPage(0); setRoleFilter(e.target.value as Role | ''); }}>
                  <option value="">All Roles</option>
                  <option value="ADMIN">Admin</option>
                  <option value="STORE_MANAGER">Store Manager</option>
                  <option value="STAFF">Staff</option>
                </Form.Select>
              </Col>
              <Col md={2}>
                <Form.Label>Status</Form.Label>
                <Form.Select value={statusFilter} onChange={(e) => { setPage(0); setStatusFilter(e.target.value as UserStatus | ''); }}>
                  <option value="">All</option>
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </Form.Select>
              </Col>
              <Col md={2}>
                <Button type="submit" variant="outline-success" className="w-100">
                  Search
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
          ) : users.length === 0 ? (
            <div className="text-center text-muted py-5">No users found.</div>
          ) : (
            <>
              <Table hover responsive>
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Mobile</th>
                    <th>Role</th>
                    <th>Status</th>
                    <th>Created At</th>
                    <th className="text-end">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.id}>
                      <td>
                        {u.firstName} {u.lastName}
                      </td>
                      <td>{u.email}</td>
                      <td>{u.mobile}</td>
                      <td>{u.role}</td>
                      <td>
                        <Badge bg={u.status === 'ACTIVE' ? 'success' : 'secondary'}>{u.status}</Badge>
                      </td>
                      <td>{new Date(u.createdAt).toLocaleDateString()}</td>
                      <td className="text-end">
                        <Button size="sm" variant="outline-secondary" className="me-2" onClick={() => setViewUser(u)}>
                          View
                        </Button>
                        <Button size="sm" variant="outline-primary" className="me-2" onClick={() => openEditModal(u)}>
                          Edit
                        </Button>
                        <Button
                          size="sm"
                          variant={u.status === 'ACTIVE' ? 'outline-danger' : 'outline-success'}
                          onClick={() => toggleStatus(u)}
                        >
                          {u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                        </Button>
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

      <UserFormModal
        show={formModal.show}
        mode={formModal.mode}
        initialUser={formModal.user}
        onClose={closeFormModal}
        onSubmit={handleFormSubmit}
      />

      <UserViewModal show={!!viewUser} user={viewUser} onClose={() => setViewUser(null)} />
    </div>
  );
}
