<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<!DOCTYPE html>
<html>
<head>
    <title>Dashboard - LegacyBridge Auth Service</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: Arial, Helvetica, sans-serif;
            background-color: #ecf0f1;
        }
        .header {
            background-color: #2c3e50;
            color: white;
            padding: 15px 20px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .header h1 {
            font-size: 1.4em;
        }
        .header .user-info {
            font-size: 0.9em;
            color: #bdc3c7;
        }
        .header a {
            color: #3498db;
            text-decoration: none;
            margin-left: 15px;
        }
        .header a:hover {
            text-decoration: underline;
        }
        .container {
            max-width: 900px;
            margin: 30px auto;
            padding: 0 20px;
        }
        .welcome-card {
            background: white;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
            padding: 30px;
            margin-bottom: 20px;
        }
        .welcome-card h2 {
            color: #2c3e50;
            margin-bottom: 10px;
        }
        .welcome-card p {
            color: #7f8c8d;
        }
        .nav-links {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 15px;
            margin-top: 20px;
        }
        .nav-card {
            background: white;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
            padding: 20px;
            text-decoration: none;
            color: #2c3e50;
            transition: transform 0.2s, box-shadow 0.2s;
        }
        .nav-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
        }
        .nav-card h3 {
            margin-bottom: 8px;
        }
        .nav-card p {
            color: #7f8c8d;
            font-size: 0.9em;
        }
    </style>
</head>
<body>

<div class="header">
    <h1>LegacyBridge Auth Service</h1>
    <div class="user-info">
        Logged in as: <strong><sec:authentication property="name" /></strong>
        <a href="${pageContext.request.contextPath}/perform_logout">Logout</a>
    </div>
</div>

<div class="container">
    <div class="welcome-card">
        <h2>Welcome, <sec:authentication property="name" />!</h2>
        <p>You are authenticated with the LegacyBridge Authentication Service.
           Use the navigation below to access system components.</p>
    </div>

    <div class="nav-links">
        <a href="/docmgr/" class="nav-card">
            <h3>Document Manager</h3>
            <p>Access the JSF + PrimeFaces document management UI to upload, search, and manage documents.</p>
        </a>

        <a href="/api/health" class="nav-card">
            <h3>REST API Health</h3>
            <p>Check the status of the JAX-RS REST API service.</p>
        </a>

        <a href="${pageContext.request.contextPath}/api/user" class="nav-card">
            <h3>My Profile</h3>
            <p>View your authentication details and token information.</p>
        </a>

        <sec:authorize access="hasRole('ADMIN')">
            <a href="${pageContext.request.contextPath}/api/tokens/generate" class="nav-card">
                <h3>Token Management</h3>
                <p>Generate and manage authentication tokens (Admin only).</p>
            </a>
        </sec:authorize>
    </div>
</div>

</body>
</html>
