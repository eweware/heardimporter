package com.eweware.twitter;

import com.eweware.feedimport.Entry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.org.apache.xml.internal.security.utils.Base64;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Created by ultradad on 3/26/15.
 */
public class TwitterImporter {
    private static final Logger log = Logger.getLogger(TwitterImporter.class.getName());
    static String bearerToken = "";
    static String fetchURL = "https://api.twitter.com/1.1/statuses/user_timeline.json";

    public TwitterImporter() {
        try {
            init();
        }
        catch (Exception exp) {

        }
    }

    private void init() throws java.io.IOException {
        if (bearerToken.isEmpty())
            bearerToken = requestBearerToken("https://api.twitter.com/oauth2/token");
    }


    // Encodes the consumer key and secret to create the basic authorization key
    private String encodeKeys(String consumerKey, String consumerSecret) {
        try {
            String encodedConsumerKey = URLEncoder.encode(consumerKey, "UTF-8");
            String encodedConsumerSecret = URLEncoder.encode(consumerSecret, "UTF-8");

            String fullKey = encodedConsumerKey + ":" + encodedConsumerSecret;
            String encodedBytes = Base64.encode(fullKey.getBytes());
            return encodedBytes;
        }
        catch (UnsupportedEncodingException e) {
            return new String();
        }
    }

    // Constructs the request for requesting a bearer token and returns that token as a string
    private String requestBearerToken(String endPointUrl) throws IOException {
        HttpsURLConnection connection = null;
        String encodedCredentials = "Basic " + encodeKeys("akwe3WfqwpXiOz0tKRXPwPgeO","AXAq5ZndDHbSSsBpiN3kRdImGX3LzyBoQcTStRaAWlEYcN82zQ");
        encodedCredentials = encodedCredentials.replaceAll("\n", "");

        try {
            URL url = new URL(endPointUrl);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Host", "api.twitter.com");
            connection.setRequestProperty("User-Agent", "HeardSocialExchange");
            connection.setRequestProperty("Authorization", encodedCredentials);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            connection.setRequestProperty("Content-Length", "29");

            connection.setUseCaches(false);

            writeRequest(connection, "grant_type=client_credentials");

            // Parse the JSON response into a JSON mapped object to fetch fields from.
            String responseStr = readResponse(connection);
            Gson gson = new Gson();
            TwitterToken theToken = gson.fromJson(responseStr, TwitterToken.class);

            if (theToken != null)
                return theToken.access_token;
            else
                return responseStr;
        }
        catch (MalformedURLException e) {
            throw new IOException("Invalid endpoint URL specified.", e);
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // Writes a request to a connection
    private boolean writeRequest(HttpsURLConnection connection, String textBody) {
        try {
            BufferedWriter wr = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            wr.write(textBody);
            wr.flush();
            wr.close();

            return true;
        }
        catch (IOException e) { return false; }
    }


    // Reads a response for a given connection and returns it as a string.
    private String readResponse(HttpsURLConnection connection) {
        try {
            StringBuilder str = new StringBuilder();

            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line = "";
            while((line = br.readLine()) != null) {
                str.append(line + System.getProperty("line.separator"));
            }
            return str.toString();
        }
        catch (IOException e) { return new String(); }
    }

    // Fetches the first tweet from a given user's timeline
    public List<Tweet> fetchTimelineTweet(String userName) throws IOException {
        HttpsURLConnection connection = null;
        String endPointUrl = fetchURL + "?screen_name=" + userName;

        try {
            URL url = new URL(endPointUrl);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Host", "api.twitter.com");
            connection.setRequestProperty("User-Agent", "HeardSocialExchange");
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            connection.setUseCaches(false);

            // Parse the JSON response into a JSON mapped object to fetch fields from.
            String theResponse = readResponse(connection);
            Gson gson = new Gson();
            Type listType = new TypeToken<ArrayList<Tweet>>() {
            }.getType();

            List<Tweet> theTweets = gson.fromJson(theResponse, listType);

            return theTweets;
        } catch (MalformedURLException e) {
            throw new IOException("Invalid endpoint URL specified.", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public List<Entry> parseTwitterFeed(String userName, Date cutoffDate, Integer maxCount, Boolean includeRetweets) {
        List<Entry> entries = new ArrayList<>();

        try {
            List<Tweet> tweets = fetchTimelineTweet(userName);

            for (Tweet curTweet : tweets) {
                Date theDate = curTweet.getCreatedDate();
                if ((cutoffDate == null) || (theDate.after(cutoffDate))) {
                    if (curTweet.isRetweet() && !includeRetweets)
                        break;

                    String theTitle = curTweet.getTweetTitle(true, true, false);
                    String theURL = curTweet.getPostedURL();

                    Entry newEntry = new Entry();
                    newEntry.setTitle(theTitle);
                    newEntry.setLink(theURL);
                    newEntry.setPublishedDate(curTweet.created_at);
                    entries.add(newEntry);
                }
            }
        }
        catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }

        return entries;
    }

}
