package com.eweware.heardimporter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by ultradad on 3/10/15.
 */
public class ImportFeedToChannel extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final String feedName = request.getParameter("feedname");
        final String channelName = request.getParameter("channel");
        final String username = request.getParameter("username");
        final String password = request.getParameter("password");




    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }
}
