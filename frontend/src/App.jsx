import { useState } from 'react'
import { reviewPullRequest } from './api'

const RISK_CLASS = {
  LOW: 'risk-low',
  MEDIUM: 'risk-medium',
  HIGH: 'risk-high',
}

function RiskBadge({ level }) {
  const normalized = (level || 'MEDIUM').toUpperCase()
  return <span className={`badge ${RISK_CLASS[normalized] || 'risk-medium'}`}>{normalized}</span>
}

export default function App() {
  const [prUrl, setPrUrl] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)

  async function handleSubmit(e) {
    e.preventDefault()
    if (!prUrl.trim()) return

    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const data = await reviewPullRequest(prUrl.trim())
      setResult(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="app">
      <header>
        <h1>AI Pull Request Reviewer</h1>
        <p className="subtitle">Paste a GitHub PR URL to get an AI-generated review.</p>
      </header>

      <form onSubmit={handleSubmit} className="review-form">
        <input
          type="text"
          value={prUrl}
          onChange={(e) => setPrUrl(e.target.value)}
          placeholder="https://github.com/owner/repo/pull/123"
          disabled={loading}
        />
        <button type="submit" disabled={loading || !prUrl.trim()}>
          {loading ? 'Reviewing…' : 'Review PR'}
        </button>
      </form>

      {error && <div className="error">{error}</div>}

      {result && (
        <section className="result">
          <div className="result-header">
            <h2>Review Summary</h2>
            <RiskBadge level={result.riskLevel} />
          </div>
          <p className="summary">{result.summary}</p>

          <h3>Findings ({result.findings?.length || 0})</h3>
          {result.findings && result.findings.length > 0 ? (
            <ul className="findings">
              {result.findings.map((f, idx) => (
                <li key={idx} className="finding">
                  <div className="finding-meta">
                    <RiskBadge level={f.severity} />
                    <span className="finding-location">
                      {f.file}
                      {f.line ? `:${f.line}` : ''}
                    </span>
                  </div>
                  <p>{f.comment}</p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="no-findings">No issues found.</p>
          )}
        </section>
      )}
    </div>
  )
}
