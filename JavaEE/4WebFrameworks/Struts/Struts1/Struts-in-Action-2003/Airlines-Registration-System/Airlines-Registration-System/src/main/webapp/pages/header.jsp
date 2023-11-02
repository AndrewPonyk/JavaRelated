<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="html" uri="http://struts.apache.org/tags-html"%>
<!DOCTYPE html>
<html>
<head>

    <meta charset="UTF-8">
    <link rel="icon" href="<%=request.getContextPath()%>/assets/images/airline-icon2.jpg">
    <link rel="stylesheet"
          href="<%=request.getContextPath()%>/assets/css/style.css">
    <link rel="stylesheet"
          href="<%=request.getContextPath()%>/assets/css/styles2.css">
    <link rel="stylesheet"
          href="https://cdn.jsdelivr.net/npm/bootstrap@4.0.0/dist/css/bootstrap.min.css">
</head>
<body id="page-top">
<!-- Navigation-->

<nav class="navbar navbar-expand-lg navbar-light fixed-top"
     id="mainNav">
    <div class="container px-4 px-lg-5 d-flex justify-content-between">
        <a class=" nav-link " href="<%=request.getContextPath()%>/home.do" >Cross Airlines</a>
        <button class="navbar-toggler navbar-toggler-right" type="button"
                data-bs-toggle="collapse" data-bs-target="#navbarResponsive"
                aria-controls="navbarResponsive" aria-expanded="false"
                aria-label="Toggle navigation">
            Menu <i class="fas fa-bars"></i>
        </button>
        <div class="collapse navbar-collapse" id="navbarResponsive">
            <ul class="navbar-nav ms-auto">
                <li class="nav-item"><a class="nav-link" href="<%=request.getContextPath()%>/home.do" >Home</a></li>
                <li class="nav-item"><a class="nav-link" href="<%=request.getContextPath()%>/about.do" >About</a></li>
                <li class="nav-item"><a class="nav-link" href="<%=request.getContextPath()%>/login.do">Login</a></li>
            </ul>
        </div>
    </div>
</nav>