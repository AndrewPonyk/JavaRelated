package com.ap.filter;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Date;

/**
 * Created by andrii on 27.06.17.
 */
@WebFilter("*.do")
public class LoginRequiredFilter implements Filter {
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        System.out.println("LoginRequiredFilter: " + new Date());
        if (request.getSession().getAttribute("name") != null) {
            chain.doFilter(servletRequest, servletResponse);
        } else {
            request.getRequestDispatcher("login.do").forward(servletRequest,
                    servletResponse);
        }
    }

    public void destroy() {

    }
}
