import { Link, useParams } from "react-router-dom";

import { api } from "../api/client";
import { AsyncBlock } from "../components/AsyncBlock";
import { HoldingsTable } from "../components/HoldingsTable";
import { PortfolioDashboard } from "../components/PortfolioDashboard";
import { useAsync, usePortfolioDetail } from "../hooks/usePortfolio";

export function PortfolioDetailPage() {
  const { id } = useParams();
  const portfolioId = Number(id);
  const portfolio = usePortfolioDetail(portfolioId);
  const assets = useAsync(() => api.listAssets(), []);

  return (
    <div>
      <p>
        <Link to="/">← Portfolios</Link>
      </p>
      <AsyncBlock loading={portfolio.loading} error={portfolio.error}>
        {portfolio.data && (
          <>
            <h1>{portfolio.data.name}</h1>
            {portfolio.data.description && <p className="muted">{portfolio.data.description}</p>}

            <section className="panel">
              <h2>Holdings</h2>
              <HoldingsTable
                portfolioId={portfolioId}
                holdings={portfolio.data.holdings}
                assets={assets.data ?? []}
                onChange={portfolio.reload}
              />
            </section>

            <section className="panel">
              <h2>Analytics</h2>
              {portfolio.data.holdings.length > 0 ? (
                <PortfolioDashboard portfolioId={portfolioId} />
              ) : (
                <p className="muted">
                  Add at least one holding (with seeded prices) to see analytics.
                </p>
              )}
            </section>
          </>
        )}
      </AsyncBlock>
    </div>
  );
}
