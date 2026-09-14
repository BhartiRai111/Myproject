export const money = (n: number | null | undefined) =>
  `₹${(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

export const todayIso = () => new Date().toISOString().slice(0, 10);

export const currentReturnPeriod = () => new Date().toISOString().slice(0, 7);

export const firstDayOfMonthIso = () => {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
};
