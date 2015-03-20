package com.eweware.heardimporter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by ultradad on 3/10/15.
 */
public class ImportAllFeeds extends HttpServlet {

    private static final Logger log = Logger.getLogger(ImportAllFeeds.class.getName());
    private static final String adminUsername = "importer_admin";
    private static final String adminPassword = "uF_4H^_;Dw7=^y?UUU";

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final String serverType = request.getParameter("server");

        try {
            ImportHelper importer = new ImportHelper(serverType, adminUsername, adminPassword);

            if (importer != null) {
                final String resultStr = importer.ImportAllChannels();

                response.setStatus(HttpServletResponse.SC_OK);
                PrintWriter out = response.getWriter();

                out.write(resultStr);
                log.log(Level.INFO, resultStr);
                out.flush();
                out.close();

            }

        } catch (Exception exp) {

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            PrintWriter out = response.getWriter();
            String resultStr = exp.getMessage();
            if (resultStr == null)
                resultStr = exp.toString();
            log.log(Level.SEVERE, resultStr);
            out.write(resultStr);
            out.flush();
            out.close();
            return;

        }
    }
}
