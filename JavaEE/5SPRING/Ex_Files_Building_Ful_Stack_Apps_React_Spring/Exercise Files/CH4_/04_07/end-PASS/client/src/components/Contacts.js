import React, { Component } from 'react';
import SingleContact from './SingleContact';
import AddContacts from './AddContacts';

export default class Contacts extends Component { // |su:25 Class component (Container pattern) - manages state and fetches data from backend—c
    constructor(props) {
        super(props);
        this.state = { // |su:26 React state initialization - contacts array will trigger re-render when updated—c
            contacts: [],
        };
    }

    componentDidMount() { // |su:27 Lifecycle hook - called once after component mounts, ideal for API calls—c
        fetch('http://localhost:8080/api/contacts') // |su:28 Fetch API call to Spring Boot backend - returns Promise
        .then(response => response.json()) // |su:29 First .then() parses JSON response body
        .then(data => this.setState({contacts: data})) // |su:30 Second .then() updates state - triggers re-render with new data
    }

    render() {
        return (
            <div>
                <div className="row">
                    <AddContacts />
                </div>
                <div className="row">
                    { this.state.contacts.map((item) => ( // |su:31 Array.map() transforms data array into React components
                        <SingleContact key={item.id} item={item} /> // |su:32 key={item.id} required by React for efficient list diffing—c
                    ))}
                </div>
            </div>
        )
    }
}
