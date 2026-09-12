import PolicyList from './components/PolicyList'

function App() {
  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
      <header style={{ padding: '20px', backgroundColor: '#2c3e50', color: 'white', marginBottom: '20px' }}>
        <h1>Enterprise Insurance Portal</h1>
      </header>
      <main>
        <PolicyList />
      </main>
    </div>
  )
}

export default App
