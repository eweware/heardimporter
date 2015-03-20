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
        <label>server:</label>
        <select name="server">
            <option value="dev">dev</option>
            <option value="qa">qa</option>
            <option value="prod">prod</option>
        </select><br>
        <label>Channel ID:</label>
        <input type="text" name="channelid" /><br>
        <label>Feed URL:</label>
        <input type="url" name="feed" /><br>
        <label>user name:</label>
        <input type="text" name="username" /><br>
        <label>password:</label>
        <input type="password" name="password" /><br>
        <label>cutoff date:</label>
        <input type="datetime" name="cutoff" /><br>
        <input type="checkbox" name="usesourceimage" />
        <label>use image from source feed instead of parsed page</label><br>
        <button type="submit">Submit</button>
    </form>
</head>
<body>

</body>
</html>
