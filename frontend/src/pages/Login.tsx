import { FormEvent, useState } from 'react';
import { Alert, Button, Card, Form, Spinner } from 'react-bootstrap';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { parseApiError } from '../utils/apiError';

export default function Login() {
  const { user, login } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      await login({ email, password });
      navigate('/dashboard');
    } catch (err: any) {
      const parsed = parseApiError(err, 'Login failed. Please try again.');
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
          <p className="text-center text-muted mb-4">Sign in to your account</p>

          {error && <Alert variant="danger">{error}</Alert>}

          <Form onSubmit={handleSubmit}>
            <Form.Group className="mb-3" controlId="loginEmail">
              <Form.Label>Email</Form.Label>
              <Form.Control
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                isInvalid={!!fieldErrors.email}
              />
              <Form.Control.Feedback type="invalid">{fieldErrors.email}</Form.Control.Feedback>
            </Form.Group>

            <Form.Group className="mb-4" controlId="loginPassword">
              <Form.Label>Password</Form.Label>
              <Form.Control
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Password"
                isInvalid={!!fieldErrors.password}
              />
              <Form.Control.Feedback type="invalid">{fieldErrors.password}</Form.Control.Feedback>
            </Form.Group>

            <Button type="submit" variant="success" className="w-100" disabled={submitting}>
              {submitting ? <Spinner size="sm" animation="border" /> : 'Login'}
            </Button>
          </Form>

          <p className="text-center mt-4 mb-0">
            Don&apos;t have an account? <Link to="/register">Register</Link>
          </p>
        </Card.Body>
      </Card>
    </div>
  );
}
