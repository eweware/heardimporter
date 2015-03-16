package com.eweware.heardimporter;

import com.eweware.feedimport.*;
import com.eweware.heard.Blah;
import com.eweware.heard.BlahType;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.ServingUrlOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Created by ultradad on 3/10/15.
 */
public class ImportFeedToChannel extends HttpServlet {

    private final String qaServerURL = "http://qa.rest.goheard.com:8080/v2/";
    private final String prodServerURL = "http://qa.rest.goheard.com/v2/";
    private final String HeardServerURL = qaServerURL;
    private String blahTypeSays = null;


    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final String feedStr = request.getParameter("feedname");
        final String channelId = request.getParameter("channel");
        final String username = request.getParameter("username");
        final String password = request.getParameter("password");
        Boolean isSignedIn = false;

        // read a feed
        String googleParserURL = "http://ajax.googleapis.com/ajax/services/feed/load?v=1.0&num=100&q=";
        String finalURL = googleParserURL + URLEncoder.encode(feedStr,"UTF-8");

        if (!SignInToHeard(username, password)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (blahTypeSays == null) {
            blahTypeSays = GetBlahTypeByName("says");
        }


        URLFetchService fetchService = URLFetchServiceFactory.getURLFetchService();

        HTTPResponse fetchResponse = fetchService.fetch(new URL(finalURL));
        byte[] theData = fetchResponse.getContent();
        String theJSON = new String (theData, "UTF-8");

        Gson gson = new Gson();
        Response feedResponse = gson.fromJson(theJSON, Response.class);
        Date lastImportDate = new Date();

        if ((feedResponse != null) && (feedResponse.responseData != null) &&
                (feedResponse.responseData.feed != null) && (feedResponse.responseData.feed.entries != null)) {
            // we have data, now load it!
            for (Entries curEntry : feedResponse.responseData.feed.entries) {
                //Entries curEntry = (Entries)curObj;
                if (curEntry.getPublishedDate().before(lastImportDate)) {
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
                    System.out.println("skipping:" + curEntry.title + " - " + curEntry.link);
                }

            }
        }

        SignOutOfHeard();
        response.setStatus(HttpServletResponse.SC_OK);
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

            System.out.println(connection.getResponseMessage());


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

        //newBlah.XX = true;
        //newBlah.XXX = false;

        Gson gson = new Gson();
        String jsonStr = gson.toJson(newBlah, Blah.class);

        System.out.println(jsonStr);
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


 /*

        CallPostMethod("blahs", JSON.stringify(param), OnSuccess, OnFailure);
    };
    */


    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }
}
