<%@page isELIgnored="false"  autoFlush="true"%>
<%@taglib tagdir="/WEB-INF/tags" prefix="st" %>
<%@taglib tagdir="/WEB-INF/tags/helloFolder" prefix="hst"%>
<%@taglib uri="http://chap888/tags"  prefix="jt"%>
<html>
<body>
<h2>Hello World!</h2>

<i>Tags must be placed in /WEB-INF/tags/ folder or it subfolders</i>

<st:helloWithAttributes htmlTitle="TITLE of TAG" csvData="data1,data2,data3">

    1222222 + ${10*10}
</st:helloWithAttributes>

<hst:hello/>
<hr>
<jt:simple repeat="10"/>
${pageScope.fromSimple }
</body>
</html>