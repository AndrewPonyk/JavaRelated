<html>
<body>
<h2>Hello World!</h2>
<h1>Upload a File</h1>
<b>In this example threre are two ways of saving files
1) Using FormDataParam
    2)Using FormDataMultiPart
</b>
<form action="rest/helloWorldREST/upload2Version" method="post" enctype="multipart/form-data">

    <p>
        Select a file : <input type="file" name="file" size="50" />
    </p>

    <input type="submit" value="Upload It" />
</form>

</body>
</html>
