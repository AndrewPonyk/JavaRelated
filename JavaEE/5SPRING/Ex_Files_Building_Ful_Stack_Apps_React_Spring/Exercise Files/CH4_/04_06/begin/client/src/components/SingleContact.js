import React from 'react';

const SingleContact = ({item}) => (
    <div class="row">
    <div class="col s12 m6">
      <div class="card blue-grey darken-1">
        <div class="card-content white-text">
          <span class="card-title">{item.firstName} {item.lastName}</span>
        </div>
        <div class="card-action">
          <p>{item.email}</p>
        </div>
      </div>
    </div>
  </div>
);

export default SingleContact;