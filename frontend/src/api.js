const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export async function reviewPullRequest(prUrl) {
  const response = await fetch(`${API_BASE_URL}/api/review`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prUrl }),
  })

  const data = await response.json().catch(() => null)

  if (!response.ok) {
    const message = data?.message || `Request failed with status ${response.status}`
    throw new Error(message)
  }

  return data
}
