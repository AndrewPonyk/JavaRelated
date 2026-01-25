import React, { Component } from 'react';

export default class AddContacts extends Component {
    submitContact(event) {
        event.preventDefault();

        let contact = {
            firstName: this.refs.firstName.value,
            lastName: this.refs.lastName.value,
            email: this.refs.email.value,
        }

        fetch("http://localhost:8080/api/contacts", {
            method: "POST",
            headers: {
                "content-type": "application/json",
            },
            body: JSON.stringify(contact),
        })
        .then(response => response.json());

        window.location.reload();
    }


    render() {
        return (
            <div class="row">
                <form class="col s12" onSubmit={this.submitContact.bind(this)}>
                <div class="row">
                    <div class="input-field col s6">
                        <input placeholder="Placeholder" ref="first_name" type="text" class="validate" />
                    <label htmlFor="firstName">First Name</label>
                    </div>
                    <div class="input-field col s6">
                        <input ref="last_name" type="text" class="validate" />
                        <label htmlFor="lastName">Last Name</label>
                    </div>
                </div>
                <div class="row">
                    <div class="input-field col s12">
                        <input ref="email" type="email" class="validate" />
                        <label htmlFor="email">Email</label>
                    </div>
                </div>
                <div class="row">
                    <button className="waves-effect waves-light btn" type="submit" name="action">Submit</button>
                </div>
                </form>
            </div>
        )
    }
}