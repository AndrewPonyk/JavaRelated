import React from "react";
import DelegateVote from "../components/Delegation/DelegateVote";

function DelegationPage() {
  return (
    <div className="delegation-page">
      <h1>Vote Delegation</h1>
      <p>Delegate your voting power to a trusted representative, or manage delegations you've received.</p>
      <DelegateVote />
    </div>
  );
}

export default DelegationPage;
