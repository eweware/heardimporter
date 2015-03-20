package com.eweware.heardimporter;


import com.sun.org.apache.xpath.internal.operations.Bool;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Created by ultradad on 3/10/15.
 */
public class ImportFeedToChannel extends HttpServlet {
    private static final Logger log = Logger.getLogger(ImportFeedToChannel.class.getName());
    private static final String adminUsername = "importer_admin";
    private static final String adminPassword = "uF_4H^_;Dw7=^y?UUU";

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        try {
            final String serverType = request.getParameter("server");
            final String channelId = request.getParameter("channelid");
            final String feedStr = request.getParameter("feed");
            final String username = request.getParameter("username");
            final String password = request.getParameter("password");
            final String cutoffDateStr = request.getParameter("cutoff");
            final String useSourceImageStr = request.getParameter("usesourceimage");
            Date cutoffDate = null;
            Boolean useSourceImage = false;

            String errString = null;

            if ((channelId == null) || channelId.isEmpty())
                errString = "missing channel";
            if ((feedStr == null) || feedStr.isEmpty())
                errString = "missing feed url";
            if ((username == null) || username.isEmpty())
                errString = "missing user name";
            if ((password == null) || password.isEmpty())
                errString = "missing password";
            if ((cutoffDateStr != null) && !cutoffDateStr.isEmpty()) {
                // parse the date
                SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
                cutoffDate = dateFormat.parse(cutoffDateStr);
            }

            if ((useSourceImageStr != null) && !useSourceImageStr.isEmpty()) {
                // parse the boolean
                useSourceImage = Boolean.getBoolean(useSourceImageStr);

            }

            if (errString == null) {
                ImportHelper importer = new ImportHelper(serverType, adminUsername, adminPassword);
                final String resultStr = importer.ImportSingleFeedToChannel(channelId, feedStr, username, password, cutoffDate, useSourceImage);

                response.setStatus(HttpServletResponse.SC_OK);
                PrintWriter out = response.getWriter();

                out.write(resultStr);
                log.log(Level.INFO, resultStr);
                out.flush();
                out.close();
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                PrintWriter out = response.getWriter();
                log.log(Level.SEVERE, errString);
                out.write(errString);
                out.flush();
                out.close();
                return;
            }

        } catch (Exception exp) {

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            PrintWriter out = response.getWriter();
            String resultStr = exp.getMessage();
            log.log(Level.SEVERE, resultStr);
            out.write(resultStr);
            out.flush();
            out.close();
            return;

        }
    }




    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }
}
