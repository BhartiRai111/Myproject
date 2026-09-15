export const money = (n: number | null | undefined) =>
  `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

export const todayIso = () => new Date().toISOString().slice(0, 10);

export const firstDayOfMonthIso = () => {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
};

export const firstDayOfFinancialYearIso = () => {
  const d = new Date();
  const fyStartYear = d.getMonth() >= 3 ? d.getFullYear() : d.getFullYear() - 1;
  return new Date(fyStartYear, 3, 1).toISOString().slice(0, 10);
};
