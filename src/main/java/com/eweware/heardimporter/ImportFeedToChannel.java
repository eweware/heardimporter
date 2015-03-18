package com.eweware.heardimporter;

import com.eweware.feedimport.*;
import com.eweware.heard.*;

import com.google.appengine.api.urlfetch.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by ultradad on 3/10/15.
 */
public class ImportFeedToChannel extends HttpServlet {
    private static final Logger log = Logger.getLogger(ImportFeedToChannel.class.getName());
    private final String qaServerURL = "http://qa.rest.goheard.com:8080/v2/";
    private final String prodServerURL = "http://app.goheard.com/v2/";
    private final String devServerURL = "http://localhost:8090/v2/";
    private String HeardServerURL;
    private String blahTypeSays = null;
    private final String adminUsername = "importer_admin";
    private final String adminPassword = "uF_4H^_;Dw7=^y?UUU";
    private static Boolean isImporting = false;
    private static Integer totalNewItems = 0;
    private static Integer totalExistingItems = 0;
    private static String sessionCookie = "";

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final String serverType = request.getParameter("server");
        if (serverType.equalsIgnoreCase("prod"))
            HeardServerURL = prodServerURL;
        else if (serverType.equalsIgnoreCase("qa"))
            HeardServerURL = qaServerURL;
        else if (serverType.equalsIgnoreCase("dev"))
            HeardServerURL = devServerURL;
        else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            PrintWriter out = response.getWriter();
            String resultStr = "invalid or missing server name";
            log.log(Level.SEVERE, "invalid or missing server name", serverType);
            out.write(resultStr);
            log.log(Level.INFO, resultStr);
            out.flush();
            out.close();
            return;
        }
        if (!isImporting) {
            try {
                isImporting = true;
                totalNewItems = 0;
                totalExistingItems = 0;
                if (SignInToHeard(adminUsername, adminPassword)) {
                    if (blahTypeSays == null) {
                        blahTypeSays = GetBlahTypeByName("says");
                    }

                    List<Channel> channelList = GetAllChannels();



                    if (channelList != null) {
                        importChannels(channelList);
                    }

                    SignOutOfHeard();
                }

                response.setStatus(HttpServletResponse.SC_OK);
                PrintWriter out = response.getWriter();
                String resultStr = "Imported " + totalNewItems + " items, skipped " + totalExistingItems + " existing items";
                out.write(resultStr);
                log.log(Level.INFO, resultStr);
                out.flush();
                out.close();
            } catch (Exception exp) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                PrintWriter out = response.getWriter();
                out.write(exp.getMessage());
                out.flush();
                out.close();
                log.log(Level.SEVERE, exp.toString(), exp);
            } finally {
                isImporting = false;
            }

        } else {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            log.log(Level.WARNING, "Importer tried to run while it was already running");
            PrintWriter out = response.getWriter();
            out.write("Import already running");
            out.flush();
            out.close();
        }

    }

    private void importChannels(List<Channel> channelList) {
        try {
            for (Channel curChannel : channelList) {
                log.log(Level.INFO, "importing channel " + curChannel.N);
                importChannel(curChannel);
            }
        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }
    }

    private void importChannel(Channel theChannel) {
        try {
            List<ImportRecord>  importRecords = GetImportRecords(theChannel._id);

            if ((importRecords != null) && (importRecords.size() > 0)) {
                log.log(Level.INFO,"  found " + importRecords.size() + " feeds");
                for (ImportRecord curRec : importRecords) {
                    processImportRecord(curRec);
                }
            } else {
                log.log(Level.INFO, "channel " + theChannel.N + " has no feeds defined");
            }
        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }
    }

    private void processImportRecord(ImportRecord theRecord) {
        try {
            if ((theRecord.autoimport != null) && theRecord.autoimport) {
                String userName = theRecord.importusername;
                String password = theRecord.importpassword;

                if ((!userName.isEmpty()) && (!password.isEmpty())) {
                    if (theRecord.feedtype == 0) {
                        processRSSImportRecord(theRecord);
                    }
                }
            }
        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }
    }

    private void processRSSImportRecord(ImportRecord theRecord) {
        try {
            Date cutoffDate = theRecord.getLastImportDate();
            importRssFeedToChannel(theRecord.channel, theRecord.RSSurl, theRecord.importusername, theRecord.importpassword, cutoffDate);
            UpdateLastImportDate(theRecord);
        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }
    }

    private void UpdateLastImportDate(ImportRecord theRecord) {

        if (SignInToHeard(adminUsername, adminPassword)) {
            try {
                String putURL = HeardServerURL + "groups/importers/" + theRecord._id;
                ImporterUpdateRecord updateRec = new ImporterUpdateRecord();
                updateRec._id = theRecord._id;
                updateRec.channel = theRecord.channel;
                updateRec.lastimport = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).format(new Date());
                String jsonContent = new Gson().toJson(updateRec, ImporterUpdateRecord.class);

                sendJsonPutRequest(putURL, jsonContent, true);
            } catch (Exception exp) {
                log.log(Level.SEVERE, exp.toString(), exp);
            } finally {
                SignOutOfHeard();
            }
        }
    }

    private void importRssFeedToChannel(String channelId, String feedStr, String username, String password, Date cutoffDate) {

        try {
            String googleParserURL = "http://ajax.googleapis.com/ajax/services/feed/load?v=1.0&num=100&q=";
            String finalURL = googleParserURL + URLEncoder.encode(feedStr,"UTF-8");

            if (!SignInToHeard(username, password)) {
                return;
            }

            URLFetchService fetchService = URLFetchServiceFactory.getURLFetchService();

            HTTPResponse fetchResponse = fetchService.fetch(new URL(finalURL));
            byte[] theData = fetchResponse.getContent();
            String theJSON = new String (theData, "UTF-8");

            Gson gson = new Gson();
            Response feedResponse = gson.fromJson(theJSON, Response.class);

            if ((feedResponse != null) && (feedResponse.responseData != null) &&
                    (feedResponse.responseData.feed != null) && (feedResponse.responseData.feed.entries != null)) {
                // we have data, now load it!
                for (Entries curEntry : feedResponse.responseData.feed.entries) {
                    //Entries curEntry = (Entries)curObj;
                    if ((cutoffDate == null) || (curEntry.getPublishedDate().after(cutoffDate))) {
                        // date is good
                        log.log(Level.INFO, "using:" + curEntry.title + " - " + curEntry.link);
                        String embedlyURL = "http://api.embed.ly/1/extract?key=16357551b6a84e6c88debee64dcd8bf3&maxwidth=500&url=" + URLEncoder.encode(curEntry.getLink(), "UTF-8");
                        HTTPResponse embedlyResponse = fetchService.fetch(new URL(embedlyURL));
                        byte[] embedData = embedlyResponse.getContent();
                        String embedJSON = new String (embedData, "UTF-8");

                        if (embedlyResponse.getResponseCode() / 100 == 2) {
                            ParsedPage thePage = gson.fromJson(embedJSON, ParsedPage.class);

                            if (thePage != null) {
                                UploadPageAsNewBlah(thePage, channelId);
                            }
                        } else {
                            log.log(Level.SEVERE, "error parsing item: " +  embedJSON);
                        }


                    } else {
                        log.log(Level.INFO, "skipping existing item:" + curEntry.title + " - " + curEntry.link);
                        totalExistingItems++;
                    }

                }
            }

        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        } finally {
            SignOutOfHeard();
        }

    }


    public static String sendJsonPostRequest(String postURL, String jsonContent, Boolean useJSON) {
        return sendJsonRequest("POST", postURL, jsonContent, useJSON);
    }

    public static String sendJsonPutRequest(String postURL, String jsonContent, Boolean useJSON) {
        return sendJsonRequest("POST", postURL, jsonContent, useJSON);
    }

    public static String sendJsonRequest(String method, String postURL, String jsonContent, Boolean useJSON) {
        String responseStr=null;

        try {
            URL url = new URL(postURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            if (!sessionCookie.isEmpty())
                connection.setRequestProperty("Cookie", sessionCookie);
            if (useJSON)
                connection.setRequestProperty("Content-Type", "application/json");
            else
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            connection.setRequestProperty("charset", "utf-8");
            connection.setRequestProperty("Content-Length", "" + Integer.toString(jsonContent.getBytes().length));
            connection.setUseCaches(false);

            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
            writer.write(jsonContent);
            writer.close();
            responseStr="Response code: "+connection.getResponseCode()+" and mesg:"+connection.getResponseMessage();
            String resultCookie = connection.getHeaderField("set-cookie");
            if ((resultCookie != null) && (!resultCookie.isEmpty()))
                sessionCookie = resultCookie.substring(0, resultCookie.indexOf(';') + 1);

            log.log(Level.FINE, responseStr);


            InputStream response;

            // Check for error , if none store response
            if(connection.getResponseCode() / 100 == 2){
                response = connection.getInputStream();
            }else{
                response = connection.getErrorStream();
            }
            InputStreamReader isr = new InputStreamReader(response);
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(isr);
            String read = br.readLine();
            while(read != null){
                sb.append(read);
                read = br.readLine();
            }

            connection.disconnect();
            responseStr = sb.toString();
        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }

        return responseStr;
    }


    private String getJsonDataFromURL(String getURL, String jsonData, Boolean useCookie) {
        String resultStr = null;

        try {
            URLFetchService fetchService = URLFetchServiceFactory.getURLFetchService();

            HTTPResponse fetchResponse;

            if (useCookie) {

                HTTPRequest theReq = new HTTPRequest(new URL(getURL), HTTPMethod.GET, FetchOptions.Builder.doNotFollowRedirects());
                HTTPHeader theHeader = new HTTPHeader("Cookie", sessionCookie);
                theReq.setHeader(theHeader);
                fetchResponse = fetchService.fetch(theReq);

            } else
                fetchResponse = fetchService.fetch(new URL(getURL));

            byte[] theData = fetchResponse.getContent();
            resultStr = new String (theData, "UTF-8");
        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }

        return resultStr;
    }

    private List<ImportRecord> GetImportRecords(String groupId) {

        SignInToHeard(adminUsername, adminPassword);
        List<ImportRecord>  recordList = null;
        String importUrl = HeardServerURL + "groups/" + groupId + "/importers";
        String jsonData = "{}";
        String jsonResponse = getJsonDataFromURL(importUrl, jsonData, true);

        if (!jsonResponse.isEmpty()) {
            Type listType = new TypeToken<ArrayList<ImportRecord>>() {}.getType();
            recordList = new Gson().fromJson(jsonResponse, listType);
        }

        SignOutOfHeard();
        return recordList;

    }

    private List<Channel> GetAllChannels() {
        List<Channel>  recordList = null;
        String importUrl = HeardServerURL + "groups/";
        String jsonData = "{}";
        String jsonResponse = getJsonDataFromURL(importUrl, jsonData, true);

        if (!jsonResponse.isEmpty()) {
            try {
                Type listType = new TypeToken<ArrayList<Channel>>() {}.getType();
                recordList = new Gson().fromJson(jsonResponse, listType);
            } catch (Exception exp) {
                log.log(Level.SEVERE, "error parsing channel json :" + jsonResponse);
                throw exp;
            }

        }

        return recordList;

    }

    private String GetBlahTypeByName(String typeName) {
        String theUrl = HeardServerURL + "blahs/types";
        String typeListStr = getJsonDataFromURL(theUrl, "{}", false);
        String theID = null;

        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<BlahType>>() {}.getType();
        List<BlahType> typeList = gson.fromJson(typeListStr, listType);

        if (typeList != null) {
            for (BlahType curType :typeList) {
                if (curType.N.compareToIgnoreCase(typeName) == 0) {
                    theID = curType._id;
                    break;
                }
            }
        }

        return theID;
    }

    private Boolean SignInToHeard(String username, String password)  {
        Boolean didIt = false;
        String theURL = HeardServerURL + "users/login";
        String jsonData = "{\"N\":\"" + username + "\", \"pwd\":\"" + password + "\"}";

        String didItMsg = sendJsonPostRequest(theURL, jsonData, true);
        if (didItMsg.isEmpty())
            didIt = true;

        return didIt;
    }

    private void SignOutOfHeard()  {
        String theURL = HeardServerURL + "users/logout";
        String jsonData = "{}";

        sendJsonPostRequest(theURL, jsonData, true);
    }

    private void UploadPageAsNewBlah(ParsedPage thePage, String channelId)
    {
        String title = thePage.getTitle();
        String body = thePage.getDescription();
        String theURL = thePage.getUrl();
        String theImageURL = null;

        if (title == null)
            title = "";
        if (body == null)
            body = "";

        if ((thePage.getImages() != null) && (!thePage.getImages().isEmpty())) {
            Images curImage = thePage.getImages().get(0);

            if ((curImage != null) && (curImage.getWidth().intValue() + curImage.getHeight().intValue() > 256)) {
                // convert this URL into a proper image
                theImageURL = FetchAndStoreImageURL(curImage.getUrl());
            }
        }

        CreateImportBlah(channelId, title, body, theImageURL, theURL);

    }

    private String FetchAndStoreImageURL(String srcURL)  {
        String finalURL = "";
        try {
            String jsonData = "imageurl=" + URLEncoder.encode(srcURL, "UTF-8");
            finalURL = sendJsonPostRequest("http://heard-test-001.appspot.com/api/image/url", jsonData, false);
            log.log(Level.INFO, "converted image: " + finalURL);
            if ((finalURL == null) || (finalURL.isEmpty()))
                log.log(Level.SEVERE, "image conversion failed!");
        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }


        return finalURL;
    }

    private void CreateImportBlah(String channelId, String title, String body, String imageURL, String appendedURL)  {

        if (!title.isEmpty())
            title = truncate(title, 64);

        if ((appendedURL != null) && (!appendedURL.isEmpty()))
            body += "\n\n" + appendedURL;
        if (!body.isEmpty())
            body = Codify(body);

        Blah newBlah = new Blah();
        newBlah.Y = blahTypeSays;
        newBlah.G = channelId;
        newBlah.T = title;
        newBlah.F = body;
        newBlah.B = null;

        if ((imageURL != null) && (!imageURL.isEmpty())) {
            List<String> mediaList = new ArrayList<String>();
            mediaList.add(imageURL);
            newBlah.M = mediaList;
        } else
            newBlah.M = null;

        newBlah.XX = false; // not anonymous
        //newBlah.XXX = false;

        Gson gson = new Gson();
        String jsonStr = gson.toJson(newBlah, Blah.class);
        String createUrl = HeardServerURL + "blahs";
        String resultStr = sendJsonPostRequest(createUrl, jsonStr, true);
        totalNewItems++;

    }


    private String Codify(String strToEncode) {
        String codedStr = strToEncode.replaceAll("\\r\\n|\\r|\\n", "[_r;");

        return codedStr;
    }


    private String truncate(String str, int limit) {

        if (str.length()< limit)
            return str;

        String[] bits = str.split(" ");
        int i = 1;
        String finalStr = bits[0];

        while (i < bits.length) {
            if (finalStr.length() + bits[i].length() + 1 < limit)
                finalStr = finalStr + " " + bits[i++];
            else {
                finalStr += "…";
                break;
            }
        }

        return finalStr;
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }
}
