const toneClasses = {
  success: "border-emerald-200 bg-emerald-50 text-emerald-800",
  neutral: "border-slate-200 bg-slate-50 text-slate-700",
  warning: "border-amber-200 bg-amber-50 text-amber-800",
};

const numberFormatter = new Intl.NumberFormat("en-US");

const statusByOutcome = {
  AWARDED: (result) => ({
    title: `${numberFormatter.format(result.pointsAwarded)} points credited`,
    description: "Your rent payment was processed successfully.",
    tone: "success",
  }),
  DUPLICATE: () => ({
    title: "Duplicate event skipped",
    description:
      "This payment was already processed, so no additional points were credited.",
    tone: "neutral",
  }),
  CAPPED: (result, member) => ({
    title: "Monthly cap reached",
    description: `You've earned the maximum of ${numberFormatter.format(member.monthlyCap)} points this month, so this payment didn't earn additional points.`,
    tone: "warning",
  }),
};

// Never announce a credit for an outcome the dashboard does not recognize.
const unknownStatus = {
  title: "Payment status unavailable",
  description: "We couldn't confirm the points for this payment yet.",
  tone: "neutral",
};

export function buildViewModel(result, member) {
  const progressPercent = Math.min(
    100,
    (member.pointsThisMonth / member.monthlyCap) * 100,
  );
  const status = statusByOutcome[result.outcome]?.(result, member) ?? unknownStatus;

  return {
    ...status,
    progressPercent,
  };
}

export function renderDashboard(result, member) {
  const view = buildViewModel(result, member);

  document.querySelector("[data-status]").className =
    `rounded-2xl border p-5 ${toneClasses[view.tone]}`;
  document.querySelector("[data-status-title]").textContent = view.title;
  document.querySelector("[data-status-description]").textContent =
    view.description;
  document.querySelector("[data-points]").textContent = numberFormatter.format(
    member.pointsThisMonth,
  );
  document.querySelector("[data-streak]").textContent =
    `${member.streakMonths} month streak`;
  document.querySelector("[data-progress]").style.width =
    `${view.progressPercent}%`;
  document.querySelector("[data-progress-label]").textContent =
    `${Math.round(view.progressPercent)}% of monthly cap`;
}
