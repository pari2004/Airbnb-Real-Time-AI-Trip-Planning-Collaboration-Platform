import React from 'react';

export default function BudgetPanel({ budget }) {
  if (!budget) return <div className="card"><h3>Budget</h3><p className="muted">No data yet.</p></div>;
  const over = budget.overBudget;
  return (
    <div className="card">
      <h3>Budget summary</h3>
      <div className="budget-line"><span>Total group budget</span><span>${num(budget.totalBudget)}</span></div>
      <div className="budget-line"><span>Per-person planned</span><span>${num(budget.perPersonPlanned)}</span></div>
      <div className="budget-line"><span>Group planned ({budget.participantCount})</span><span>${num(budget.groupPlanned)}</span></div>
      <div className="budget-line budget-total">
        <span>Remaining</span>
        <span className={over ? 'over' : 'under'}>${num(budget.remaining)}{over ? ' (over!)' : ''}</span>
      </div>
      {budget.lineItems?.length > 0 && (
        <div style={{ marginTop: 10 }}>
          <div className="muted" style={{ fontSize: 12 }}>Running per-person</div>
          {budget.lineItems.map((li) => (
            <div className="budget-line" key={li.itemId}>
              <span>{li.venueName}</span>
              <span className="muted">${num(li.pricePerPerson)} → ${num(li.runningPerPerson)}</span>
            </div>
          ))}
        </div>
      )}
      {budget.evenSplit?.length > 0 && (
        <div style={{ marginTop: 10 }}>
          <div className="muted" style={{ fontSize: 12 }}>Even split</div>
          {budget.evenSplit.map((s) => (
            <div className="budget-line" key={s.userId}><span>{s.displayName}</span><span>${num(s.share)}</span></div>
          ))}
        </div>
      )}
    </div>
  );
}

function num(x) { return x == null ? '0' : Number(x).toFixed(2); }
