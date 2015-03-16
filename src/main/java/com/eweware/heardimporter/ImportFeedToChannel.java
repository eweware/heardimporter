package com.eweware.heardimporter;

import com.eweware.feedimport.*;
import com.eweware.heard.*;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.ServingUrlOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.tools.internal.ws.wsdl.document.Import;

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
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by ultradad on 3/10/15.
 */
public class ImportFeedToChannel extends HttpServlet {

    private final String qaServerURL = "http://qa.rest.goheard.com:8080/v2/";
    private final String prodServerURL = "http://qa.rest.goheard.com/v2/";
    private final String devServerURL = "http://localhost:8090/v2/";
    private final String HeardServerURL = devServerURL;
    private String blahTypeSays = null;
    private final String adminUsername = "davevr";
    private final String adminPassword = "Sheep";
    private static Boolean isImporting = false;
    private static Integer totalItems = 0;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if (!isImporting) {
            try {
                isImporting = true;
                totalItems = 0;
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
                out.write("Imported " + totalItems + " items");
                out.flush();
                out.close();
            } catch (Exception exp) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                PrintWriter out = response.getWriter();
                out.write(exp.getMessage());
                out.flush();
                out.close();
                System.err.println(exp.getMessage());
            } finally {
                isImporting = false;
            }

        } else {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            PrintWriter out = response.getWriter();
            out.write("Import already running");
            out.flush();
            out.close();
        }

    }

    private void importChannels(List<Channel> channelList) {
        try {
            for (Channel curChannel : channelList) {
                System.out.println("importing channel " + curChannel.N);
                importChannel(curChannel);
            }
        } catch (Exception exp) {
            System.err.println(exp.getMessage());
        }
    }

    private void importChannel(Channel theChannel) {
        try {
            List<ImportRecord>  importRecords = GetImportRecords(theChannel._id);

            if ((importRecords != null) && (importRecords.size() > 0)) {
                System.out.println("  found " + importRecords.size() + " feeds");
                for (ImportRecord curRec : importRecords) {
                    processImportRecord(curRec);
                }
            } else {
                System.out.println("channel " + theChannel.N + " has no feeds defined");
            }
        } catch (Exception exp) {
            System.err.println(exp.getMessage());
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
            System.err.println(exp.getMessage());
        }
    }

    private void processRSSImportRecord(ImportRecord theRecord) {
        try {
            Date cutoffDate = theRecord.getLastImportDate();
            importRssFeedToChannel(theRecord.channel, theRecord.RSSurl, theRecord.importusername, theRecord.importpassword, cutoffDate);
            UpdateLastImportDate(theRecord);
        } catch (Exception exp) {
            System.err.println(exp.getMessage());
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
                System.err.println(exp.getMessage());
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
                        System.out.println("using:" + curEntry.title + " - " + curEntry.link);
                        String embedlyURL = "http://api.embed.ly/1/extract?key=16357551b6a84e6c88debee64dcd8bf3&maxwidth=500&url=" + URLEncoder.encode(curEntry.getLink(), "UTF-8");
                        HTTPResponse embedlyResponse = fetchService.fetch(new URL(embedlyURL));
                        byte[] embedData = embedlyResponse.getContent();
                        String embedJSON = new String (embedData, "UTF-8");

                        ParsedPage thePage = gson.fromJson(embedJSON, ParsedPage.class);

                        if (thePage != null) {
                            UploadPageAsNewBlah(thePage, channelId);
                        }

                    } else {
                        System.out.println("skipping existing item:" + curEntry.title + " - " + curEntry.link);
                    }

                }
            }

        } catch (Exception exp) {
            System.err.println(exp.getMessage());
        } finally {
            SignOutOfHeard();
        }

    }


    public static String sendJsonPostRequest(String postURL, String jsonContent, Boolean useJSON) {
        String responseStr=null;

        try {
            URL url = new URL(postURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
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

            //System.out.println(connection.getResponseMessage());


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
            System.err.println("Error forming request");
        }

        return responseStr;
    }

    public static String sendJsonPutRequest(String postURL, String jsonContent, Boolean useJSON) {
        String responseStr=null;

        try {
            URL url = new URL(postURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("PUT");
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
            responseStr="Response code: "+connection.getResponseCode()+" and msg:"+connection.getResponseMessage();

            //System.out.println(connection.getResponseMessage());


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
            System.err.println("Error forming request");
        }

        return responseStr;
    }


    private String getJsonDataFromURL(String getURL, String jsonData) {
        String resultStr = null;

        try {
            URLFetchService fetchService = URLFetchServiceFactory.getURLFetchService();

            HTTPResponse fetchResponse = fetchService.fetch(new URL(getURL));
            byte[] theData = fetchResponse.getContent();
            resultStr = new String (theData, "UTF-8");
        } catch (Exception exp) {

        }

        return resultStr;
    }

    private List<ImportRecord> GetImportRecords(String groupId) {

        SignInToHeard(adminUsername, adminPassword);
        List<ImportRecord>  recordList = null;
        String importUrl = HeardServerURL + "groups/" + groupId + "/importers";
        String jsonData = "{}";
        String jsonResponse = getJsonDataFromURL(importUrl, jsonData);

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
        String jsonResponse = getJsonDataFromURL(importUrl, jsonData);

        if (!jsonResponse.isEmpty()) {
            Type listType = new TypeToken<ArrayList<Channel>>() {}.getType();
            recordList = new Gson().fromJson(jsonResponse, listType);
        }

        return recordList;

    }

    private String GetBlahTypeByName(String typeName) {
        String theUrl = HeardServerURL + "blahs/types";
        String typeListStr = getJsonDataFromURL(theUrl, "{}");
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

        } catch (Exception exp) {
            System.err.println(exp.getMessage());
        }


        return finalURL;
    }

    private void CreateImportBlah(String channelId, String title, String body, String imageURL, String appendedURL)  {

        if (!title.isEmpty())
            title = truncate(title, 64);

        if (!appendedURL.isEmpty())
            body += "\n\n" + appendedURL;
        if (!body.isEmpty())
            body = Codify(body);

        Blah newBlah = new Blah();
        newBlah.Y = blahTypeSays;
        newBlah.G = channelId;
        newBlah.T = title;
        newBlah.F = body;
        newBlah.B = null;

        if (!imageURL.isEmpty()) {
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

        totalItems++;

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
