<%--
  Created by IntelliJ IDEA.
  User: ultradad
  Date: 3/10/15
  Time: 10:36 AM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Manual Importer</title>
    <form action="/api/importFeedToChannel" method="post" >
        <label>Feed Name:</label>
        <input type="url" name="feedname" /><br>
        <label>Channel Name:</label>
        <input type="text" name="channel" /><br>
        <label>user name:</label>
        <input type="text" name="username" /><br>
        <label>password:</label>
        <input type="password" name="password" /><br>
        <button type="submit">Submit</button>
    </form>
</head>
<body>

</body>
</html>
