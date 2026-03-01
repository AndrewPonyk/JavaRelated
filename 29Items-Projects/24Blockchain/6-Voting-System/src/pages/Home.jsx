import React, { useState } from "react";
import ProposalList from "../components/Proposal/ProposalList";
import CreateProposal from "../components/Proposal/CreateProposal";

function Home() {
  const [showCreate, setShowCreate] = useState(false);

  return (
    <div className="home-page">
      <div className="page-header">
        <h1>Governance Proposals</h1>
        <button onClick={() => setShowCreate(!showCreate)} className="btn-primary">
          {showCreate ? "Back to List" : "New Proposal"}
        </button>
      </div>

      {showCreate ? <CreateProposal /> : <ProposalList />}
    </div>
  );
}

export default Home;
