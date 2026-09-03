import { useEffect, useState } from 'react';
import { Alert, Button, Form, Modal } from 'react-bootstrap';
import { ApiErrorResponse, Role, User, UserStatus } from '../types/user';

export interface UserFormValues {
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
  password: string;
  role: Role;
  status: UserStatus;
}

interface Props {
  show: boolean;
  mode: 'add' | 'edit';
  initialUser?: User | null;
  onClose: () => void;
  onSubmit: (values: UserFormValues) => Promise<void>;
}

const EMPTY_FORM: UserFormValues = {
  firstName: '',
  lastName: '',
  email: '',
  mobile: '',
  password: '',
  role: 'STAFF',
  status: 'ACTIVE',
};

export default function UserFormModal({ show, mode, initialUser, onClose, onSubmit }: Props) {
  const [form, setForm] = useState<UserFormValues>(EMPTY_FORM);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (show) {
      setError('');
      if (mode === 'edit' && initialUser) {
        setForm({
          firstName: initialUser.firstName,
          lastName: initialUser.lastName,
          email: initialUser.email,
          mobile: initialUser.mobile,
          password: '',
          role: initialUser.role,
          status: initialUser.status,
        });
      } else {
        setForm(EMPTY_FORM);
      }
    }
  }, [show, mode, initialUser]);

  const handleChange = (field: keyof UserFormValues) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await onSubmit(form);
    } catch (err: any) {
      const apiError: ApiErrorResponse | undefined = err.response?.data;
      setError(apiError?.message || 'Something went wrong. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal show={show} onHide={onClose} centered>
      <Form onSubmit={handleSubmit}>
        <Modal.Header closeButton>
          <Modal.Title>{mode === 'add' ? 'Add User' : 'Edit User'}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {error && <Alert variant="danger">{error}</Alert>}

          <div className="d-flex gap-3">
            <Form.Group className="mb-3 flex-fill">
              <Form.Label>First Name</Form.Label>
              <Form.Control required value={form.firstName} onChange={handleChange('firstName')} />
            </Form.Group>
            <Form.Group className="mb-3 flex-fill">
              <Form.Label>Last Name</Form.Label>
              <Form.Control required value={form.lastName} onChange={handleChange('lastName')} />
            </Form.Group>
          </div>

          <Form.Group className="mb-3">
            <Form.Label>Email</Form.Label>
            <Form.Control type="email" required value={form.email} onChange={handleChange('email')} />
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Mobile</Form.Label>
            <Form.Control required value={form.mobile} onChange={handleChange('mobile')} />
          </Form.Group>

          {mode === 'add' && (
            <Form.Group className="mb-3">
              <Form.Label>Password</Form.Label>
              <Form.Control
                type="password"
                required
                minLength={6}
                value={form.password}
                onChange={handleChange('password')}
              />
            </Form.Group>
          )}

          <div className="d-flex gap-3">
            <Form.Group className="mb-3 flex-fill">
              <Form.Label>Role</Form.Label>
              <Form.Select value={form.role} onChange={handleChange('role')}>
                <option value="ADMIN">Admin</option>
                <option value="STORE_MANAGER">Store Manager</option>
                <option value="STAFF">Staff</option>
              </Form.Select>
            </Form.Group>

            <Form.Group className="mb-3 flex-fill">
              <Form.Label>Status</Form.Label>
              <Form.Select value={form.status} onChange={handleChange('status')}>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </Form.Select>
            </Form.Group>
          </div>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button variant="success" type="submit" disabled={submitting}>
            {mode === 'add' ? 'Create User' : 'Save Changes'}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}
