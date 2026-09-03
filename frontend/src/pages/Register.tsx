import { FormEvent, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Eye, EyeOff, Loader2, Store } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { parseApiError } from '../utils/apiError';
import { Role } from '../types/user';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

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
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  const setField = (field: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});

    if (form.password !== form.confirmPassword) {
      setError('Password and confirm password do not match');
      setFieldErrors({ confirmPassword: 'Passwords do not match' });
      return;
    }

    setSubmitting(true);
    try {
      await register(form);
      setSuccess(true);
      toast.success('Registration successful', { description: 'Redirecting you to login...' });
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
    <div className="flex min-h-svh items-center justify-center bg-muted/40 p-6">
      <div className="w-full max-w-lg space-y-6 rounded-2xl border border-border bg-card p-8 shadow-sm">
        <div className="space-y-1 text-center">
          <div className="mb-3 flex justify-center">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <Store className="h-6 w-6" />
            </div>
          </div>
          <h1 className="text-2xl font-bold tracking-tight">Create your account</h1>
          <p className="text-sm text-muted-foreground">Join StoreHub to start managing your store</p>
        </div>

        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        {success && (
          <Alert variant="success">
            <AlertDescription>Registration successful! Redirecting to login...</AlertDescription>
          </Alert>
        )}

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="firstName">First Name</Label>
              <Input
                id="firstName"
                required
                value={form.firstName}
                onChange={setField('firstName')}
                invalid={!!fieldErrors.firstName}
              />
              {fieldErrors.firstName && <p className="text-xs text-destructive">{fieldErrors.firstName}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="lastName">Last Name</Label>
              <Input
                id="lastName"
                required
                value={form.lastName}
                onChange={setField('lastName')}
                invalid={!!fieldErrors.lastName}
              />
              {fieldErrors.lastName && <p className="text-xs text-destructive">{fieldErrors.lastName}</p>}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              required
              value={form.email}
              onChange={setField('email')}
              invalid={!!fieldErrors.email}
            />
            {fieldErrors.email && <p className="text-xs text-destructive">{fieldErrors.email}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="mobile">Mobile</Label>
            <Input
              id="mobile"
              required
              value={form.mobile}
              onChange={setField('mobile')}
              placeholder="10-digit mobile number"
              invalid={!!fieldErrors.mobile}
            />
            {fieldErrors.mobile && <p className="text-xs text-destructive">{fieldErrors.mobile}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="role">Role</Label>
            <Select value={form.role} onValueChange={(value) => setForm((prev) => ({ ...prev, role: value as Role }))}>
              <SelectTrigger id="role">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {REGISTERABLE_ROLES.map((r) => (
                  <SelectItem key={r.value} value={r.value}>
                    {r.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  required
                  minLength={6}
                  value={form.password}
                  onChange={setField('password')}
                  invalid={!!fieldErrors.password}
                  className="pr-9"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((p) => !p)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  tabIndex={-1}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {fieldErrors.password && <p className="text-xs text-destructive">{fieldErrors.password}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">Confirm Password</Label>
              <div className="relative">
                <Input
                  id="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  required
                  value={form.confirmPassword}
                  onChange={setField('confirmPassword')}
                  invalid={!!fieldErrors.confirmPassword}
                  className="pr-9"
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword((p) => !p)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  tabIndex={-1}
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                >
                  {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {fieldErrors.confirmPassword && (
                <p className="text-xs text-destructive">{fieldErrors.confirmPassword}</p>
              )}
            </div>
          </div>

          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
            {submitting ? 'Creating account...' : 'Register'}
          </Button>
        </form>

        <p className="text-center text-sm text-muted-foreground">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Login
          </Link>
        </p>
      </div>
    </div>
  );
}
