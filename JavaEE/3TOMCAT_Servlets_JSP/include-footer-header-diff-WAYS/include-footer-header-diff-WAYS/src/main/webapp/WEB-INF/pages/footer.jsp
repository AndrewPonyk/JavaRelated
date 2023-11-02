<%@ page import="java.time.LocalDateTime" %>
<div style="border: black 1px solid">
    <b>This is footer
        <ul>
            <li>item1</li>
            <li>item2</li>
        </ul>
    </b>
    <%= LocalDateTime.now().toString() %>
    <div>This is some data from request obj: <%= request.getHeader("Accept-Language") %></div>




</div>