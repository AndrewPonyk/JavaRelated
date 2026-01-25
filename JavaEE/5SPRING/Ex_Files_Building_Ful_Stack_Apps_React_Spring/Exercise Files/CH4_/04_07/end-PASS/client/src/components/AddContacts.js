import React, {useRef} from 'react'; // |su:33 useRef hook - creates mutable ref object that persists across re-renders—c
const AddContacts = () => { // |su:34 Functional component with hooks - modern React approach (vs class components)
    const firstNameRef = useRef(); // |su:35 useRef() returns {current: undefined} - will hold DOM element reference
    const lastNameRef = useRef();
    const emailRef = useRef();

    const submitContact = (event) => {
        event.preventDefault(); // |su:36 Prevents default form submission (page reload)

        let contact = {
            firstName: firstNameRef.current.value, // |su:37 ref.current.value accesses input DOM element's value directly
            lastName: lastNameRef.current.value,
            email: emailRef.current.value,
        }

        fetch("http://localhost:8080/api/contacts", { // |su:38 POST request to backend - creates new contact—c
            method: "POST",
            headers: {
                "content-type": "application/json", // |su:39 Content-Type header tells server to expect JSON body
            },
            body: JSON.stringify(contact), // |su:40 JSON.stringify() converts JS object to JSON string for HTTP body
        })
        .then(response => response.json());
        window.location.reload(); // |su:41 Simple refresh to fetch updated data - production apps would update state instead
    }

    return (
        <div className="row">
            <form className="col s12" onSubmit={submitContact}>
            <div className="row">
                <div className="input-field col s6">
                    <input placeholder="Placeholder1" ref={firstNameRef} type="text" className="validate" /> {/* |su:42 ref={} attaches ref to DOM element */}
                <label htmlFor="firstName">First Name1</label>
                </div>
                <div className="input-field col s6">
                    <input ref={lastNameRef} type="text" className="validate" />
                    <label htmlFor="lastName">Last Name</label>
                </div>
            </div>
            <div className="row">
                <div className="input-field col s12">
                    <input ref={emailRef} type="email" className="validate" />
                    <label htmlFor="email">Email</label>
                </div>
            </div>
            <div className="row">
                <button className="waves-effect waves-light btn" type="submit" name="action">Submit</button>
            </div>
            </form>
        </div>
    )

}

export default AddContacts;
