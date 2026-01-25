import React from 'react';

const SingleContact = ({item}) => ( // |su:43 Presentational component - receives props, renders UI, no state management—c
    <div className="row">
    <div className="col s12 m6">
      <div className="card blue-grey darken-1">
        <div className="card-content white-text">
          <span className="card-title">{item.firstName} {item.lastName}</span> {/* |su:44 JSX {} interpolation renders dynamic data from props */}
        </div>
        <div className="card-action">
          Email:<p>{item.email}</p>
        </div>
      </div>
    </div>
  </div>
);

export default SingleContact; // |su:45 Default export - allows 'import SingleContact from ...' syntax