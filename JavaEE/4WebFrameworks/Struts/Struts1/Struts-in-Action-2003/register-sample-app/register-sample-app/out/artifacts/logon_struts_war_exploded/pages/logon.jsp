<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<HTML>
<HEAD>
    <TITLE>Sign in, Please!</TITLE>
</HEAD>
<BODY>
<html:errors/>
<!--NOT just show login errors but also SHOW alert !!!-->
<%
    org.apache.struts.action.ActionErrors errors = (org.apache.struts.action.ActionErrors) request.getAttribute(org.apache.struts.Globals.ERROR_KEY);
    String message = "";

    if(errors != null) {
        java.util.Iterator it = errors.get();
        while (it.hasNext()) {
            org.apache.struts.action.ActionMessage error = (org.apache.struts.action.ActionMessage) it.next();
            message += error.getKey();  // get the error message from the key
            message += ",";            // add a new line for multiple errors
        }
    }
%>

<script type="text/javascript">
    var errorMessage = "<%= message %> ";

    if (errorMessage.trim() !== "") {
        console.log(errorMessage+"1");
        alert(errorMessage);
    }
</script>
<html:form action="/LogonSubmit" focus="username">
    <TABLE border="0" width="100%">
        <TR>
            <TH align="right">Username:</TH>
            <TD align="left"><html:text property="username"/></TD>
        </TR>
        <TR>
            <TH align="right">Password:</TH>
            <TD align="left"><html:password property="password"/></TD>
        </TR>
        <TR>
            <TD align="right"><html:submit/></TD>
            <TD align="left"><html:reset/></TD>
        </TR>
    </TABLE>
</html:form>
</BODY>
</HTML>

<%--

Allow user to submit username and password to logon action.

--%>