package com.eweware.heardimporter;

import com.eweware.feedimport.*;
import com.eweware.heard.*;
import com.google.appengine.api.urlfetch.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by ultradad on 3/19/15.
 */
public class ImportHelper {
    private static final Logger log = Logger.getLogger(ImportHelper.class.getName());
    private final static String qaServerURL = "http://qa.rest.goheard.com:8080/v2/";
    private final static String prodServerURL = "http://app.goheard.com/v2/";
    private final static String devServerURL = "http://localhost:8090/v2/";
    private String HeardServerURL;
    private static String blahTypeSays = null;
    public String adminUsername = "importer_admin";
    public String adminPassword = "uF_4H^_;Dw7=^y?UUU";
    private  Boolean isImporting = false;
    private  Integer totalNewItems = 0;
    private  Integer totalExistingItems = 0;
    private  String sessionCookie = "";


    public ImportHelper(String server, String adminUser, String adminPassword) throws Exception {
        SetServer(server);
        InitializeServer();
    }

    private void InitializeServer() throws Exception {
        totalNewItems = 0;
        totalExistingItems = 0;
        if (blahTypeSays == null) {
            blahTypeSays = GetBlahTypeByName("says");
        }

    }

    private void SetServer(String serverStr) throws Exception {
        if (serverStr.equalsIgnoreCase("prod"))
            HeardServerURL = prodServerURL;
        else if (serverStr.equalsIgnoreCase("qa"))
            HeardServerURL = qaServerURL;
        else if (serverStr.equalsIgnoreCase("dev"))
            HeardServerURL = devServerURL;
        else {
            throw new Exception("Unrecognized server string");
        }
    }

    public String ImportAllChannels() throws Exception {
        String resultStr = "";
        if (SignInToHeard(adminUsername, adminPassword)) {

            List<Channel> channelList = GetAllChannels();


            if (channelList != null) {
                importChannels(channelList);
            }

            SignOutOfHeard();
            resultStr = "Imported " + totalNewItems + " items, skipped " + totalExistingItems + " existing items";
        } else {
            throw new Exception("Could not sign in as admin");
        }

        return resultStr;
    }

    public String ImportSingleFeedToChannel(String channelId, String feedStr, String username, String password, Date cutoffDate, Boolean useSourceImage) {
        String resultStr = "";
        importRssFeedToChannel(channelId, feedStr, username, password, cutoffDate, useSourceImage);
        resultStr = "Imported " + totalNewItems + " items, skipped " + totalExistingItems + " existing items";

        return resultStr;
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
            Boolean useSourceImage = theRecord.usefeedimage;
            if (useSourceImage == null)
                useSourceImage = false;

            importRssFeedToChannel(theRecord.channel, theRecord.RSSurl, theRecord.importusername, theRecord.importpassword, cutoffDate, useSourceImage);
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

    private String extractNextPageURL(String sourceURL) {
        String nextURL = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(sourceURL);
            doc.getDocumentElement().normalize();
            Node root = doc.getDocumentElement();

            XPathFactory xFactory = XPathFactory.newInstance();
            XPath xPath = xFactory.newXPath();

            XPathExpression xExpress = xPath.compile("/rss/channel/*[local-name()='link'][@rel='next']");
            NodeList nodes = (NodeList) xExpress.evaluate(root, XPathConstants.NODESET);
            if ((nodes != null) && (nodes.getLength() > 0)) {
                final String urlPart = nodes.item(0).getAttributes().getNamedItem("href").getNodeValue();
                final URL baseURL = new URL(sourceURL);
                final String protocol = baseURL.getProtocol();
                final String hostStr = baseURL.getHost();
                final URL newURL = new URL(protocol, hostStr, urlPart);
                nextURL = newURL.toString();
            }
            log.log(Level.INFO, "found " + nodes.getLength() + " atom:link nodes");
        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }

        return nextURL;
    }


    private ParsedPage fetchParsedPage(String thePageUrl, int iteration) {
        ParsedPage resultPage = null;

        URLFetchService fetchService = URLFetchServiceFactory.getURLFetchService();

        try {
            String embedlyURL = "http://api.embed.ly/1/extract?key=16357551b6a84e6c88debee64dcd8bf3&maxwidth=500&url=" + URLEncoder.encode(thePageUrl, "UTF-8");
            Future<HTTPResponse> responseFuture = fetchService.fetchAsync(new URL(embedlyURL));
            HTTPResponse response = responseFuture.get(10, TimeUnit.SECONDS);

            byte[] embedData = response.getContent();
            String resultStr  = new String (embedData, "UTF-8");
            if (response.getResponseCode() / 100 == 2) {
                resultPage = new Gson().fromJson(resultStr, ParsedPage.class);
            }

        } catch (java.util.concurrent.ExecutionException execExp) {
            if (execExp.getCause().getClass() == java.net.SocketTimeoutException.class) {
                iteration++;
                if (iteration < 3) {
                    log.log(Level.INFO, String.format("timeout - retry #%d of 3", iteration+1));
                    resultPage = fetchParsedPage(thePageUrl, iteration);

                } else
                    log.log(Level.SEVERE, execExp.toString(), execExp);
            }
            else
                log.log(Level.SEVERE, execExp.toString(), execExp);

        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }

        return resultPage;
    }

    private void importRssFeedToChannel(String channelId, String feedStr, String username, String password, Date cutoffDate, Boolean useSourceImage) {
        String nextURL = extractNextPageURL(feedStr);
        try {
            String googleParserURL = "http://ajax.googleapis.com/ajax/services/feed/load?v=1.0&num=100&q=";
            String finalURL = googleParserURL + URLEncoder.encode(feedStr,"UTF-8");


            if (!SignInToHeard(username, password)) {
                return;
            }

            URLFetchService fetchService = URLFetchServiceFactory.getURLFetchService();

            Future<HTTPResponse> fetchResponseFuture = fetchService.fetchAsync(new URL(finalURL));
            HTTPResponse fetchResponse = fetchResponseFuture.get(10, TimeUnit.SECONDS);
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
                        ParsedPage thePage = fetchParsedPage(curEntry.getLink(), 0);

                        if (thePage != null) {
                            String imageUrl = null;

                            if (useSourceImage) {
                                if ((curEntry.mediaGroups != null) && (curEntry.mediaGroups.size() > 0)) {
                                    MediaGroup firstGroup = curEntry.mediaGroups.get(0);
                                    if ((firstGroup != null) && (firstGroup.contents != null) && (firstGroup.contents.size() > 0)) {
                                        MediaContent content = firstGroup.contents.get(0);
                                        if (content != null)
                                            imageUrl = content.url;
                                    }
                                }
                            }

                            UploadPageAsNewBlah(thePage, channelId, imageUrl);
                        } else {
                            log.log(Level.SEVERE, "error parsing item: " +  curEntry.getLink());
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

        if (nextURL != null)
            importRssFeedToChannel(channelId, nextURL, username, password, cutoffDate, useSourceImage);


    }

    private static String cleanUrlString(String sourceStr) {
        int findLoc = sourceStr.indexOf('?');
        if (findLoc != -1)
            return sourceStr.substring(0, findLoc);
        else
            return sourceStr;
    }


    private String sendJsonPostRequest(String postURL, String jsonContent, Boolean useJSON) {
        return sendJsonRequest("POST", postURL, jsonContent, useJSON);
    }

    private String sendJsonPutRequest(String postURL, String jsonContent, Boolean useJSON) {
        return sendJsonRequest("PUT", postURL, jsonContent, useJSON);
    }

    private String sendJsonRequest(String method, String postURL, String jsonContent, Boolean useJSON) {
        String responseStr=null;

        try {
            URL url = new URL(postURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
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
            Boolean failed = false;

            // Check for error , if none store response
            if(connection.getResponseCode() / 100 == 2){
                response = connection.getInputStream();
            }else{
                response = connection.getErrorStream();
                failed = true;
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
            if (failed)
                log.log(Level.SEVERE, method + " FAILED: " + responseStr);

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

    private void UploadPageAsNewBlah(ParsedPage thePage, String channelId, String altImageUrl)
    {
        String title = thePage.getTitle();
        String body = thePage.getDescription();
        String theURL = thePage.getUrl();
        String theImageURL = null;

        if (title == null)
            title = "";
        if (body == null)
            body = "";

        if ((altImageUrl == null) || (altImageUrl.isEmpty())) {
            if ((thePage.getImages() != null) && (!thePage.getImages().isEmpty())) {
                Images curImage = thePage.getImages().get(0);

                if ((curImage != null) && (curImage.getWidth().intValue() + curImage.getHeight().intValue() > 256)) {
                    // convert this URL into a proper image
                    theImageURL = FetchAndStoreImageURL(curImage.getUrl());
                }
            }
        } else {
            theImageURL = altImageUrl;
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
            title = truncate(title, 255);

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
            mediaList.add(cleanUrlString(imageURL));
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

}
