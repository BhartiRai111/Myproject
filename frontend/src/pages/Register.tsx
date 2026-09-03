import { FormEvent, useState } from 'react';
import { Alert, Button, Card, Form, Spinner } from 'react-bootstrap';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { parseApiError } from '../utils/apiError';
import { Role } from '../types/user';

const REGISTERABLE_ROLES: { value: Role; label: string }[] = [
  { value: 'STORE_MANAGER', label: 'Store Manager' },
  { value: 'STAFF', label: 'Staff' },
];

export default function Register() {
  const { user, register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    mobile: '',
    password: '',
    confirmPassword: '',
    role: 'STAFF' as Role,
  });
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  const handleChange = (field: keyof typeof form) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});

    if (form.password !== form.confirmPassword) {
      setError('Password and confirm password do not match');
      return;
    }

    setSubmitting(true);
    try {
      await register(form);
      setSuccess(true);
      setTimeout(() => navigate('/login'), 1500);
    } catch (err: any) {
      const parsed = parseApiError(err, 'Registration failed. Please try again.');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="sh-auth-wrapper">
      <Card className="sh-auth-card sh-card p-4">
        <Card.Body>
          <h2 className="text-brand text-center mb-1">StoreHub</h2>
          <p className="text-center text-muted mb-4">Create your account</p>

          {error && <Alert variant="danger">{error}</Alert>}
          {success && <Alert variant="success">Registration successful! Redirecting to login...</Alert>}

          <Form onSubmit={handleSubmit}>
            <div className="d-flex gap-3">
              <Form.Group className="mb-3 flex-fill" controlId="firstName">
                <Form.Label>First Name</Form.Label>
                <Form.Control
                  required
                  value={form.firstName}
                  onChange={handleChange('firstName')}
                  isInvalid={!!fieldErrors.firstName}
                />
                <Form.Control.Feedback type="invalid">{fieldErrors.firstName}</Form.Control.Feedback>
              </Form.Group>
              <Form.Group className="mb-3 flex-fill" controlId="lastName">
                <Form.Label>Last Name</Form.Label>
                <Form.Control
                  required
                  value={form.lastName}
                  onChange={handleChange('lastName')}
                  isInvalid={!!fieldErrors.lastName}
                />
                <Form.Control.Feedback type="invalid">{fieldErrors.lastName}</Form.Control.Feedback>
              </Form.Group>
            </div>

            <Form.Group className="mb-3" controlId="email">
              <Form.Label>Email</Form.Label>
              <Form.Control
                type="email"
                required
                value={form.email}
                onChange={handleChange('email')}
                isInvalid={!!fieldErrors.email}
              />
              <Form.Control.Feedback type="invalid">{fieldErrors.email}</Form.Control.Feedback>
            </Form.Group>

            <Form.Group className="mb-3" controlId="mobile">
              <Form.Label>Mobile</Form.Label>
              <Form.Control
                required
                value={form.mobile}
                onChange={handleChange('mobile')}
                placeholder="10-digit mobile number"
                isInvalid={!!fieldErrors.mobile}
              />
              <Form.Control.Feedback type="invalid">{fieldErrors.mobile}</Form.Control.Feedback>
            </Form.Group>

            <Form.Group className="mb-3" controlId="role">
              <Form.Label>Role</Form.Label>
              <Form.Select value={form.role} onChange={handleChange('role')}>
                {REGISTERABLE_ROLES.map((r) => (
                  <option key={r.value} value={r.value}>
                    {r.label}
                  </option>
                ))}
              </Form.Select>
            </Form.Group>

            <div className="d-flex gap-3">
              <Form.Group className="mb-3 flex-fill" controlId="password">
                <Form.Label>Password</Form.Label>
                <Form.Control
                  type="password"
                  required
                  minLength={6}
                  value={form.password}
                  onChange={handleChange('password')}
                  isInvalid={!!fieldErrors.password}
                />
                <Form.Control.Feedback type="invalid">{fieldErrors.password}</Form.Control.Feedback>
              </Form.Group>
              <Form.Group className="mb-4 flex-fill" controlId="confirmPassword">
                <Form.Label>Confirm Password</Form.Label>
                <Form.Control
                  type="password"
                  required
                  value={form.confirmPassword}
                  onChange={handleChange('confirmPassword')}
                  isInvalid={!!fieldErrors.confirmPassword}
                />
                <Form.Control.Feedback type="invalid">{fieldErrors.confirmPassword}</Form.Control.Feedback>
              </Form.Group>
            </div>

            <Button type="submit" variant="success" className="w-100" disabled={submitting}>
              {submitting ? <Spinner size="sm" animation="border" /> : 'Register'}
            </Button>
          </Form>

          <p className="text-center mt-4 mb-0">
            Already have an account? <Link to="/login">Login</Link>
          </p>
        </Card.Body>
      </Card>
    </div>
  );
}
