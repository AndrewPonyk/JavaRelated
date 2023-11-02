<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<HTML>
<HEAD>
    <TITLE>Welcome!</TITLE>
    <html:base/>
</HEAD>
<BODY>
<logic:present name="user">
    <H3>Welcome <bean:write name="user" property="username"/>!</H3>
</logic:present>
<logic:notPresent scope="session" name="user">
    <H3>Welcome World!</H3>
</logic:notPresent>
<html:errors/>
<UL>
    <LI><html:link forward="logon">Sign in</html:link></LI>
    <logic:present name="user">
        <LI><html:link forward="logoff">Sign out</html:link></LI>
    </logic:present>
</UL>
<IMG src='struts-power.gif' alt='Powered by Struts'>
</BODY>
</HTML>

<%--

If user is logged in, display "Welcome ${username}!"
Else display "Welcome World!"
Display link to log in page; maintain session id if needed.
If user is logged in, display a link to the sign-out page.

Note: Only the minimum required html or Struts custom tags
are used in this example.

--%>