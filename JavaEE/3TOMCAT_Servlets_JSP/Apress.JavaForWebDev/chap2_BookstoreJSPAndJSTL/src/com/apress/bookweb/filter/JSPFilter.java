package com.apress.bookweb.filter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet Filter implementation class JSPFIlter
 */

public class JSPFilter implements Filter {

    /**
     * Default constructor. 
     */
    public JSPFilter() {
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see Filter#destroy()
	 */
	public void destroy() {
		// TODO Auto-generated method stub
	}

	/**
	 * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
	 */
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		System.out.println("JSP FILTER");
		System.out.println("# " + request.getServletContext().getRealPath("/WEB-INF"));
		request.setAttribute("jspfilter", "1");
		
		HttpServletRequest httpRequest = (HttpServletRequest)request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		
		if(httpRequest.getSession().getAttribute("books") == null){
			httpRequest.getSession().setAttribute("books", new HashMap<String, String>());
		}
		
		// test c:remove tag in jsp;
		request.setAttribute("toDelete", "valueToDeleteRequest");
		httpRequest.getSession().setAttribute("toDelete", "valueToDeleteSession");
		
		String [] array = {"1", "abc", "345", "az"};
		request.setAttribute("array", array);
		
		Map<String, String> map = new HashMap<String, String>();
		map.put("i1", "Value in map 1");
		map.put("i2", "Value in map 2");
		map.put("i3", "Value in map 3");
		request.setAttribute("map", map);
		
		httpResponse.addHeader("intercepted by JSPFilter", "True");
		chain.doFilter(request, response);
	}

	/**
	 * @see Filter#init(FilterConfig)
	 */
	public void init(FilterConfig fConfig) throws ServletException {
	}

}
