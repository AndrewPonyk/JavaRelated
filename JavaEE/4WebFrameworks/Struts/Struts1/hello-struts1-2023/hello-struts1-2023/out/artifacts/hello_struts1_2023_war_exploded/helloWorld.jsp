<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<html>
<head>
    <title>Title</title>
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.7.1/jquery.min.js"></script>
</head>
<body>
<b><bean:write name="helloWorldForm" property="outMessage" /></b>
<h1><bean:write name="helloWorldForm" property="greeting" /></h1>

<hr>
<html:form action="/helloWorld">
    <html:hidden name="action" value="save" property="action" />
    <span>Greeting message</span><html:text property="greeting" />
    <br/>

    <span>akaList:</span> <br>
    <input type="hidden" name="akaListSize" value="<c:out value='${fn:length(helloWorldForm.akaList)}' />" />
    <div id="akaListContainer">
    <c:forEach var="entry" items="${helloWorldForm.akaList}" varStatus="status">
        <input type="text" name="akaList[${status.index}].key" value="${entry.key}" />
        <input type="radio" name="akaList[${status.index}].value" value="true" ${entry.value ? 'checked' : ''} /> True
        <input type="radio" name="akaList[${status.index}].value" value="false" ${!entry.value ? 'checked' : ''} /> False
        <br/>

    </c:forEach>
    </div>
    <input id="addAka" value="Add" type="button"/>
    <html:submit value="Submit" />
</html:form>

<script type="application/javascript">
    console.log(123);
    $('#addAka').click(function(){
        var akaListSize = parseInt($('input[name="akaListSize"]').val());
        akaListSize++;
        $('input[name="akaListSize"]').val(akaListSize);
        var html = '<input type="text" name="akaList[' + (akaListSize-1) + '].key" value="" />' +
            '<input type="radio" name="akaList[' + (akaListSize-1) + '].value" value="true" /> True' +
            '<input type="radio" name="akaList[' + (akaListSize-1) + '].value" value="false" /> False' +
            '<br/>';
        $('#akaListContainer').append(html);

        console.log('addAka');
    });

</script>
</body>
</html>
